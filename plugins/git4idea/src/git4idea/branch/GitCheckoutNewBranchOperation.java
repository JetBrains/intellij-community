/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package git4idea.branch;

import com.intellij.notification.NotificationType;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.GitVcs;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.repo.GitRepository;
import git4idea.util.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static git4idea.util.GitUIUtil.code;

/**
 * Create new branch (starting from the current branch) and check it out.
 *
 * @author Kirill Likhodedov
 */
class GitCheckoutNewBranchOperation extends GitBranchOperation {

  @NotNull private final Project myProject;
  @NotNull private final String myNewBranchName;
  @NotNull private final String myPreviousBranch;

  GitCheckoutNewBranchOperation(@NotNull Project project, @NotNull Collection<GitRepository> repositories,
                                       @NotNull String newBranchName, @NotNull String previousBranch,
                                       @NotNull ProgressIndicator indicator) {
    super(project, repositories, indicator);
    myNewBranchName = newBranchName;
    myProject = project;
    myPreviousBranch = previousBranch;
  }

  @Override
  protected void execute() {
    boolean fatalErrorHappened = false;
    while (hasMoreRepositories() && !fatalErrorHappened) {
      final GitRepository repository = next();

      GitSimpleEventDetector unmergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED_PREVENTING_CHECKOUT);
      GitCommandResult result = Git.checkoutNewBranch(repository, myNewBranchName, unmergedDetector);

      if (result.success()) {
        refresh(repository);
        markSuccessful(repository);
      }
      else if (unmergedDetector.hasHappened()) {
        fatalUnmergedFilesError();
        fatalErrorHappened = true;
      }
      else {
        fatalError("Couldn't create new branch " + myNewBranchName, result.getErrorOutputAsJoinedString());
        fatalErrorHappened = true;
      }
    }

    if (!fatalErrorHappened) {
      notifySuccess();
    }
  }

  private static void refresh(@NotNull GitRepository repository) {
    repository.update(GitRepository.TrackedTopic.ALL_CURRENT, GitRepository.TrackedTopic.CONFIG, GitRepository.TrackedTopic.BRANCHES);
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    return String.format("Branch <b><code>%s</code></b> was created", myNewBranchName);
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    return "However checkout has succeeded for the following " + repositories() + ":<br/>" +
           successfulRepositoriesJoined() +
           "<br/>You may rollback (checkout back to " + myPreviousBranch + " and delete " + myNewBranchName + ") not to let branches diverge.";
  }

  @NotNull
  @Override
  protected String getOperationName() {
    return "checkout";
  }

  @Override
  protected void rollback() {
    GitCompoundResult checkoutResult = new GitCompoundResult(myProject);
    GitCompoundResult deleteResult = new GitCompoundResult(myProject);
    Collection<GitRepository> repositories = getSuccessfulRepositories();
    for (GitRepository repository : repositories) {
      GitCommandResult result = Git.checkout(repository, myPreviousBranch, null, true);
      checkoutResult.append(repository, result);
      if (result.success()) {
        deleteResult.append(repository, Git.branchDelete(repository, myNewBranchName, false));
      }
      refresh(repository);
    }
    if (checkoutResult.totalSuccess() && deleteResult.totalSuccess()) {
      GitUIUtil.notify(GitVcs.NOTIFICATION_GROUP_ID, myProject, "Rollback successful",
                       String.format("Checked out %s and deleted %s on %s %s", code(myPreviousBranch), code(myNewBranchName),
                                     StringUtil.pluralize("root", repositories.size()), successfulRepositoriesJoined()),
                       NotificationType.INFORMATION, null);
    }
    else {
      StringBuilder message = new StringBuilder();
      if (!checkoutResult.totalSuccess()) {
        message.append("Errors during checkout: ");
        message.append(checkoutResult.getErrorOutputWithReposIndication());
      }
      if (!deleteResult.totalSuccess()) {
        message.append("Errors during deleting ").append(code(myNewBranchName));
        message.append(deleteResult.getErrorOutputWithReposIndication());
      }
      GitUIUtil.notify(GitVcs.IMPORTANT_ERROR_NOTIFICATION, myProject, "Error during rollback", message.toString(), NotificationType.ERROR, null);
    }
  }

}
