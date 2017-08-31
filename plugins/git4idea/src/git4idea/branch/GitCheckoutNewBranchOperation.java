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

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsNotifier;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static git4idea.util.GitUIUtil.code;

/**
 * Create new branch (starting from the current branch) and check it out.
 */
class GitCheckoutNewBranchOperation extends GitBranchOperation {

  @NotNull private final Project myProject;
  @NotNull private final String myNewBranchName;

  GitCheckoutNewBranchOperation(@NotNull Project project, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler,
                                @NotNull Collection<GitRepository> repositories, @NotNull String newBranchName) {
    super(project, git, uiHandler, repositories);
    myNewBranchName = newBranchName;
    myProject = project;
  }

  @Override
  protected void execute() {
    boolean fatalErrorHappened = false;
    while (hasMoreRepositories() && !fatalErrorHappened) {
      final GitRepository repository = next();

      GitSimpleEventDetector unmergedDetector = new GitSimpleEventDetector(GitSimpleEventDetector.Event.UNMERGED_PREVENTING_CHECKOUT);
      GitCommandResult result = myGit.checkoutNewBranch(repository, myNewBranchName, unmergedDetector);

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
      updateRecentBranch(myNewBranchName);
    }
  }

  private static void refresh(@NotNull GitRepository repository) {
    repository.update();
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
           "<br/>You may rollback (checkout previous branch back, and delete " + myNewBranchName + ") not to let branches diverge.";
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
      GitCommandResult result = myGit.checkout(repository, myCurrentHeads.get(repository), null, true, false);
      checkoutResult.append(repository, result);
      if (result.success()) {
        deleteResult.append(repository, myGit.branchDelete(repository, myNewBranchName, false));
      }
      refresh(repository);
    }
    if (checkoutResult.totalSuccess() && deleteResult.totalSuccess()) {
      VcsNotifier.getInstance(myProject).notifySuccess("Rollback successful", String
        .format("Checked out %s and deleted %s on %s %s", stringifyBranchesByRepos(myCurrentHeads), code(myNewBranchName),
                StringUtil.pluralize("root", repositories.size()), successfulRepositoriesJoined()));
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
      VcsNotifier.getInstance(myProject).notifyError("Error during rollback", message.toString());
    }
  }

}
