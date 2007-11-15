package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.psi.xml.XmlElement;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.Function;
import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.GenericDomValue;
import com.intellij.util.xml.XmlName;
import com.intellij.util.xml.reflect.DomExtender;
import com.intellij.util.xml.reflect.DomExtension;
import com.intellij.util.xml.reflect.DomExtensionsRegistrar;
import com.intellij.util.xmlb.annotations.Attribute;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.dom.*;

import java.util.*;

/**
 * @author mike
 */
public class ExtensionDomExtender extends DomExtender<Extensions> {


  public Object[] registerExtensions(@NotNull final Extensions extensions, @NotNull final DomExtensionsRegistrar registrar) {
    final XmlElement xmlElement = extensions.getXmlElement();
    if (xmlElement == null) return new Object[0];
    final XmlFile xmlFile = (XmlFile)xmlElement.getContainingFile();
    final PsiManager psiManager = xmlFile.getManager();

    IdeaPlugin ideaPlugin = extensions.getParentOfType(IdeaPlugin.class, true);

    if (ideaPlugin == null) return new Object[0];

    final Collection<String> dependencies = getDependencies(ideaPlugin);

    final Collection<? extends IdeaPlugin> allVisiblePlugins = IdeaPluginConverter.collectAllVisiblePlugins(xmlFile);

    List<IdeaPlugin> depPlugins = new ArrayList<IdeaPlugin>();

    for (IdeaPlugin plugin : allVisiblePlugins) {
      final GenericDomValue<String> value = plugin.getId();
      final String id = value.getStringValue();
      if (id != null && dependencies.contains(id)) {
        depPlugins.add(plugin);
      }
    }

    registerExtensions(extensions, ideaPlugin, registrar, psiManager);
    for (IdeaPlugin plugin : depPlugins) {
      registerExtensions(extensions, plugin, registrar, psiManager);
    }

    List<Object> deps = new ArrayList<Object>();
    deps.addAll(ContainerUtil.map(allVisiblePlugins, new Function<IdeaPlugin, Object>() {
      public Object fun(final IdeaPlugin ideaPlugin) {
        return ideaPlugin.getRoot();
      }
    }));
    deps.add(ProjectRootManager.getInstance(xmlFile.getProject()));

    //dependencies
    return deps.toArray(new Object[deps.size()]);
  }

  private static void registerExtensions(final Extensions extensions, final IdeaPlugin plugin, final DomExtensionsRegistrar registrar,
                                         final PsiManager psiManager) {
    final List<ExtensionPoint> l = plugin.getExtensionPoints().getExtensionPoints();

    for (ExtensionPoint extensionPoint : l) {
      registerExtensionPoint(registrar, extensionPoint, psiManager);
    }
  }

  private static void registerExtensionPoint(final DomExtensionsRegistrar registrar, final ExtensionPoint extensionPoint, final PsiManager manager) {
    final String epName = extensionPoint.getName().getStringValue();
    if (epName != null) {
      final DomExtension domExtension = registrar.registerCollectionChildrenExtension(new XmlName(epName), Extension.class);
      domExtension.putUserData(DomExtension.KEY_DECLARATION, extensionPoint);
      domExtension.addExtender(new DomExtender() {
        public Object[] registerExtensions(@NotNull final DomElement domElement, @NotNull final DomExtensionsRegistrar registrar) {
          final String interfaceName = extensionPoint.getInterface().getStringValue();
          if (interfaceName != null) {
            registrar.registerGenericAttributeValueChildExtension(new XmlName("implementation"), PsiClass.class);
          }
          else {
            final String beanClassName = extensionPoint.getBeanClass().getStringValue();
            if (beanClassName != null) {
              final PsiClass beanClass = manager.findClass(beanClassName, GlobalSearchScope.allScope(manager.getProject()));

              if (beanClass != null) {
                registerXmlb(registrar, beanClass);
              }
            }
          }


          return new Object[]{};
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
    for (PsiAnnotation annotation : annotations) {
      final String qName = annotation.getQualifiedName();
      if (qName != null && qName.equals(Attribute.class.getName())) {
        final PsiAnnotationMemberValue attributeName = annotation.findAttributeValue("value");
        if (attributeName != null) {
          final Class<String> type = String.class;
          registrar.registerGenericAttributeValueChildExtension(new XmlName(attributeName.getText()), type);
        }
      }
    }
  }

  private static Collection<String> getDependencies(IdeaPlugin ideaPlugin) {
    Set<String> result = new HashSet<String>();

    result.add(PluginManager.CORE_PLUGIN_ID);

    for (Dependency dependency : ideaPlugin.getDependencies()) {
      result.add(dependency.getStringValue());
    }

    return result;
  }
}
