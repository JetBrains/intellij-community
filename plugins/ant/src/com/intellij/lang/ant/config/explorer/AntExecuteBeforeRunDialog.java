// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang.ant.config.explorer;

import com.intellij.execution.impl.BaseExecuteBeforeRunDialog;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.impl.AntBeforeRunTask;
import com.intellij.lang.ant.config.impl.AntBeforeRunTaskProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

public final class AntExecuteBeforeRunDialog extends BaseExecuteBeforeRunDialog<AntBeforeRunTask> {
  private final AntBuildTarget myTarget;

  public AntExecuteBeforeRunDialog(final Project project, final AntBuildTarget target) {
    super(project);
    myTarget = target;
    init();
  }

  @Override
  protected @Nls String getTargetDisplayString() {
    return AntBundle.message("ant.target");
  }

  @Override
  protected Key<AntBeforeRunTask> getTaskId() {
    return AntBeforeRunTaskProvider.ID;
  }

  @Override
  protected boolean isRunning(AntBeforeRunTask task) {
    return task.isRunningTarget(myTarget);
  }

  @Override
  protected void update(@NotNull AntBeforeRunTask task) {
    VirtualFile f = myTarget.getModel().getBuildFile().getVirtualFile();
    task.setAntFileUrl(f != null ? f.getUrl() : null);
    task.setTargetName(f != null ? myTarget.getName() : null);
  }

  @Override
  protected void clear(AntBeforeRunTask task) {
    task.setAntFileUrl(null);
    task.setTargetName(null);
  }
}
