/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jetbrains.idea.devkit.dom.impl;

import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleUtil;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.PackageIndex;
import com.intellij.openapi.vfs.JarFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlDocument;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.xml.ConvertContext;
import com.intellij.util.xml.DomElement;
import com.intellij.util.xml.DomManager;
import com.intellij.util.xml.ResolvingConverter;
import com.intellij.util.containers.CollectionFactory;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.dom.IdeaPlugin;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.projectRoots.IdeaJdk;

import java.util.*;

/**
 * @author mike
 */
public class IdeaPluginConverter extends ResolvingConverter<IdeaPlugin> {
  @NonNls private static final Set<String> PLATFORM_MODULES = CollectionFactory.newTroveSet("com.intellij.modules.platform",
                                                                                            "com.intellij.modules.lang",
                                                                                            "com.intellij.modules.vcs",
                                                                                            "com.intellij.modules.xdebugger",
                                                                                            "com.intellij.modules.xml");

  @NotNull
  public Collection<? extends IdeaPlugin> getVariants(final ConvertContext context) {
    return collectAllVisiblePlugins(context.getFile());
  }

  @NotNull
  @Override
  public Set<String> getAdditionalVariants(@NotNull final ConvertContext context) {
    return PLATFORM_MODULES;
  }

  @Override
  public String getErrorMessage(@Nullable final String s, final ConvertContext context) {
    return DevKitBundle.message("error.cannot.resolve.plugin", s);
  }

  public static Collection<IdeaPlugin> collectAllVisiblePlugins(final XmlFile xmlFile) {
    List<IdeaPlugin> ideaPlugins = new ArrayList<IdeaPlugin>();
    final Project project = xmlFile.getProject();
    final PsiManager psiManager = PsiManager.getInstance(project);

    final Iterable<VirtualFile> metaInfs = PackageIndex.getInstance(project).getDirsByPackageName("META-INF", true);

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
        final VirtualFile jdkHome = jdk.getHomeDirectory();
        if (jdkHome != null) {
          final VirtualFile pluginsHome = jdkHome.findChild("plugins");
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
