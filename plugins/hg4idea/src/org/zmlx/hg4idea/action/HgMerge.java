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
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.command.HgMergeCommand;
import org.zmlx.hg4idea.execution.HgCommandException;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.provider.update.HgHeadMerger;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.ui.HgMergeDialog;

import java.util.Collection;

public class HgMerge extends HgAbstractGlobalAction {

  @Override
  public void execute(@NotNull final Project project,
                      @NotNull final Collection<HgRepository> repos,
                      @Nullable final HgRepository selectedRepo) {
    final HgMergeDialog mergeDialog = new HgMergeDialog(project, repos, selectedRepo);
    mergeDialog.show();
    if (mergeDialog.isOK()) {
      final String targetValue = mergeDialog.getTargetValue();
      final VirtualFile repoRoot = mergeDialog.getRepository().getRoot();
      new Task.Backgroundable(project, "Merging changes...") {
        @Override
        public void run(@NotNull ProgressIndicator indicator) {
          try {
            executeMerge(project, repoRoot, targetValue);
            markDirtyAndHandleErrors(project, repoRoot);
          }
          catch (HgCommandException e) {
            handleException(project, e);
          }
        }
      }.queue();
    }
  }


  private static void executeMerge(@NotNull final Project project, @NotNull VirtualFile repo, @NotNull String targetValue)
    throws HgCommandException {
    UpdatedFiles updatedFiles = UpdatedFiles.create();

    HgMergeCommand hgMergeCommand = new HgMergeCommand(project, repo);
    hgMergeCommand.setRevision(targetValue);

    try {
      new HgHeadMerger(project, hgMergeCommand).merge(repo);
      new HgConflictResolver(project, updatedFiles).resolve(repo);
    }
    catch (VcsException e) {
      if (e.isWarning()) {
        VcsNotifier.getInstance(project).notifyWarning("Warning during merge", e.getMessage());
      }
      else {
        VcsNotifier.getInstance(project).notifyError("Exception during merge", e.getMessage());
      }
    }
  }
}
