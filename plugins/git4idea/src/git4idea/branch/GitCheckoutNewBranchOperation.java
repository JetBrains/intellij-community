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
import com.intellij.openapi.util.NlsContexts;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.vcs.VcsNotifier;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.commands.GitSimpleEventDetector;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;

import static git4idea.GitNotificationIdsHolder.CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_ERROR;
import static git4idea.GitNotificationIdsHolder.CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_SUCCESSFUL;
import static git4idea.util.GitUIUtil.bold;
import static git4idea.util.GitUIUtil.code;

/**
 * Create new branch (starting from the current branch) and check it out.
 */
class GitCheckoutNewBranchOperation extends GitBranchOperation {
  @NotNull private final String myNewBranchName;

  GitCheckoutNewBranchOperation(@NotNull Project project, @NotNull Git git, @NotNull GitBranchUiHandler uiHandler,
                                @NotNull Collection<? extends GitRepository> repositories, @NotNull String newBranchName) {
    super(project, git, uiHandler, repositories);
    myNewBranchName = newBranchName;
  }

  @Override
  protected void execute() {
    boolean fatalErrorHappened = false;
    notifyBranchWillChange();
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
        fatalError(GitBundle.message("checkout.new.branch.operation.could.not.create.new.branch", myNewBranchName),
                   result.getErrorOutputAsJoinedString());
        fatalErrorHappened = true;
      }
    }

    if (!fatalErrorHappened) {
      notifySuccess();
      notifyBranchHasChanged(myNewBranchName);
      updateRecentBranch();
    }
  }

  private static void refresh(@NotNull GitRepository repository) {
    repository.update();
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    return GitBundle.message("checkout.new.branch.operation.branch.was.created", bold(code(myNewBranchName)));
  }

  @NotNull
  @Override
  protected String getRollbackProposal() {
    return new HtmlBuilder().append(GitBundle.message("checkout.new.branch.operation.however.checkout.has.succeeded.for.the.following",
                                                      getSuccessfulRepositories().size()))
      .br()
      .appendRaw(successfulRepositoriesJoined())
      .br()
      .append(GitBundle.message("checkout.new.branch.operation.you.may.rollback.not.to.let.branches.diverge", myNewBranchName))
      .toString();
  }

  @NotNull
  @Nls
  @Override
  protected String getOperationName() {
    return GitBundle.message("checkout.operation.name");
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
      String message = GitBundle
        .message("checkout.new.branch.operation.checked.out.0.and.deleted.1.on.2.3",
                 stringifyBranchesByRepos(myCurrentHeads),
                 code(myNewBranchName),
                 repositories.size(),
                 successfulRepositoriesJoined());
      VcsNotifier.getInstance(myProject).notifySuccess(CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_SUCCESSFUL,
                                                       GitBundle.message("checkout.new.branch.operation.rollback.successful"), message);
    }
    else {
      @NlsContexts.NotificationContent StringBuilder message = new StringBuilder();
      if (!checkoutResult.totalSuccess()) {
        message.append(GitBundle.message("checkout.new.branch.operation.errors.during.checkout"));
        message.append(checkoutResult.getErrorOutputWithReposIndication());
      }
      if (!deleteResult.totalSuccess()) {
        message.append(GitBundle.message("checkout.new.branch.operation.errors.during.deleting", code(myNewBranchName)));
        message.append(deleteResult.getErrorOutputWithReposIndication());
      }
      VcsNotifier.getInstance(myProject)
        .notifyError(CHECKOUT_NEW_BRANCH_OPERATION_ROLLBACK_ERROR,
                     GitBundle.message("checkout.new.branch.operation.error.during.rollback"),
                     message.toString(),
                     true);
    }
  }

}
