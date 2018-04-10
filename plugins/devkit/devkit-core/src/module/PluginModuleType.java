/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.module;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.module.*;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.ContentEntry;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.roots.SourceFolder;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.xml.XmlFile;
import java.util.HashSet;
import com.intellij.util.descriptors.ConfigFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.build.PluginBuildConfiguration;
import org.jetbrains.idea.devkit.build.PluginBuildUtil;
import org.jetbrains.idea.devkit.util.PsiUtil;
import org.jetbrains.jps.model.java.JavaModuleSourceRootTypes;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class PluginModuleType extends ModuleType<PluginModuleBuilder> {

  @NonNls private static final String ID = "PLUGIN_MODULE";

  public PluginModuleType() {
    super(ID);
  }

  public static PluginModuleType getInstance() {
    return (PluginModuleType)ModuleTypeManager.getInstance().findByID(ID);
  }

  public static boolean isOfType(@NotNull Module module) {
    return get(module) instanceof PluginModuleType;
  }

  @NotNull
  public PluginModuleBuilder createModuleBuilder() {
    return new PluginModuleBuilder();
  }

  @NotNull
  public String getName() {
    return DevKitBundle.message("module.title");
  }

  @NotNull
  public String getDescription() {
    return DevKitBundle.message("module.description");
  }

  public Icon getNodeIcon(boolean isOpened) {
    return AllIcons.Nodes.Plugin;
  }

  @Nullable
  public static XmlFile getPluginXml(Module module) {
    if (module == null) return null;
    if (!isOfType(module)) {
      for (final ContentEntry entry : ModuleRootManager.getInstance(module).getContentEntries()) {
        for (final SourceFolder folder : entry.getSourceFolders(JavaModuleSourceRootTypes.PRODUCTION)) {
          final VirtualFile file = folder.getFile();
          if (file == null) continue;

          final String packagePrefix = folder.getPackagePrefix();
          final String prefixPath = packagePrefix.isEmpty() ? "" :
                                    packagePrefix.replace('.', '/') + '/';

          final String relativePath = prefixPath + PluginDescriptorConstants.PLUGIN_XML_PATH;
          final VirtualFile pluginXmlVF = file.findFileByRelativePath(relativePath);
          if (pluginXmlVF != null) {
            final PsiFile psiFile = PsiManager.getInstance(module.getProject()).findFile(pluginXmlVF);
            if (psiFile instanceof XmlFile) {
              return (XmlFile)psiFile;
            }
          }
        }
      }

      return null;
    }

    final PluginBuildConfiguration buildConfiguration = PluginBuildConfiguration.getInstance(module);
    if (buildConfiguration == null) return null;
    final ConfigFile configFile = buildConfiguration.getPluginXmlConfigFile();
    return configFile != null ? configFile.getXmlFile() : null;
  }

  public static boolean isPluginModuleOrDependency(@Nullable Module module) {
    if (module == null) return false;
    if (isOfType(module)) return true;
    return getCandidateModules(module).size() > 0;
  }

  public static List<Module> getCandidateModules(Module module) {
    if (PsiUtil.isIdeaProject(module.getProject())) {
      Set<Module> dependents = new HashSet<>();
      ModuleUtilCore.collectModulesDependsOn(module, dependents);
      return new ArrayList<>(dependents);
    }

    final Module[] modules = ModuleManager.getInstance(module.getProject()).getModules();
    final List<Module> candidates = new ArrayList<>(modules.length);
    final Set<Module> deps = new HashSet<>(modules.length);
    for (Module m : modules) {
      if (get(m) == getInstance()) {
        deps.clear();
        PluginBuildUtil.getDependencies(m, deps);

        if (deps.contains(module) && getPluginXml(m) != null) {
          candidates.add(m);
        }
      }
    }
    return candidates;
  }

  @Override
  public boolean isValidSdk(@NotNull final Module module, final Sdk projectSdk) {
    return JavaModuleType.isValidJavaSdk(module);
  }
}
