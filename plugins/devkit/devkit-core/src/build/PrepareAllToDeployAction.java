// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.idea.devkit.build;

import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.roots.ui.configuration.ChooseModulesDialog;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.idea.devkit.DevKitBundle;
import org.jetbrains.idea.devkit.module.PluginModuleType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class PrepareAllToDeployAction extends PrepareToDeployAction {

  @Override
  public void actionPerformed(final @NotNull AnActionEvent e) {
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

  @Override
  public void update(@NotNull AnActionEvent e) {
    long moduleCount = 0;
    final Project project = e.getData(CommonDataKeys.PROJECT);
    if (project != null) {
      moduleCount = Arrays.stream(ModuleManager.getInstance(project).getModules()).filter(PluginModuleType::isOfType)
        .limit(2).count();
    }
    boolean enabled = false;
    if (moduleCount > 1) {
      enabled = true;
    }
    else if (moduleCount > 0) {
      final Module module = e.getData(PlatformCoreDataKeys.MODULE);
      if (module == null || !(PluginModuleType.isOfType(module))) {
        enabled = true;
      }
    }
    e.getPresentation().setEnabledAndVisible(enabled);
    if (enabled) {
      e.getPresentation().setText(DevKitBundle.messagePointer("prepare.for.deployment.all"));
    }
  }
}