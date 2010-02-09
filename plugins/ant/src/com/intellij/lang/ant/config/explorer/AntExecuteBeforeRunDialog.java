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
package com.intellij.lang.ant.config.explorer;

import com.intellij.execution.impl.BaseExecuteBeforeRunDialog;
import com.intellij.lang.ant.AntBundle;
import com.intellij.lang.ant.config.AntBuildTarget;
import com.intellij.lang.ant.config.impl.AntBeforeRunTask;
import com.intellij.lang.ant.config.impl.AntBeforeRunTaskProvider;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.vfs.VirtualFile;

public final class AntExecuteBeforeRunDialog extends BaseExecuteBeforeRunDialog<AntBeforeRunTask> {
  private final AntBuildTarget myTarget;

  public AntExecuteBeforeRunDialog(final Project project, final AntBuildTarget target) {
    super(project);
    myTarget = target;
    init();
  }

  @Override
  protected String getTargetDisplayString() {
    return AntBundle.message("ant.target");
  }

  @Override
  protected Key<AntBeforeRunTask> getTaskID() {
    return AntBeforeRunTaskProvider.ID;
  }

  @Override
  protected boolean isRunning(AntBeforeRunTask task) {
    return task.isRunningTarget(myTarget);
  }

  @Override
  protected void update(AntBeforeRunTask task) {
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
