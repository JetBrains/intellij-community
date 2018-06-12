/*
 * Copyright 2000-2017 JetBrains s.r.o.
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

import java.util.Collection;

public class ShelveChangesCommitExecutor extends LocalCommitExecutor {
  private static final Logger LOG = Logger.getInstance("#com.intellij.openapi.vcs.changes.shelf.ShelveChangesCommitExecutor");

  private final Project myProject;

  public ShelveChangesCommitExecutor(final Project project) {
    myProject = project;
  }

  @Override
  @Nls
  public String getActionText() {
    return VcsBundle.message("shelve.changes.action");
  }

  @Override
  @NotNull
  public CommitSession createCommitSession() {
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

  private class ShelveChangesCommitSession implements CommitSession, CommitSessionContextAware {
    @Override
    public void setContext(CommitContext context) {
    }

    @Override
    public boolean canExecute(Collection<Change> changes, String commitMessage) {
      return changes.size() > 0;
    }

    @Override
    public void execute(Collection<Change> changes, String commitMessage) {
      if (changes.size() > 0 && !ChangesUtil.hasFileChanges(changes)) {
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(() -> Messages
          .showErrorDialog(myProject, VcsBundle.message("shelve.changes.only.directories"), VcsBundle.message("shelve.changes.action")), null, myProject);
        return;
      }
      try {
        final ShelvedChangeList list = ShelveChangesManager.getInstance(myProject).shelveChanges(changes, commitMessage, true);
        ShelvedChangesViewManager.getInstance(myProject).activateView(list);

        Change[] changesArray = changes.toArray(new Change[0]);
        LocalChangeList changeList = ChangesUtil.getChangeListIfOnlyOne(myProject, changesArray);
        if (changeList != null) {
          ChangeListManager.getInstance(myProject).scheduleAutomaticEmptyChangeListDeletion(changeList, true);
        }
      }
      catch (final Exception ex) {
        LOG.info(ex);
        WaitForProgressToShow.runOrInvokeLaterAboveProgress(
          () -> Messages.showErrorDialog(myProject, VcsBundle.message("create.patch.error.title", ex.getMessage()), CommonBundle.getErrorTitle()), ModalityState.NON_MODAL, myProject);
      }
    }

    @Override
    public String getHelpId() {
      return null;
    }
  }
}
