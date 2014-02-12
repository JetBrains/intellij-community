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

import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
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

  private static void showUpdateDialogAndExecute(@NotNull final Project project,
                                                 @NotNull Collection<VirtualFile> repos, @Nullable VirtualFile selectedRepo,
                                                 @NotNull HgBranchesAndTags branchesAndTags) {
    final HgUpdateToDialog dialog = new HgUpdateToDialog(project);
    dialog.setRoots(repos, selectedRepo, branchesAndTags);
    dialog.show();
    if (dialog.isOK()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      final String updateToValue = dialog.isBranchSelected()
                                   ? dialog.getBranch().getName()
                                   : dialog.isBookmarkSelected()
                                     ? dialog.getBookmark().getName()
                                     : dialog.isTagSelected() ? dialog.getTag().getName() : dialog.getRevision();
      String title = HgVcsMessages.message("hg4idea.progress.updatingTo", updateToValue);
      runUpdateToInBackground(project, title, dialog.getRepository(), updateToValue, dialog.isRemoveLocalChanges());
    }
  }

  public static void runUpdateToInBackground(@NotNull final Project project,
                                             @NotNull String title,
                                             @NotNull final VirtualFile root,
                                             @NotNull final String updateToNameOrRevision,
                                             final boolean clean) {
    new Task.Backgroundable(project, title) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final HgUpdateCommand command = new HgUpdateCommand(project, root);
        command.setRevision(updateToNameOrRevision);
        command.setClean(clean);
        HgCommandResult result = command.execute();
        new HgConflictResolver(project).resolve(root);
        if (HgErrorUtil.hasErrorsInCommandExecution(result)) {
          new HgCommandResultNotifier(project).notifyError(result, "", "Update failed");
        }
        markDirtyAndHandleErrors(project, root);
      }
    }.queue();
  }
}
