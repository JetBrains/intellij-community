/*
 * Copyright 2000-2014 JetBrains s.r.o.
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
package git4idea.actions;

import com.intellij.dvcs.DvcsUtil;
import com.intellij.history.Label;
import com.intellij.history.LocalHistory;
import com.intellij.openapi.application.AccessToken;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Computable;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vcs.update.ActionInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import git4idea.GitRevisionNumber;
import git4idea.GitUtil;
import git4idea.GitVcs;
import git4idea.commands.*;
import git4idea.merge.GitMergeUtil;
import git4idea.repo.GitRepository;
import git4idea.repo.GitRepositoryManager;
import git4idea.util.GitUIUtil;
import git4idea.util.GitUntrackedFilesHelper;
import git4idea.util.LocalChangesWouldBeOverwrittenHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static git4idea.commands.GitLocalChangesWouldBeOverwrittenDetector.Operation.MERGE;

abstract class GitMergeAction extends GitRepositoryAction {

  protected static class DialogState {
    final VirtualFile selectedRoot;
    final String progressTitle;
    final Computable<GitLineHandler> handlerProvider;
    DialogState(@NotNull VirtualFile root, @NotNull String title, @NotNull Computable<GitLineHandler> provider) {
      selectedRoot = root;
      progressTitle = title;
      handlerProvider = provider;
    }
  }

  @Nullable
  protected abstract DialogState displayDialog(@NotNull Project project, @NotNull List<VirtualFile> gitRoots,
                                               @NotNull VirtualFile defaultRoot);

  protected void perform(@NotNull final Project project,
                         @NotNull final List<VirtualFile> gitRoots,
                         @NotNull final VirtualFile defaultRoot,
                         final Set<VirtualFile> affectedRoots,
                         final List<VcsException> exceptions) throws VcsException {
    final DialogState dialogState = displayDialog(project, gitRoots, defaultRoot);
    if (dialogState == null) {
      return;
    }
    final VirtualFile selectedRoot = dialogState.selectedRoot;
    final Computable<GitLineHandler> handlerProvider = dialogState.handlerProvider;
    final Label beforeLabel = LocalHistory.getInstance().putSystemLabel(project, "Before update");

    new Task.Backgroundable(project, dialogState.progressTitle, true) {
      @Override
      public void run(@NotNull ProgressIndicator indicator) {
        final GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
        final Git git = ServiceManager.getService(Git.class);
        final GitLocalChangesWouldBeOverwrittenDetector localChangesDetector =
          new GitLocalChangesWouldBeOverwrittenDetector(selectedRoot, MERGE);
        final GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector =
          new GitUntrackedFilesOverwrittenByOperationDetector(selectedRoot);
        final GitSimpleEventDetector mergeConflict = new GitSimpleEventDetector(GitSimpleEventDetector.Event.MERGE_CONFLICT);

        AccessToken token = DvcsUtil.workingTreeChangeStarted(project);
        try {
          GitCommandResult result = git.runCommand(new Computable<GitLineHandler>() {
            @Override
            public GitLineHandler compute() {
              GitLineHandler handler = handlerProvider.compute();
              handler.addLineListener(localChangesDetector);
              handler.addLineListener(untrackedFilesDetector);
              handler.addLineListener(mergeConflict);
              return handler;
            }
          });
          affectedRoots.add(selectedRoot);

          GitRepository repository = repositoryManager.getRepositoryForRoot(selectedRoot);
          assert repository != null : "Repository can't be null for root " + selectedRoot;
          String revision = repository.getCurrentRevision();
          if (revision == null) {
            return;
          }
          final GitRevisionNumber currentRev = new GitRevisionNumber(revision);
          handleResult(result, project, mergeConflict, localChangesDetector, untrackedFilesDetector,
                       repository, currentRev, affectedRoots, beforeLabel);
        }
        finally {
          DvcsUtil.workingTreeChangeFinished(project, token);
        }
      }

    }.queue();
  }

  private void handleResult(GitCommandResult result,
                            Project project,
                            GitSimpleEventDetector mergeConflictDetector,
                            GitLocalChangesWouldBeOverwrittenDetector localChangesDetector,
                            GitUntrackedFilesOverwrittenByOperationDetector untrackedFilesDetector,
                            GitRepository repository,
                            GitRevisionNumber currentRev,
                            Set<VirtualFile> affectedRoots,
                            Label beforeLabel) {
    GitRepositoryManager repositoryManager = GitUtil.getRepositoryManager(project);
    VirtualFile root = repository.getRoot();
    if (result.success() || mergeConflictDetector.hasHappened()) {
      VfsUtil.markDirtyAndRefresh(false, true, false, root);
      List<VcsException> exceptions = new ArrayList<>();
      GitMergeUtil.showUpdates(this, project, exceptions, root, currentRev, beforeLabel, getActionName(), ActionInfo.UPDATE);
      repositoryManager.updateRepository(root);
      runFinalTasks(project, GitVcs.getInstance(project), affectedRoots, getActionName(), exceptions);
    }
    else if (localChangesDetector.wasMessageDetected()) {
      LocalChangesWouldBeOverwrittenHelper.showErrorNotification(project, repository.getRoot(), getActionName(),
                                                                 localChangesDetector.getRelativeFilePaths());
    }
    else if (untrackedFilesDetector.wasMessageDetected()) {
      GitUntrackedFilesHelper.notifyUntrackedFilesOverwrittenBy(project, root, untrackedFilesDetector.getRelativeFilePaths(),
                                                                getActionName(), null);
    }
    else {
      GitUIUtil.notifyError(project, "Git " + getActionName() + " Failed", result.getErrorOutputAsJoinedString(), true, null);
      repositoryManager.updateRepository(root);
    }
  }

  @Override
  protected boolean executeFinalTasksSynchronously() {
    return false;
  }

}
