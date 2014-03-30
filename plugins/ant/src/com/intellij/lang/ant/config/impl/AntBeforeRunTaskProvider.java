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
package com.intellij.lang.ant.config.impl;

import com.intellij.execution.BeforeRunTaskProvider;
import com.intellij.execution.RunManagerEx;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.execution.runners.ExecutionEnvironment;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildFile;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.AntConfiguration;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import icons.AntIcons;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;

/**
 * @author Vladislav.Kaznacheev
 */
public class AntBeforeRunTaskProvider extends BeforeRunTaskProvider<AntBeforeRunTask> {
  public static final Key<AntBeforeRunTask> ID = Key.create("AntTarget");
  private final Project myProject;

  public AntBeforeRunTaskProvider(Project project) {
    myProject = project;
  }

  public Key<AntBeforeRunTask> getId() {
    return ID;
  }

  @Override
  public String getName() {
    return AntBundle.message("ant.target.before.run.description.empty");
  }

  @Override
  public Icon getIcon() {
    return AntIcons.Target;
  }

  @Override
  public Icon getTaskIcon(AntBeforeRunTask task) {
    AntBuildTarget antTarget = findTargetToExecute(task);
    return antTarget instanceof MetaTarget ? AntIcons.MetaTarget : AntIcons.Target;
  }

  @Override
  public String getDescription(AntBeforeRunTask task) {
    final String targetName = task.getTargetName();
    if (targetName == null) {
      return AntBundle.message("ant.target.before.run.description.empty");
    }
    return AntBundle.message("ant.target.before.run.description", targetName != null? targetName : "<not selected>");
  }

  public boolean isConfigurable() {
    return true;
  }

  public boolean configureTask(RunConfiguration runConfiguration, AntBeforeRunTask task) {
    AntBuildTarget buildTarget = findTargetToExecute(task);
    final TargetChooserDialog dlg = new TargetChooserDialog(myProject, buildTarget);
    dlg.show();
    if (dlg.isOK()) {
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

  public AntBeforeRunTask createTask(RunConfiguration runConfiguration) {
    return new AntBeforeRunTask();
  }

  @Override
  public boolean canExecuteTask(RunConfiguration configuration, AntBeforeRunTask task) {
    return findTargetToExecute(task) != null;
  }

  public boolean executeTask(DataContext context, RunConfiguration configuration, ExecutionEnvironment env, AntBeforeRunTask task) {
    final AntBuildTarget target = findTargetToExecute(task);
    if (target != null) {
      return AntConfigurationImpl.executeTargetSynchronously(context, target);
    }
    return true;
  }

  @Nullable
  private AntBuildTarget findTargetToExecute(AntBeforeRunTask task) {
    final String fileUrl = task.getAntFileUrl();
    final String targetName = task.getTargetName();
    if (fileUrl == null || targetName == null) {
      return null;
    }
    final VirtualFile vFile = VirtualFileManager.getInstance().findFileByUrl(fileUrl);
    if (vFile == null) {
      return null;
    }
    final AntConfigurationImpl antConfiguration = (AntConfigurationImpl)AntConfiguration.getInstance(myProject);
    for (AntBuildFile buildFile : antConfiguration.getBuildFiles()) {
      if (vFile.equals(buildFile.getVirtualFile())) {
        final AntBuildTarget target = buildFile.getModel().findTarget(targetName);
        if (target != null) {
          return target;
        }
        for (AntBuildTarget metaTarget : antConfiguration.getMetaTargets(buildFile)) {
          if (targetName.equals(metaTarget.getName())) {
            return metaTarget;
          }
        }
        return null;
      }
    }
    return null;
  }

  public void handleTargetRename(String oldName, String newName) {
    final RunManagerEx runManager = RunManagerEx.getInstanceEx(myProject);
    for (AntBeforeRunTask task : runManager.getBeforeRunTasks(ID)) {
      if (oldName.equals(task.getTargetName())) {
        task.setTargetName(newName);
      }
    }
  }
}
