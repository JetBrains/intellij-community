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
import com.intellij.openapi.actionSystem.Presentation;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.OrderEntry;
import com.intellij.openapi.roots.ProjectFileIndex;
import com.intellij.openapi.roots.ProjectRootManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.JavaDirectoryService;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiDirectory;
import com.intellij.psi.xml.XmlFile;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;
import org.jetbrains.idea.devkit.util.ChooseModulesDialog;

import java.util.*;

public final class DevkitActionsUtil {
  private DevkitActionsUtil() {
  }

  // length == 1 is important to make MyInputValidator close the dialog when
  // module selection is canceled. That's some weird interface actually...
  public static final PsiClass[] CANCELED = new PsiClass[1];


  @NotNull
  public static PsiClass[] createSinglePluginClass(String name, String classTemplateName, PsiDirectory directory,
                                              Set<XmlFile> pluginXmlsToPatch, Presentation templatePresentation) {
    Project project = directory.getProject();
    Module module = getModule(directory);

    if (module != null) {
      addPluginModule(module, pluginXmlsToPatch);

      if (pluginXmlsToPatch.isEmpty()) {
        List<Module> candidateModules = PluginModuleType.getCandidateModules(module);
        Iterator<Module> it = candidateModules.iterator();
        while (it.hasNext()) {
          Module m = it.next();
          if (PluginModuleType.getPluginXml(m) == null) it.remove();
        }

        if (candidateModules.size() == 1) {
          addPluginModule(candidateModules.get(0), pluginXmlsToPatch);
        }
        else {
          ChooseModulesDialog dialog = new ChooseModulesDialog(project, candidateModules, templatePresentation.getDescription());
          if (!dialog.showAndGet()) {
            // create() should return CANCELED now
            return CANCELED;
          }
          else {
            List<Module> modules = dialog.getSelectedModules();
            for (Module m : modules) {
              addPluginModule(m, pluginXmlsToPatch);
            }
          }
        }
      }
    }

    if (pluginXmlsToPatch.size() == 0) {
      throw new IncorrectOperationException(DevKitBundle.message("error.no.plugin.xml"));
    }

    if (name.contains(".")) {
      String[] names = name.split("\\.");
      for (int i = 0; i < names.length - 1; i++) {
        directory = CreateFileAction.findOrCreateSubdirectory(directory, names[i]);
      }
      name = names[names.length - 1];
    }

    PsiClass klass = JavaDirectoryService.getInstance().createClass(directory, name, classTemplateName);
    return new PsiClass[] {klass};
  }

  @Nullable
  private static Module getModule(PsiDirectory dir) {
    Project project = dir.getProject();
    final ProjectFileIndex fileIndex = ProjectRootManager.getInstance(project).getFileIndex();

    final VirtualFile vFile = dir.getVirtualFile();
    if (fileIndex.isInLibrarySource(vFile) || fileIndex.isInLibraryClasses(vFile)) {
      final List<OrderEntry> orderEntries = fileIndex.getOrderEntriesForFile(vFile);
      if (orderEntries.isEmpty()) {
        return null;
      }
      Set<Module> modules = new HashSet<>();
      for (OrderEntry orderEntry : orderEntries) {
        modules.add(orderEntry.getOwnerModule());
      }
      final Module[] candidates = modules.toArray(new Module[modules.size()]);
      Arrays.sort(candidates, ModuleManager.getInstance(project).moduleDependencyComparator());
      return candidates[0];
    }
    return fileIndex.getModuleForFile(vFile);
  }

  private static void addPluginModule(Module module, Set<XmlFile> pluginXmlsToPatch) {
    final XmlFile pluginXml = PluginModuleType.getPluginXml(module);
    if (pluginXml != null) pluginXmlsToPatch.add(pluginXml);
  }
}
