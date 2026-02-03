// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history.actions;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsDataKeys;
import com.intellij.openapi.vcs.history.VcsRevisionNumber;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public class CopyRevisionNumberAction extends DumbAwareAction {

  @Override
  public void actionPerformed(@NotNull AnActionEvent e) {
    List<VcsRevisionNumber> revisions = getRevisionNumbersFromContext(e);
    revisions = ContainerUtil.reverse(revisions); // we want hashes from old to new, e.g. to be able to pass to native client in terminal
    CopyPasteManager.getInstance().setContents(new StringSelection(getHashesAsString(revisions)));
  }

  private static @NotNull List<VcsRevisionNumber> getRevisionNumbersFromContext(@NotNull AnActionEvent e) {
    VcsRevisionNumber[] revisionNumbers = e.getData(VcsDataKeys.VCS_REVISION_NUMBERS);

    return revisionNumbers != null ? Arrays.asList(revisionNumbers) : Collections.emptyList();
  }

  private static @NotNull String getHashesAsString(@NotNull List<? extends VcsRevisionNumber> revisions) {
    return StringUtil.join(revisions, VcsRevisionNumber::asString, " ");
  }

  @Override
  public void update(@NotNull AnActionEvent e) {
    e.getPresentation().setEnabled(!getRevisionNumbersFromContext(e).isEmpty());
  }

  @Override
  public @NotNull ActionUpdateThread getActionUpdateThread() {
    return ActionUpdateThread.BGT;
  }
}
