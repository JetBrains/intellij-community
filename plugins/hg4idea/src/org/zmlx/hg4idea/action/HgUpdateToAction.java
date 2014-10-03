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
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgUpdateCommand;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgUpdateToDialog;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.Collection;

public class HgUpdateToAction extends HgAbstractGlobalSingleRepoAction {

  @Override
  protected void execute(@NotNull final Project project,
                         @NotNull final Collection<HgRepository> repositories,
                         @Nullable HgRepository selectedRepo) {
    final HgUpdateToDialog dialog = new HgUpdateToDialog(project, repositories, selectedRepo);
    dialog.show();
    if (dialog.isOK()) {
      FileDocumentManager.getInstance().saveAllDocuments();
      final String updateToValue = StringUtil.escapeBackSlashes(dialog.getTargetValue());
      final boolean clean = dialog.isRemoveLocalChanges();
      final VirtualFile root = dialog.getRepository().getRoot();
      new Task.Backgroundable(project, HgVcsMessages.message("hg4idea.progress.updatingTo", updateToValue)) {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          updateTo(project, root, updateToValue, clean);
        }
      }.queue();
    }
  }

  public static boolean updateTo(@NotNull final Project project,
                                 @NotNull final VirtualFile root,
                                 @NotNull final String updateToValue,
                                 final boolean clean) {
    final HgUpdateCommand command = new HgUpdateCommand(project, root);
    command.setRevision(updateToValue);
    command.setClean(clean);
    HgCommandResult result = command.execute();
    new HgConflictResolver(project).resolve(root);
    boolean success = !HgErrorUtil.isCommandExecutionFailed(result);
    boolean hasUnresolvedConflicts = !HgConflictResolver.findConflicts(project, root).isEmpty();
    if (!success) {
      new HgCommandResultNotifier(project).notifyError(result, "", "Update failed");
    }
    else if (hasUnresolvedConflicts) {
      new VcsNotifier(project)
        .notifyImportantWarning("Unresolved conflicts.", HgVcsMessages.message("hg4idea.update.warning.merge.conflicts", root.getPath()));
    }
    markDirtyAndHandleErrors(project, root);
    return success;
  }
}
