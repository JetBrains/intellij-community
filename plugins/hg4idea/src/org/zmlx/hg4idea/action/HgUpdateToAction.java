// Copyright 2008-2010 Victor Iacoban
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software distributed under
// the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
// either express or implied. See the License for the specific language governing permissions and
// limitations under the License.
package org.zmlx.hg4idea.action;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcs;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.ui.HgUpdateToDialog;
import org.zmlx.hg4idea.util.HgBranchesAndTags;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUiUtil;

import java.util.Collection;

public class HgUpdateToAction extends HgAbstractGlobalAction {

  protected void execute(@NotNull final Project project,
                         @NotNull final Collection<VirtualFile> repos,
                         @Nullable final VirtualFile selectedRepo) {
    HgUiUtil.loadBranchesInBackgroundableAndExecuteAction(project, repos, new Consumer<HgBranchesAndTags>() {
      @Override
      public void consume(HgBranchesAndTags info) {
        showUpdateDialogAndExecute(project, repos, selectedRepo, info);
      }
    });
  }

  private void showUpdateDialogAndExecute(final Project project,
                                          Collection<VirtualFile> repos, @Nullable VirtualFile selectedRepo,
                                          HgBranchesAndTags branchesAndTags) {
    final HgUpdateToDialog dialog = new HgUpdateToDialog(project);
    dialog.setRoots(repos, selectedRepo, branchesAndTags);
    dialog.show();
    if (dialog.isOK()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      String updateToValue = dialog.isBranchSelected()
                             ? dialog.getBranch().getName()
                             : dialog.isTagSelected() ? dialog.getTag().getName() : dialog.getRevision();
      new Task.Backgroundable(project, HgVcsMessages.message("action.hg4idea.updateTo.description", updateToValue)) {

        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          updateTo(dialog, project);
          markDirtyAndHandleErrors(project, dialog.getRepository());
        }
      }.queue();
    }
  }

  public void updateTo(HgUpdateToDialog dialog, final Project project) {
    final VirtualFile repository = dialog.getRepository();
    final HgUpdateCommand command = new HgUpdateCommand(project, repository);
    command.setClean(dialog.isRemoveLocalChanges());
    if (dialog.isRevisionSelected()) {
      command.setRevision(dialog.getRevision());
    }
    if (dialog.isBranchSelected()) {
      command.setBranch(dialog.getBranch().getName());
    }
    if (dialog.isTagSelected()) {
      command.setRevision(dialog.getTag().getName());
    }
    if (dialog.isBookmarkSelected()) {
      command.setRevision(dialog.getBookmark().getName());
    }
    ApplicationManager.getApplication().executeOnPooledThread(new Runnable() {
      @Override
      public void run() {
        HgCommandResult result = command.execute();
        new HgConflictResolver(project).resolve(repository);
        if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
          new HgCommandResultNotifier(project).notifyError(result, "", "Update failed");
        }
        project.getMessageBus().syncPublisher(HgVcs.BRANCH_TOPIC).update(project, null);
      }
    });
  }
}
