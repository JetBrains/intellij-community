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
package org.zmlx.hg4idea.ui;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.ui.CloneDvcsDialog;
import com.intellij.dvcs.ui.DvcsBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRememberedInputs;
import org.zmlx.hg4idea.command.HgIdentifyCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgUtil;

/**
 * A dialog for the mercurial clone options
 */
public class HgCloneDialog extends CloneDvcsDialog {

  public HgCloneDialog(@NotNull Project project) {
    super(project, HgUtil.DOT_HG);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "HgCloneDialog";
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.clone.mercurial.repository";
  }

  @NotNull
  @Override
  protected DvcsRememberedInputs getRememberedInputs() {
    return ServiceManager.getService(HgRememberedInputs.class);
  }

  @Override
  protected boolean test(@NotNull final String url) {
    final boolean[] testResult = new boolean[1];
    ProgressManager.getInstance().run(new Task.Modal(myProject, DvcsBundle.message("clone.testing", url), true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final HgIdentifyCommand identifyCommand = new HgIdentifyCommand(myProject);
        identifyCommand.setSource(url);
        final HgCommandResult result = identifyCommand.execute(ModalityState.stateForComponent(getRootPane()));
        testResult[0] = result != null && result.getExitValue() == 0;
      }
    });
    return testResult[0];
  }
}
