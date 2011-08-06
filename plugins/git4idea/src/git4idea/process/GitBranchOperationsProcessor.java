/*
 * Copyright 2000-2011 JetBrains s.r.o.
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
package git4idea.process;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vcs.VcsException;
import git4idea.GitVcs;
import git4idea.commands.GitCommand;
import git4idea.commands.GitLineHandler;
import git4idea.commands.GitTask;
import git4idea.commands.GitTaskResultHandlerAdapter;
import git4idea.repo.GitRepository;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Executor of Git branching operations.
 *
 * @author Kirill Likhodedov
 */
public class GitBranchOperationsProcessor {

  private final Project myProject;
  private final GitRepository myRepository;

  public GitBranchOperationsProcessor(Project project, GitRepository repository) {
    myProject = project;
    myRepository = repository;
  }

  public void checkoutNewBranch(@NotNull String name) {
    checkoutNewBranch(name, null);
  }

  public void checkoutNewTrackingBranch(@NotNull String newBranchName, @NotNull String trackedBranchName) {
    checkoutNewBranch(newBranchName, trackedBranchName);
  }

  public void checkout(@NotNull final String reference) {
    final GitLineHandler h = new GitLineHandler(myProject, myRepository.getRoot(), GitCommand.CHECKOUT);
    h.setNoSSH(true);
    h.addParameters(reference);

    GitTask task = new GitTask(myProject, h, "Checking out " + reference);
    task.executeInBackground(false, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        notifySuccess(reference, "checked out");
        updateRepository();
      }

      @Override protected void onFailure() {
        showErrorMessage(h, "Couldn't checkout " + reference);
      }
    });
  }

  public void deleteBranch(final String branchName) {
    final GitLineHandler h = new GitLineHandler(myProject, myRepository.getRoot(), GitCommand.BRANCH);
    h.setNoSSH(true);
    h.addParameters("-d");
    h.addParameters(branchName);

    GitTask task = new GitTask(myProject, h, "Deleting " + branchName);
    task.executeInBackground(false, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        notifySuccess(branchName, "deleted");
        updateRepository();
      }

      @Override protected void onFailure() {
        showErrorMessage(h, "Couldn't delete " + branchName);
      }
    });
  }

  private void checkoutNewBranch(@NotNull final String name, @Nullable String trackedBranchName) {
    final GitLineHandler h = new GitLineHandler(myProject, myRepository.getRoot(), GitCommand.CHECKOUT);
    h.setNoSSH(true);
    h.addParameters("-b");
    h.addParameters(name);
    if (trackedBranchName != null) {
      h.addParameters(trackedBranchName);
    }

    GitTask task = new GitTask(myProject, h, "Checking out new branch");
    task.executeInBackground(false, new GitTaskResultHandlerAdapter() {
      @Override protected void onSuccess() {
        notifySuccess(name, "created");
        updateRepository();
      }

      @Override protected void onFailure() {
        showErrorMessage(h, "Couldn't create new branch " + name);
      }
    });
  }

  private void updateRepository() {
    myRepository.update(GitRepository.TrackedTopic.CURRENT_BRANCH, GitRepository.TrackedTopic.BRANCHES);
  }
  
  private void showErrorMessage(GitLineHandler h, String message) {
    if (h.errors().isEmpty()) {
      h.addError(new VcsException(h.getLastOutput()));
    }
    Messages.showErrorDialog(myProject, GitUIUtil.stringifyErrors(h.errors()), message);
  }
  
  private void notifySuccess(String branchName, String action) {
    // TODO not only branch, but tags, commits
    GitVcs.NOTIFICATION_GROUP_ID.createNotification("Branch <b><code>" + branchName + "</code></b> was " +  action, NotificationType.INFORMATION).notify(myProject);
  }

}
