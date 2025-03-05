// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.vcs.changes.shelf;

import com.intellij.CommonBundle;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsBundle;
import com.intellij.openapi.vcs.changes.*;
import com.intellij.util.WaitForProgressToShow;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;

public class ShelveChangesCommitExecutor extends LocalCommitExecutor {
  private static final Logger LOG = Logger.getInstance(ShelveChangesCommitExecutor.class);

  private final Project myProject;

  public ShelveChangesCommitExecutor(final Project project) {
    myProject = project;
  }

  @Override
  public @NotNull @Nls String getActionText() {
    return VcsBundle.message("shelve.changes.action");
  }

  @Override
  public @NotNull CommitSession createCommitSession(@NotNull CommitContext commitContext) {
    return new ShelveChangesCommitSession();
  }

  @Override
  public String getHelpId() {
    return "reference.dialogs.vcs.shelve";
  }

  @Override
  public boolean supportsPartialCommit() {
    return true;
  }

  private class ShelveChangesCommitSession implements CommitSession {
    @Override
    public void execute(@NotNull Collection<? extends Change> changes, @Nullable String commitMessage) {
      if (!changes.isEmpty() && !ChangesUtil.hasFileChanges(changes)) {
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> Messages
          .showErrorDialog(myProject, VcsBundle.message("shelve.changes.only.directories"), VcsBundle.message("shelve.changes.action")), null, myProject);
        return;
      }
      try {
        final ShelvedChangeList list = ShelveChangesManager.getInstance(myProject).shelveChanges(changes, commitMessage, true, false, true);
        ShelvedChangesViewManager.getInstance(myProject).activateView(list);

        Change[] changesArray = changes.toArray(Change.EMPTY_CHANGE_ARRAY);
        LocalChangeList changeList = ChangesUtil.getChangeListIfOnlyOne(myProject, changesArray);
        if (changeList != null) {
          ChangeListManager.getInstance(myProject).scheduleAutomaticEmptyChangeListDeletion(changeList, true);
        }
      }
      catch (final Exception ex) {
        LOG.info(ex);
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(
          () -> Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()), CommonBundle.getErrorTitle()), ModalityState.nonModal(), myProject);
      }
    }

    @Override
    public String getHelpId() {
      return null;
    }
  }
}
