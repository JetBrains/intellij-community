/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.LangDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.ArrayList;
import java.util.List;

public class PrepareAllToDeployAction extends PrepareToDeployAction {

  public void actionPerformed(final AnActionEvent e) {
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project == null) return;

    List<Module> pluginModules = new ArrayList<>();
    for (Module aModule : ModuleManager.getInstance(project).getModules()) {
      if (PluginModuleType.isOfType(aModule)) {
        pluginModules.add(aModule);
      }
    }

    ChooseModulesDialog dialog = new ChooseModulesDialog(project,
                                                         pluginModules,
                                                         DevKitBundle.message("select.plugin.modules.title"),
                                                         DevKitBundle.message("select.plugin.modules.description"));
    if (dialog.showAndGet()) {
      doPrepare(dialog.getChosenElements(), project);
    }
  }

  public void update(AnActionEvent e) {
    int moduleCount = 0;
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      for (Module aModule : (ModuleManager.getInstance(project).getModules())) {
        if (PluginModuleType.isOfType(aModule)) {
          moduleCount++;
        }
      }
    }
    boolean enabled = false;
    if (moduleCount > 1) {
      enabled = true;
    }
    else if (moduleCount > 0) {
      final Module module = e.getData(LangDataKeys.MODULE);
      if (module == null || !(PluginModuleType.isOfType(module))) {
        enabled = true;
      }
    }
    e.getPresentation().setVisible(enabled);
    e.getPresentation().setEnabled(enabled);
    if (enabled) {
      e.getPresentation().setText(DevKitBundle.message("prepare.for.deployment.all"));
    }
  }
}