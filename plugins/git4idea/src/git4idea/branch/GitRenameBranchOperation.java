// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.vcs.BranchRenameListener;
import com.intellij.openapi.vcs.VcsNotifier;
import git4idea.commands.Git;
import git4idea.commands.GitCommandResult;
import git4idea.commands.GitCompoundResult;
import git4idea.i18n.GitBundle;
import git4idea.repo.GitRepository;
import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NotNull;

import java.util.Collection;
import java.util.List;

import static git4idea.GitNotificationIdsHolder.BRANCH_RENAME_ROLLBACK_FAILED;
import static git4idea.GitNotificationIdsHolder.BRANCH_RENAME_ROLLBACK_SUCCESS;

class GitRenameBranchOperation extends GitBranchOperation {
  private final @NotNull VcsNotifier myNotifier;
  private final @NotNull @NlsSafe String myCurrentName;
  private final @NotNull @NlsSafe String myNewName;

  GitRenameBranchOperation(@NotNull Project project,
                           @NotNull Git git,
                           @NotNull GitBranchUiHandler uiHandler,
                           @NotNull @NlsSafe String currentName,
                           @NotNull @NlsSafe String newName,
                           @NotNull List<? extends GitRepository> repositories) {
    super(project, git, uiHandler, repositories);
    myCurrentName = currentName;
    myNewName = newName;
    myNotifier = VcsNotifier.getInstance(myProject);
  }

  @Override
  protected void execute() {
    while (hasMoreRepositories()) {
      GitRepository repository = next();
      GitCommandResult result = myGit.renameBranch(repository, myCurrentName, myNewName);
      if (result.success()) {
        repository.update();
        notifyBranchNameChanged(repository, myCurrentName, myNewName);
        markSuccessful(repository);
      }
      else {
        fatalError(GitBundle.message("git.rename.branch.could.not.rename.from.to", myCurrentName, myNewName), result);
        return;
      }
    }
    notifySuccess();
  }

  @Override
  protected void rollback() {
    GitCompoundResult compoundResult = new GitCompoundResult(myProject);
    Collection<GitRepository> repositories = getSuccessfulRepositories();
    for (GitRepository repository : repositories) {
      GitCommandResult result = myGit.renameBranch(repository, myNewName, myCurrentName);
      if (result.success()) {
        repository.update();
        notifyBranchNameChanged(repository, myNewName, myCurrentName);
      }
      compoundResult.append(repository, result);
    }
    if (compoundResult.totalSuccess()) {
      myNotifier.notifySuccess(BRANCH_RENAME_ROLLBACK_SUCCESS,
                               GitBundle.message("git.rename.branch.rollback.successful"),
                               GitBundle.message("git.rename.branch.renamed.back.to", myCurrentName));
    }
    else {
      myNotifier.notifyError(BRANCH_RENAME_ROLLBACK_FAILED,
                             GitBundle.message("git.rename.branch.rollback.failed"),
                             compoundResult.getErrorOutputWithReposIndication(),
                             true);
    }
  }

  protected final void notifyBranchNameChanged(@NotNull GitRepository repository, @NotNull String oldName, @NotNull String newName) {
    ApplicationManager.getApplication().invokeLater(() -> {
      if (myProject.isDisposed()) return;
      myProject.getMessageBus().syncPublisher(BranchRenameListener.VCS_BRANCH_RENAMED)
        .branchNameChanged(repository.getRoot(), oldName, newName);
    });
  }

  @Override
  protected @NotNull String getSuccessMessage() {
    return GitBundle.message("git.rename.branch.was.renamed.to",
                             HtmlChunk.text(myCurrentName).code().bold(), HtmlChunk.text(myNewName).code().bold());
  }

  @Override
  protected @NotNull @Nls(capitalization = Nls.Capitalization.Sentence) String getRollbackProposal() {
    return new HtmlBuilder().append(GitBundle.message("git.rename.branch.has.succeeded.for.the.following.repositories",
                                                      getSuccessfulRepositories().size()))
      .br()
      .appendRaw(successfulRepositoriesJoined())
      .br()
      .append(GitBundle.message("git.rename.branch.you.may.rename.branch.back", myCurrentName)).toString();
  }

  @Override
  protected @NotNull @Nls String getOperationName() {
    return GitBundle.message("rename.branch.operation.name");
  }
}
