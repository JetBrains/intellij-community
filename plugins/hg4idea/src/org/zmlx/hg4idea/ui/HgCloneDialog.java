// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.zmlx.hg4idea.ui;

import com.intellij.dvcs.DvcsRememberedInputs;
import com.intellij.dvcs.ui.CloneDvcsDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRememberedInputs;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.command.HgIdentifyCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.util.HgUtil;

/**
 * A dialog for the mercurial clone options
 * @deprecated deprecated in favour of {@link com.intellij.util.ui.cloneDialog.VcsCloneDialog}
 */
@SuppressWarnings("DeprecatedIsStillUsed")
@Deprecated(forRemoval = true)
public class HgCloneDialog extends CloneDvcsDialog {

  public HgCloneDialog(@NotNull Project project) {
    super(project, HgVcs.DISPLAY_NAME.get(), HgUtil.DOT_HG);
  }

  @Override
  protected String getDimensionServiceKey() {
    return "HgCloneDialog";
  }

  @Override
  protected String getHelpId() {
    return "reference.mercurial.clone.mercurial.repository";
  }

  @Override
  protected @NotNull DvcsRememberedInputs getRememberedInputs() {
    return ApplicationManager.getApplication().getService(HgRememberedInputs.class);
  }

  @Override
  protected @NotNull TestResult test(final @NotNull String url) {
    HgIdentifyCommand identifyCommand = new HgIdentifyCommand(myProject);
    identifyCommand.setSource(url);
    HgCommandResult result = identifyCommand.execute(ModalityState.stateForComponent(getRootPane()));
    return result != null && result.getExitValue() == 0 ? TestResult.SUCCESS : new TestResult(result.getRawError());
  }
}
