/*
 * Copyright 2000-2017 JetBrains s.r.o.
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
package git4idea.branch

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.HtmlBuilder
import com.intellij.openapi.util.text.HtmlChunk
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.GitNotificationIdsHolder.Companion.BRANCH_CREATE_ROLLBACK_ERROR
import git4idea.GitNotificationIdsHolder.Companion.BRANCH_CREATE_ROLLBACK_SUCCESS
import git4idea.commands.Git
import git4idea.commands.GitCompoundResult
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.util.GitUIUtil.bold
import git4idea.util.GitUIUtil.code
import org.jetbrains.annotations.Nls

internal class GitCreateBranchOperation(
  project: Project,
  git: Git,
  uiHandler: GitBranchUiHandler,
  private val branchName: String,
  private val startPoints: Map<GitRepository, String>,
  private val force: Boolean) : GitBranchOperation(project, git, uiHandler, startPoints.keys) {

  public override fun execute() {
    var fatalErrorHappened = false
    while (hasMoreRepositories() && !fatalErrorHappened) {
      val repository = next()
      val result = myGit.branchCreate(repository, branchName, startPoints[repository]!!, force)

      if (result.success()) {
        repository.update()
        markSuccessful(repository)
      }
      else {
        fatalError(GitBundle.message("create.branch.operation.could.not.create.new.branch", branchName), result.errorOutputAsJoinedString)
        fatalErrorHappened = true
      }
    }

    if (!fatalErrorHappened) {
      notifySuccess()
      updateRecentBranch()
    }
  }

  override fun rollback() {
    val repositories = successfulRepositories
    val deleteResult = GitCompoundResult(myProject)
    for (repository in repositories) {
      deleteResult.append(repository, myGit.branchDelete(repository, branchName, false))
      repository.update()
    }

    val vcsNotifier = VcsNotifier.getInstance(myProject)
    if (deleteResult.totalSuccess()) {
      vcsNotifier.notifySuccess(BRANCH_CREATE_ROLLBACK_SUCCESS,
                                GitBundle.message("create.branch.operation.rollback.successful"),
                                GitBundle.message("create.branch.operation.deleted.branch", branchName))
    }
    else {
      vcsNotifier.notifyError(BRANCH_CREATE_ROLLBACK_ERROR,
                              GitBundle.message("create.branch.operation.error.during.rollback"),
                              deleteResult.errorOutputWithReposIndication,
                              true)
    }
  }

  override fun getSuccessMessage(): String = GitBundle.message("create.branch.operation.branch.created",
                                                               bold(code(branchName)))

  override fun getRollbackProposal(): String =
    HtmlBuilder()
      .append(GitBundle.message("create.branch.operation.however.the.branch.was.created.in.the.following.repositories",
                                successfulRepositories.size))
      .br()
      .appendRaw(successfulRepositoriesJoined())
      .br()
      .append(GitBundle.message("create.branch.operation.you.may.rollback.not.to.let.branches.diverge", branchName))
      .toString()

  override fun getOperationName(): @Nls String = GitBundle.message("create.branch.operation.name")
}
