/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
import com.intellij.openapi.util.NlsSafe;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
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
  @NotNull private final VcsNotifier myNotifier;
  @NotNull @NlsSafe private final String myCurrentName;
  @NotNull @NlsSafe private final String myNewName;

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
        markSuccessful(repository);
      }
      else {
        fatalError(GitBundle.message("git.rename.branch.could.not.rename.from.to", myCurrentName, myNewName),
                   result.getErrorOutputAsJoinedString());
        return;
      }
    }
    notifySuccess();
  }

  @Override
  protected void rollback() {
    GitCompoundResult result = new GitCompoundResult(myProject);
    Collection<GitRepository> repositories = getSuccessfulRepositories();
    for (GitRepository repository : repositories) {
      result.append(repository, myGit.renameBranch(repository, myNewName, myCurrentName));
      repository.update();
    }
    if (result.totalSuccess()) {
      myNotifier.notifySuccess(BRANCH_RENAME_ROLLBACK_SUCCESS,
                               GitBundle.message("git.rename.branch.rollback.successful"),
                               GitBundle.message("git.rename.branch.renamed.back.to", myCurrentName));
    }
    else {
      myNotifier.notifyError(BRANCH_RENAME_ROLLBACK_FAILED,
                             GitBundle.message("git.rename.branch.rollback.failed"),
                             result.getErrorOutputWithReposIndication(),
                             true);
    }
  }

  @NotNull
  @Override
  public String getSuccessMessage() {
    return GitBundle.message("git.rename.branch.was.renamed.to",
                             HtmlChunk.text(myCurrentName).code().bold(), HtmlChunk.text(myNewName).code().bold());
  }

  @NotNull
  @Override
  @Nls(capitalization = Nls.Capitalization.Sentence)
  protected String getRollbackProposal() {
    return new HtmlBuilder().append(GitBundle.message("git.rename.branch.has.succeeded.for.the.following.repositories",
                                                      getSuccessfulRepositories().size()))
      .br()
      .appendRaw(successfulRepositoriesJoined())
      .br()
      .append(GitBundle.message("git.rename.branch.you.may.rename.branch.back", myCurrentName)).toString();
  }

  @NotNull
  @Nls
  @Override
  protected String getOperationName() {
    return GitBundle.message("rename.branch.operation.name");
  }
}
