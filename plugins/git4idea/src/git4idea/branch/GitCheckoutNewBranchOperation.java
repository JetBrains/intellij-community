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
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import git4idea.Git;
import git4idea.GitVcs;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.repo.GitRepository;
import git4idea.ui.GitUIUtil;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static git4idea.ui.GitUIUtil.code;

/**
* @author Kirill Likhodedov
*/
public class GitCheckoutNewBranchOperation implements GitBranchOperation {

  private final Collection<GitRepository> myRepositories;
  private final String myNewBranchName;
  private Project myProject;
  private final String myPreviousBranch;

  public GitCheckoutNewBranchOperation(@NotNull Project project,
                                       @NotNull Collection<GitRepository> repositories,
                                       @NotNull String newBranchName,
                                       @NotNull String previousBranch) {
    myRepositories = repositories;
    myNewBranchName = newBranchName;
    myProject = project;
    myPreviousBranch = previousBranch;
  }

  @NotNull
  @Override
  public GitBranchOperationResult execute(@NotNull GitRepository repository) {
    GitSimpleEventDetector unmergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED);
    GitCommandResult result = Git.checkoutNewBranch(repository, myNewBranchName, unmergedDetector);
    if (result.success()) {
      return GitBranchOperationResult.success();
    }
    else if (unmergedDetector.hasHappened()) {
      return GitBranchOperationResult.resolvable();
    }
    else {
      return GitBranchOperationResult.error("Couldn't create new branch " + myNewBranchName, result.getErrorOutputAsJoinedString());
    }
  }

  @NotNull
  @Override
  public GitBranchOperationResult tryResolve() {
    return GitBranchUtil.proposeToResolveUnmergedFiles(myProject, myRepositories, "Couldn't create new branch " + myNewBranchName,
                                                       "Couldn't create new branch due to unmerged files. ");
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    return String.format("Branch <b><code>%s</code></b> was created", myNewBranchName);
  }

  @Override
  public boolean showFatalError() {
    return false;
  }

  @Override
  public boolean rollbackable() {
    return true;
  }

  @Override
  public void rollback(@NotNull Collection<GitRepository> repositories) {
    GitCompoundResult checkoutResult = new GitCompoundResult(myProject);
    GitCompoundResult deleteResult = new GitCompoundResult(myProject);
    for (GitRepository repository : repositories) {
      GitCommandResult result = Git.checkout(repository, myPreviousBranch, null);
      checkoutResult.append(repository, result);
      if (result.success()) {
        deleteResult.append(repository, Git.branchDelete(repository, myNewBranchName, false));
      }
    }
    if (checkoutResult.totalSuccess() && deleteResult.totalSuccess()) {
      GitUIUtil.notify(GitVcs.NOTIFICATION_GROUP_ID, myProject, "Rollback successful",
                       String.format("Checked out %s and deleted %s on %s %s", code(myPreviousBranch), code(myNewBranchName),
                                     StringUtil.pluralize("root", repositories.size()), GitMultiRootOperationExecutor
                         .joinRepositoryUrls(repositories, "<br/>")),
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
