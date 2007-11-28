package org.jetbrains.android.dom;

import com.intellij.openapi.project.Project;
import com.intellij.psi.CommonClassNames;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiField;
import com.intellij.psi.PsiManager;
import com.intellij.psi.util.PsiModificationTracker;
import com.intellij.util.xml.Converter;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xml.reflect.DomExtension;
import org.jetbrains.android.dom.converters.ResourceReferenceConverter;
import org.jetbrains.android.dom.layout.LayoutElement;
import org.jetbrains.android.dom.resources.ResourceValue;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author yole
 */
public class AndroidDomExtender extends DomExtender<AndroidDomElement> {
    public Object[] registerExtensions(@NotNull AndroidDomElement androidDomElement, @NotNull DomExtensionsRegistrar registrar) {
        /*
        if (androidDomElement instanceof Activity) {
            DomExtension extension = registrar.registerAttributeChildExtension(new XmlName("label", "android"),
                    ResourceValue.class);
            extension.setConverter(new ResourceReferenceConverter("string"));
        }
        */
        if (androidDomElement instanceof LayoutElement) {
            String name = androidDomElement.getXmlElementName();
            Collection<String> attributes = getAttributeList(androidDomElement.getManager().getProject(), name);
            for(String attr: attributes) {
                AndroidAttributeDescriptor descriptor = ourDescriptors.get(attr);
                if (descriptor != null) {
                    XmlName xmlName = new XmlName(attr, "android");
                    DomExtension extension = registrar.registerAttributeChildExtension(xmlName, descriptor.myValueClass);
                    extension.setConverter(descriptor.myConverter);
                }
            }
        }
        return new Object[] {PsiModificationTracker.OUT_OF_CODE_BLOCK_MODIFICATION_COUNT };
    }

    private Collection<String> getAttributeList(final Project project, String name) {
        Collection<String> result = new HashSet<String>();
        PsiManager manager = PsiManager.getInstance(project);
        PsiClass styleableClass = manager.findClass("android.R.styleable", project.getAllScope());
        if (styleableClass != null) {
            collectAttributesForClass(name, result, styleableClass);
            PsiClass layoutClass = manager.findClass("android.widget." + name, project.getAllScope());
            if (layoutClass != null) {
                layoutClass = layoutClass.getSuperClass();
                while(layoutClass != null &&
                        !CommonClassNames.JAVA_LANG_OBJECT.equals(layoutClass.getQualifiedName())) {
                    collectAttributesForClass(layoutClass.getName(), result, styleableClass);
                    layoutClass = layoutClass.getSuperClass();
                }
            }
        }
        return result;
    }

    private static void collectAttributesForClass(String name, Collection<String> result, PsiClass styleableClass) {
        Pattern pattern = Pattern.compile(name + "_(Layout_)?([a-z][A-Za-z_]+)");
        for(PsiField field: styleableClass.getFields()) {
            String fieldName = field.getName();
            Matcher matcher = pattern.matcher(fieldName);
            if (matcher.matches()) {
                result.add(matcher.group(2));
            }
        }
    }

    private static class AndroidAttributeDescriptor {
        private Class myValueClass;
        private Converter myConverter;

        private AndroidAttributeDescriptor(Class valueClass, Converter converter) {
            myValueClass = valueClass;
            myConverter = converter;
        }
    }

    private static final Map<String, AndroidAttributeDescriptor> ourDescriptors = new HashMap<String, AndroidAttributeDescriptor>();

    private static void registerDescriptor(String name, Class valueClass, Converter converter) {
        ourDescriptors.put(name, new AndroidAttributeDescriptor(valueClass, converter));
    }

    static {
        registerDescriptor("text", ResourceValue.class, new ResourceReferenceConverter("string"));
    }
}