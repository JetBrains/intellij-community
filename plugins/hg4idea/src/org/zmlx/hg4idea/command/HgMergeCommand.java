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
package org.zmlx.hg4idea.command;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vcs.update.UpdatedFiles;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgBundle;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgPromptCommandExecutor;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;
import org.zmlx.hg4idea.util.HgUtil;

import java.util.LinkedList;
import java.util.List;

import static org.zmlx.hg4idea.HgNotificationIdsHolder.*;
import static org.zmlx.hg4idea.util.HgErrorUtil.ensureSuccess;

public class HgMergeCommand {

  @NotNull private final Project project;
  @NotNull private final HgRepository repo;
  @Nullable private String revision;

  public HgMergeCommand(@NotNull Project project, @NotNull HgRepository repo) {
    this.project = project;
    this.repo = repo;
  }

  private void setRevision(@NotNull @NonNls String revision) {
    this.revision = revision;
  }

  @Nullable
  private HgCommandResult executeInCurrentThread() {
    HgPromptCommandExecutor commandExecutor = new HgPromptCommandExecutor(project);
    commandExecutor.setShowOutput(true);
    List<String> arguments = new LinkedList<>();
    if (!StringUtil.isEmptyOrSpaces(revision)) {
      arguments.add("--rev");
      arguments.add(revision);
    }
    try (AccessToken ignore = DvcsUtil.workingTreeChangeStarted(project, HgBundle.message("activity.name.merge"))) {
      HgCommandResult result = commandExecutor.executeInCurrentThread(repo.getRoot(), "merge", arguments);
      repo.update();
      return result;
    }
  }

  @Nullable
  public HgCommandResult mergeSynchronously() throws VcsException {
    HgCommandResult commandResult = ensureSuccess(executeInCurrentThread());
    HgUtil.markDirectoryDirty(project, repo.getRoot());
    return commandResult;
  }

  public static void mergeWith(@NotNull final HgRepository repository,
                               @NotNull final @NonNls String branchName,
                               @NotNull final UpdatedFiles updatedFiles) {
    mergeWith(repository, branchName, updatedFiles, null);
  }

  public static void mergeWith(@NotNull final HgRepository repository,
                               @NotNull final @NonNls String branchName,
                               @NotNull final UpdatedFiles updatedFiles, @Nullable final Runnable onSuccessHandler) {
    final Project project = repository.getProject();
    final VirtualFile repositoryRoot = repository.getRoot();
    final HgMergeCommand hgMergeCommand = new HgMergeCommand(project, repository);
    hgMergeCommand.setRevision(branchName);//there is no difference between branch or revision or bookmark as parameter to merge,
    // we need just a string
    new Task.Backgroundable(project, HgBundle.message("action.hg4idea.merge.progress")) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        try {
          HgCommandResult result = hgMergeCommand.mergeSynchronously();
          if (HgErrorUtil.isAncestorMergeError(result)) {
            //skip and notify
            VcsNotifier.getInstance(project)
              .notifyMinorWarning(MERGE_WITH_ANCESTOR_SKIPPED,
                                  HgBundle.message("action.hg4idea.merge.skipped.title", repositoryRoot.getPresentableName()),
                                  HgBundle.message("action.hg4idea.merge.skipped"));
            return;
          }
          new HgConflictResolver(project, updatedFiles).resolve(repositoryRoot);
          if (!HgConflictResolver.hasConflicts(project, repositoryRoot) && onSuccessHandler != null) {
            onSuccessHandler.run();    // for example commit changes
          }
        }
        catch (VcsException exception) {
          if (exception.isWarning()) {
            VcsNotifier.getInstance(project).notifyWarning(MERGE_WARNING,
                                                           HgBundle.message("action.hg4idea.merge.warning"),
                                                           exception.getMessage());
          }
          else {
            VcsNotifier.getInstance(project).notifyError(MERGE_EXCEPTION,
                                                         HgBundle.message("action.hg4idea.merge.exception"),
                                                         exception.getMessage());
          }
        }
      }
    }.queue();
  }
}
