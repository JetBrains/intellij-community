/*
 * Copyright 2000-2013 JetBrains s.r.o.
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
package org.zmlx.hg4idea.action;

import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.Consumer;
import org.jetbrains.annotations.NotNull;
import org.zmlx.hg4idea.HgRevisionNumber;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.command.HgTagBranch;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.provider.update.HgHeadMerger;
import org.zmlx.hg4idea.ui.HgMergeDialog;

import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * @author Nadya Zabrodina
 */
public class HgMerge extends HgAbstractGlobalAction {

  @Override

  public void execute(final Project project, final Collection<VirtualFile> repos) {
    HgActionDialogUtil.loadBranchesInBackgroundableAndExecuteAction(project, repos, new Consumer<Map<VirtualFile, List<HgTagBranch>>>() {

      @Override
      public void consume(Map<VirtualFile, List<HgTagBranch>> branches) {
        showMergeDialogAndExecute(project, repos, branches);
      }
    });
  }

  private void showMergeDialogAndExecute(final Project project,
                                         Collection<VirtualFile> repos,
                                         Map<VirtualFile, List<HgTagBranch>> branchesForRepos) {
    final HgMergeDialog mergeDialog = new HgMergeDialog(project, repos, branchesForRepos);
    mergeDialog.show();
    if (mergeDialog.isOK()) {
      new Task.Backgroundable(project, "Merging changes...") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            executeMerge(mergeDialog, project);
            markDirtyAndHandleErrors(project, mergeDialog.getRepository());
          }
          catch (HgCommandException e) {
            handleException(project, e);
          }
        }
      }.queue();
    }
  }

  private static void executeMerge(final HgMergeDialog dialog, final Project project) throws HgCommandException {
    UpdatedFiles updatedFiles = UpdatedFiles.create();
    HgCommandResultNotifier notifier = new HgCommandResultNotifier(project);
    final VirtualFile repo = dialog.getRepository();

    HgMergeCommand hgMergeCommand = new HgMergeCommand(project, repo);

    HgRevisionNumber incomingRevision = null;
    HgTagBranch branch = dialog.getBranch();
    if (branch != null) {
      hgMergeCommand.setBranch(branch.getName());
      incomingRevision = branch.getHead();
    }

    HgTagBranch tag = dialog.getTag();
    if (tag != null) {
      hgMergeCommand.setRevision(tag.getName());
      incomingRevision = tag.getHead();
    }

    String revision = dialog.getRevision();
    if (revision != null) {
      hgMergeCommand.setRevision(revision);
      incomingRevision = HgRevisionNumber.getLocalInstance(revision);
    }

    HgRevisionNumber otherHead = dialog.getOtherHead();
    if (otherHead != null) {
      hgMergeCommand.setRevision(otherHead.getRevision());
      incomingRevision = otherHead;
    }

    if (incomingRevision != null) {
      try {
        String warnings = new HgHeadMerger(project, hgMergeCommand)
          .merge(repo, updatedFiles, incomingRevision).getWarnings();

        if (!StringUtil.isEmptyOrSpaces(warnings)) {
          //noinspection ThrowableInstanceNeverThrown
          VcsException warning = new VcsException(warnings);
          warning.setIsWarning(true);
          notifier.notifyWarning("Warnings during merge", warnings);
        }

        new HgConflictResolver(project, updatedFiles).resolve(repo);
      }
      catch (VcsException e) {
        if (e.isWarning()) {
          notifier.notifyWarning("Warning during merge", e.getMessage());
        }
        else {
          notifier.notifyError(null, "Exception during merge", e.getMessage());
        }
      }
    }
    else {
      //noinspection ThrowableInstanceNeverThrown
      notifier.notifyError(null, "Merge error", HgVcsMessages.message("hg4idea.error.invalidTarget"));
    }
  }
}
