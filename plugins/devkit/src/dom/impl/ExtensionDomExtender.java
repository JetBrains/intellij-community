package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.util.xml.*;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.*;

import java.util.*;

/**
 * @author mike
 */
public class ExtensionDomExtender extends DomExtender<Extensions> {
  private static final PsiClassConverter CLASS_CONVERTER = new GlobalScopePsiClassConverter();


  public void registerExtensions(@NotNull final Extensions extensions, @NotNull final DomExtensionsRegistrar registrar) {
    final XmlElement xmlElement = extensions.getXmlElement();
    if (xmlElement == null) return;
    final PsiManager psiManager = xmlElement.getManager();

    IdeaPlugin ideaPlugin = extensions.getParentOfType(IdeaPlugin.class, true);

    if (ideaPlugin == null) return;

    registerExtensions(extensions, ideaPlugin, registrar, psiManager);

    IdeaPlugin plugin = extensions.getDefaultExtensionNs().getValue();
    if (plugin == null) plugin = extensions.getXmlns().getValue();
    if (plugin != null) {
      registerExtensions(extensions, plugin, registrar, psiManager);
    }
  }

  private static void registerExtensions(final Extensions extensions, final IdeaPlugin plugin, final DomExtensionsRegistrar registrar,
                                         final PsiManager psiManager) {
    for (ExtensionPoints points : plugin.getExtensionPoints()) {
      for (ExtensionPoint point : points.getExtensionPoints()) {
        registerExtensionPoint(registrar, point, psiManager);
      }
    }
  }

  private static void registerExtensionPoint(final DomExtensionsRegistrar registrar, final ExtensionPoint extensionPoint, final PsiManager manager) {
    final String epName = extensionPoint.getName().getStringValue();
    if (epName != null) {
      final DomExtension domExtension = registrar.registerCollectionChildrenExtension(new XmlName(epName), Extension.class);
      domExtension.putUserData(DomExtension.KEY_DECLARATION, extensionPoint);
      domExtension.addExtender(new DomExtender() {
        public void registerExtensions(@NotNull final DomElement domElement, @NotNull final DomExtensionsRegistrar registrar) {
          final String interfaceName = extensionPoint.getInterface().getStringValue();
          if (interfaceName != null) {
            registrar.registerGenericAttributeValueChildExtension(new XmlName("implementation"), PsiClass.class).setConverter(
                CLASS_CONVERTER);

            final PsiClass implClass =
              JavaPsiFacade.getInstance(manager.getProject()).findClass(interfaceName, GlobalSearchScope.allScope(manager.getProject()));
            if (implClass != null) {
              registerXmlb(registrar, implClass);
            }
          }
          else {
            final String beanClassName = extensionPoint.getBeanClass().getStringValue();
            if (beanClassName != null) {
              final PsiClass beanClass =
                JavaPsiFacade.getInstance(manager.getProject()).findClass(beanClassName, GlobalSearchScope.allScope(manager.getProject()));

              if (beanClass != null) {
                registerXmlb(registrar, beanClass);
              }
            }
          }
        }
      });
    }
  }

  private static void registerXmlb(final DomExtensionsRegistrar registrar, final PsiClass beanClass) {
    final PsiField[] fields = beanClass.getAllFields();
    for (PsiField field : fields) {
      if (field.getModifierList().hasModifierProperty(PsiModifier.PUBLIC)) {
        registerField(registrar, field);
      }
    }
  }

  private static void registerField(final DomExtensionsRegistrar registrar, @NotNull final PsiField field) {
    final PsiAnnotation[] annotations = field.getModifierList().getAnnotations();
    final PsiConstantEvaluationHelper evalHelper = JavaPsiFacade.getInstance(field.getProject()).getConstantEvaluationHelper();
    for (PsiAnnotation annotation : annotations) {
      final String qName = annotation.getQualifiedName();
      if (qName != null) {
        if (qName.equals(Attribute.class.getName())) {
          final PsiAnnotationMemberValue attributeName = annotation.findAttributeValue("value");
          if (attributeName != null && attributeName instanceof PsiExpression) {
            final Class<String> type = String.class;
            PsiExpression expression = (PsiExpression)attributeName;
            final Object evaluatedExpression = evalHelper.computeConstantExpression(expression, false);
            if (evaluatedExpression != null) {
              registrar.registerGenericAttributeValueChildExtension(new XmlName(evaluatedExpression.toString()), type);
            }
          }
        } else if (qName.equals(Tag.class.getName())) {
          final PsiAnnotationMemberValue attributeName = annotation.findAttributeValue("value");
          if (attributeName != null && attributeName instanceof PsiExpression) {
            PsiExpression expression = (PsiExpression)attributeName;
            final Object evaluatedExpression = evalHelper.computeConstantExpression(expression, false);
            if (evaluatedExpression != null) {
              // I guess this actually needs something like registrar.registerGenericTagValueChildExtension...
              registrar.registerFixedNumberChildExtension(new XmlName(evaluatedExpression.toString()), SimpleTagValue.class);
            }
          }
        }
      }
    }
  }

  public static Collection<String> getDependencies(IdeaPlugin ideaPlugin) {
    Set<String> result = new HashSet<String>();

    result.add(PluginManager.CORE_PLUGIN_ID);

    for (Dependency dependency : ideaPlugin.getDependencies()) {
      result.add(dependency.getStringValue());
    }

    return result;
  }

  interface SimpleTagValue extends DomElement {
    @TagValue
    String getValue();
  }

}
