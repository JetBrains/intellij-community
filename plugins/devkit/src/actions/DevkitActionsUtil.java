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
package org.jetbrains.idea.devkit.actions;

import com.intellij.ide.actions.CreateFileAction;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import com.intellij.openapi.vfs.VfsUtilCore;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.io.File;
import java.util.*;

public final class DevkitActionsUtil {
  private DevkitActionsUtil() {
  }


  /**
   * @return plugin descriptor for current module (if it's a plugin module) or plugin descriptor selected in dialog or null if cancelled.
   * @throws IncorrectOperationException if no plugin descriptors found.
   */
  @Nullable
  public static XmlFile choosePluginModuleDescriptor(PsiDirectory directory) {
    Project project = directory.getProject();
    Module module = getModule(directory);

    XmlFile currentModulePluginXml = PluginModuleType.getPluginXml(module);
    if (currentModulePluginXml != null) {
      return currentModulePluginXml;
    }

    if (module != null) {
      List<Module> candidateModules = PluginModuleType.getCandidateModules(module);
      Iterator<Module> it = candidateModules.iterator();
      while (it.hasNext()) {
        Module m = it.next();
        if (PluginModuleType.getPluginXml(m) == null) it.remove();
      }

      if (candidateModules.size() == 1) {
        return PluginModuleType.getPluginXml(candidateModules.get(0));
      }

      ChoosePluginModuleDialog chooseModulesDialog = new ChoosePluginModuleDialog(project, candidateModules,
                                                                        DevKitBundle.message("select.plugin.module.to.patch"), null);
      chooseModulesDialog.setSingleSelectionMode();
      chooseModulesDialog.show();

      List<Module> selectedModules = chooseModulesDialog.getChosenElements();
      if (selectedModules.isEmpty()) {
        return null; // cancelled
      }

      assert selectedModules.size() == 1;
      XmlFile pluginXml = PluginModuleType.getPluginXml(selectedModules.get(0));
      if (pluginXml != null) {
        return pluginXml;
      }
    }

    throw new IncorrectOperationException(DevKitBundle.message("error.no.plugin.xml"));
  }

  public static PsiClass createSingleClass(String name, String classTemplateName, PsiDirectory directory) {
    if (name.contains(".")) {
      String[] names = name.split("\\.");
      for (int i = 0; i < names.length - 1; i++) {
        directory = CreateFileAction.findOrCreateSubdirectory(directory, names[i]);
      }
      name = names[names.length - 1];
    }

    return JavaDirectoryService.getInstance().createClass(directory, name, classTemplateName);
  }

  @Nullable
  private static Module getModule(PsiDirectory dir) {
    Project project = dir.getProject();
    ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    VirtualFile vFile = dir.getVirtualFile();
    if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
      List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
      if (orderEntries.isEmpty()) {
        return null;
      }
      Set<Module> modules = new HashSet<>();
      for (OrderEntry orderEntry : orderEntries) {
        modules.add(orderEntry.getOwnerModule());
      }
      Module[] candidates = modules.toArray(new Module[modules.size()]);
      Arrays.sort(candidates, ModuleManager.getInstance(project).moduleDependencyComparator());
      return candidates[0];
    }
    return fileIndex.getModuleForFile(vFile);
  }


  private static class ChoosePluginModuleDialog extends ChooseModulesDialog {
    public ChoosePluginModuleDialog(Project project, List<? extends Module> items, String title, @Nullable String description) {
      super(project, items, title, description);
    }

    @Override
    protected String getItemLocation(Module item) {
      XmlFile pluginXml = PluginModuleType.getPluginXml(item);
      assert pluginXml != null;

      VirtualFile virtualFile = pluginXml.getVirtualFile();
      VirtualFile projectPath = item.getProject().getBaseDir();
      assert virtualFile != null;
      assert projectPath != null;

      if (VfsUtilCore.isAncestor(projectPath, virtualFile, false)) {
        return VfsUtilCore.getRelativePath(virtualFile, projectPath, File.separatorChar);
      } else {
        return virtualFile.getPresentableUrl();
      }
    }
  }
}
