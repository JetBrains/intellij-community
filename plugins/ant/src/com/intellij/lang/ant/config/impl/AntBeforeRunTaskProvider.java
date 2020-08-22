// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.impl;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.icons.AllIcons;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import icons.AntIcons;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public final class AntBeforeRunTaskProvider extends BeforeRunTaskProvider<AntBeforeRunTask> {
  public static final Key<AntBeforeRunTask> ID = Key.create("AntTarget");

  @Override
  public Key<AntBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return AntBundle.message("ant.target.before.run.description.empty");
  }

  @Override
  public Icon getIcon() {
    return AllIcons.Nodes.Target;
  }

  @Override
  public Icon getTaskIcon(AntBeforeRunTask task) {
    AntBuildTarget antTarget = findTargetToExecute(task);
    return antTarget instanceof MetaTarget ? AntIcons.MetaTarget : AllIcons.Nodes.Target;
  }

  @Override
  public String getDescription(AntBeforeRunTask task) {
    final String targetName = task.getTargetName();
    if (targetName == null) {
      return AntBundle.message("ant.target.before.run.description.empty");
    }
    return AntBundle.message("ant.target.before.run.description", targetName);
  }

  @Override
  public boolean isConfigurable() {
    return true;
  }

  @Override
  public boolean configureTask(@NotNull RunConfiguration runConfiguration, @NotNull AntBeforeRunTask task) {
    AntBuildTarget buildTarget = findTargetToExecute(task);
    final TargetChooserDialog dlg = new TargetChooserDialog(task.getProject(), buildTarget);
    if (dlg.showAndGet()) {
      task.setTargetName(null);
      task.setAntFileUrl(null);
      buildTarget = dlg.getSelectedTarget();
      if (buildTarget != null) {
        final VirtualFile vFile = buildTarget.getModel().getBuildFile().getVirtualFile();
        if (vFile != null) {
          task.setAntFileUrl(vFile.getUrl());
          task.setTargetName(buildTarget.getName());
        }
      }
      return true;
    }
    return false;
  }

  @Override
  public AntBeforeRunTask createTask(@NotNull RunConfiguration runConfiguration) {
    return new AntBeforeRunTask(runConfiguration.getProject());
  }

  @Override
  public boolean canExecuteTask(@NotNull RunConfiguration configuration, @NotNull AntBeforeRunTask task) {
    return findTargetToExecute(task) != null;
  }

  @Override
  public boolean executeTask(@NotNull DataContext context, @NotNull RunConfiguration configuration, @NotNull ExecutionEnvironment env, @NotNull AntBeforeRunTask task) {
    AntBuildTarget target = findTargetToExecute(task);
    return target == null || AntConfigurationImpl.executeTargetSynchronously(context, target);
  }

  @Nullable
  private static AntBuildTarget findTargetToExecute(@NotNull AntBeforeRunTask task) {
    return GlobalAntConfiguration.getInstance().findTarget(task.getProject(), task.getAntFileUrl(), task.getTargetName());
  }
}
