// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.history.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;

@ApiStatus.Internal
public class CopyCommitSubjectAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    String[] subjects = e.getData(VcsDataKeys.VCS_COMMIT_SUBJECTS);
    if (subjects == null || subjects.length == 0) return;
    CopyPasteManager.getInstance().setContents(new StringSelection(StringUtil.join(subjects, "\n")));
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    String[] subjects = e.getData(VcsDataKeys.VCS_COMMIT_SUBJECTS);
    e.getPresentation().setEnabled(subjects != null && subjects.length > 0);
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
