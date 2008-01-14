package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.ResolvingConverter;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * @author mike
 */
public class IdeaPluginConverter extends ResolvingConverter<IdeaPlugin> {
  @NotNull
  public Collection<? extends IdeaPlugin> getVariants(final ConvertContext context) {
    final XmlFile xmlFile = context.getFile();

    return collectAllVisiblePlugins(xmlFile);
  }

  public static Collection<IdeaPlugin> collectAllVisiblePlugins(final XmlFile xmlFile) {
    List<IdeaPlugin> ideaPlugins = new ArrayList<IdeaPlugin>();
    final Project project = xmlFile.getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);

    final Iterable<VirtualFile> metaInfs = ProjectRootManager.getInstance(project).getFileIndex().getDirsByPackageName("META-INF", true);

    for (VirtualFile metaInf : metaInfs) {
      final VirtualFile pluginXml = metaInf.findChild("plugin.xml");
      if (pluginXml == null) continue;
      final IdeaPlugin ideaPlugin = getIdeaPlugin(project, psiManager, pluginXml);
      if (ideaPlugin != null) {
        ideaPlugins.add(ideaPlugin);
      }
    }

    final Module module = ModuleUtil.findModuleForPsiElement(xmlFile);
    if (module != null) {
      // a plugin.xml doesn't need to be in a source folder. 
      final Module[] dependencies = ModuleRootManager.getInstance(module).getDependencies();
      for (Module dep : dependencies) {
        if (PluginModuleType.isOfType(dep)) {
          final XmlFile file = PluginModuleType.getPluginXml(dep);
          if (file == null) continue;
          final VirtualFile pluginXml = file.getVirtualFile();
          if (pluginXml != null) {
            final IdeaPlugin ideaPlugin = getIdeaPlugin(project, psiManager, pluginXml);
            if (ideaPlugin != null) {
              if (!ideaPlugins.contains(ideaPlugin)) {
                ideaPlugins.add(ideaPlugin);
              }
            }
          }
        }
      }

      final Sdk jdk = ModuleRootManager.getInstance(module).getSdk();
      if (jdk != null && jdk.getSdkType() instanceof IdeaJdk) {
        final VirtualFile pluginsHome = jdk.getHomeDirectory().findChild("plugins");
        final VirtualFile[] plugins = pluginsHome != null ? pluginsHome.getChildren() : VirtualFile.EMPTY_ARRAY;
        for (VirtualFile plugin : plugins) {
          if (plugin.isDirectory()) {
            final VirtualFile lib = plugin.findChild("lib");
            final VirtualFile[] children = lib != null ? lib.getChildren() : VirtualFile.EMPTY_ARRAY;
            for (VirtualFile child : children) {
              final IdeaPlugin ideaPlugin = findPluginInFile(child, project, psiManager);
              if (ideaPlugin != null) {
                ideaPlugins.add(ideaPlugin);
              }
            }
          }
          else {
            final IdeaPlugin ideaPlugin = findPluginInFile(plugin, project, psiManager);
            if (ideaPlugin != null) {
              ideaPlugins.add(ideaPlugin);
            }
          }
        }
      }
    }

    return ideaPlugins;
  }

  @Nullable
  private static IdeaPlugin findPluginInFile(final VirtualFile child, final Project project, final PsiManager psiManager) {
    if (child.getFileType() != FileTypes.ARCHIVE) return null;

    final VirtualFile jarRoot = JarFileSystem.getInstance().getJarRootForLocalFile(child);
    if (jarRoot == null) return null;
    final VirtualFile metaInf = jarRoot.findChild("META-INF");
    if (metaInf == null) return null;

    final VirtualFile pluginXml = metaInf.findChild("plugin.xml");
    if (pluginXml == null) return null;

    return getIdeaPlugin(project, psiManager, pluginXml);
  }

  @Nullable
  private static IdeaPlugin getIdeaPlugin(final Project project, final PsiManager psiManager, final VirtualFile pluginXml) {
    final XmlFile psiFile = (XmlFile)psiManager.findFile(pluginXml);
    if (psiFile == null) return null;

    final XmlDocument document = psiFile.getDocument();
    if (document == null) return null;

    final DomElement domElement = DomManager.getDomManager(project).getDomElement(document.getRootTag());
    if (!(domElement instanceof IdeaPlugin)) return null;
    return (IdeaPlugin)domElement;

  }

  public IdeaPlugin fromString(@Nullable @NonNls final String s, final ConvertContext context) {
    for (IdeaPlugin ideaPlugin : getVariants(context)) {
      final String otherId = ideaPlugin.getPluginId();
      if (otherId == null) continue;
      if (otherId.equals(s)) return ideaPlugin;
    }
    return null;
  }

  public String toString(@Nullable final IdeaPlugin ideaPlugin, final ConvertContext context) {
    return ideaPlugin != null ? ideaPlugin.getPluginId() : null;
  }
}
