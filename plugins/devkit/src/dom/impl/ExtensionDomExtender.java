package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.ide.plugins.PluginManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.psi.PsiClass;
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

    registerExtensions(extensions, ideaPlugin, registrar);
    for (IdeaPlugin plugin : depPlugins) {
      registerExtensions(extensions, plugin, registrar);
    }

    /*
    registrar.registerCollectionChildrenExtension(new XmlName("errorHandler"), Extension.class).addExtender(new DomExtender() {
      public Object[] registerExtensions(@NotNull final DomElement domElement, @NotNull final DomExtensionsRegistrar registrar) {
        registrar.registerGenericAttributeValueChildExtension(new XmlName("implementation"), PsiClass.class);

        //dependencies
        return new Object[]{};
      }
    });
    */

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

  private static void registerExtensions(final Extensions extensions, final IdeaPlugin plugin, final DomExtensionsRegistrar registrar) {
    final List<ExtensionPoint> l = plugin.getExtensionPoints().getExtensionPoints();

    for (ExtensionPoint extensionPoint : l) {
      registerExtensionPoint(registrar, extensionPoint);
    }
  }

  private static void registerExtensionPoint(final DomExtensionsRegistrar registrar, final ExtensionPoint extensionPoint) {
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

          return new Object[]{};
        }
      });
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
