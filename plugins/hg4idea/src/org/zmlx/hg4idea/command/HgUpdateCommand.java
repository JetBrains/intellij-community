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
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.util.ui.UIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zmlx.hg4idea.HgVcsMessages;
import org.zmlx.hg4idea.action.HgCommandResultNotifier;
import org.zmlx.hg4idea.execution.HgCommandResult;
import org.zmlx.hg4idea.execution.HgPromptCommandExecutor;
import org.zmlx.hg4idea.provider.update.HgConflictResolver;
import org.zmlx.hg4idea.repo.HgRepository;
import org.zmlx.hg4idea.util.HgErrorUtil;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.zmlx.hg4idea.util.HgErrorUtil.hasUncommittedChangesConflict;
import static org.zmlx.hg4idea.util.HgUtil.getRepositoryManager;

public class HgUpdateCommand {

  private final Project project;
  private final VirtualFile repo;

  private String revision;
  private boolean clean;

  public HgUpdateCommand(@NotNull Project project, @NotNull VirtualFile repo) {
    this.project = project;
    this.repo = repo;
  }

  public void setRevision(String revision) {
    this.revision = revision;
  }

  public void setClean(boolean clean) {
    this.clean = clean;
  }


  @Nullable
  public HgCommandResult execute() {
    List<String> arguments = new LinkedList<>();
    if (clean) {
      arguments.add("--clean");
    }

    if (!StringUtil.isEmptyOrSpaces(revision)) {
      arguments.add("--rev");
      arguments.add(revision);
    }

    final HgPromptCommandExecutor executor = new HgPromptCommandExecutor(project);
    executor.setShowOutput(true);
    HgCommandResult result;
    AccessToken token = DvcsUtil.workingTreeChangeStarted(project);
    try {
      result =
        executor.executeInCurrentThread(repo, "update", arguments);
      if (!clean && hasUncommittedChangesConflict(result)) {
        final String message = "<html>Your uncommitted changes couldn't be merged into the requested changeset.<br>" +
                               "Would you like to perform force update and discard them?";
        if (showDiscardChangesConfirmation(project, message) == Messages.OK) {
          arguments.add("-C");
          result = executor.executeInCurrentThread(repo, "update", arguments);
        }
      }
    }
    finally {
      DvcsUtil.workingTreeChangeFinished(project, token);
    }

    VfsUtil.markDirtyAndRefresh(false, true, false, repo);
    return result;
  }

  public static int showDiscardChangesConfirmation(@NotNull final Project project, @NotNull final String confirmationMessage) {
    final AtomicInteger exitCode = new AtomicInteger();
    UIUtil.invokeAndWaitIfNeeded(new Runnable() {
      @Override
      public void run() {
        exitCode.set(Messages.showOkCancelDialog(project, confirmationMessage, "Uncommitted Changes Problem",
                                                 "&Discard Changes", "&Cancel", Messages.getWarningIcon()));
      }
    });
    return exitCode.get();
  }

  public static void updateTo(@NotNull final String targetRevision, @NotNull List<HgRepository> repos, @Nullable final Runnable callInAwtLater) {
    FileDocumentManager.getInstance().saveAllDocuments();
    for (HgRepository repo : repos) {
      final VirtualFile repository = repo.getRoot();
      Project project = repo.getProject();
      updateRepoTo(project, repository, targetRevision, callInAwtLater);
    }
  }

  public static void updateRepoTo(@NotNull final Project project,
                                  @NotNull final VirtualFile repository,
                                  @NotNull final String targetRevision,
                                  @Nullable final Runnable callInAwtLater) {
    updateRepoTo(project, repository, targetRevision, false, callInAwtLater);
  }

  public static void updateRepoTo(@NotNull final Project project,
                                  @NotNull final VirtualFile repository,
                                  @NotNull final String targetRevision,
                                  final boolean clean,
                                  @Nullable final Runnable callInAwtLater) {
    new Task.Backgroundable(project, HgVcsMessages.message("action.hg4idea.updateTo.description", targetRevision)) {
      @Override
      public void onSuccess() {
        if (callInAwtLater != null) {
          callInAwtLater.run();
        }
      }

      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        updateRepoToInCurrentThread(project, repository, targetRevision, clean);
      }
    }.queue();
  }

  public static boolean updateRepoToInCurrentThread(@NotNull final Project project,
                                                    @NotNull final VirtualFile repository,
                                                    @NotNull final String targetRevision,
                                                    final boolean clean) {
    final HgUpdateCommand hgUpdateCommand = new HgUpdateCommand(project, repository);
    hgUpdateCommand.setRevision(targetRevision);
    hgUpdateCommand.setClean(clean);
    HgCommandResult result = hgUpdateCommand.execute();
    new HgConflictResolver(project).resolve(repository);
    boolean success = !HgErrorUtil.isCommandExecutionFailed(result);
    boolean hasUnresolvedConflicts = HgConflictResolver.hasConflicts(project, repository);
    if (!success) {
      new HgCommandResultNotifier(project).notifyError(result, "", "Update failed");
    }
    else if (hasUnresolvedConflicts) {
      new VcsNotifier(project)
        .notifyImportantWarning("Unresolved conflicts.",
                                HgVcsMessages.message("hg4idea.update.warning.merge.conflicts", repository.getPath()));
    }
    getRepositoryManager(project).updateRepository(repository);
    HgErrorUtil.markDirtyAndHandleErrors(project, repository);
    return success;
  }
}
