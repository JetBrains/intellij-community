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
import com.intellij.openapi.vcs.VcsNotifier
import git4idea.commands.Git
import git4idea.commands.GitCompoundResult
import git4idea.repo.GitRepository

internal class GitCreateBranchOperation(
  project: Project,
  git: Git,
  uiHandler: GitBranchUiHandler,
  private val branchName: String,
  private val startPoints: Map<GitRepository, String>) : GitBranchOperation(project, git, uiHandler, startPoints.keys) {

  public override fun execute() {
    var fatalErrorHappened = false
    while (hasMoreRepositories() && !fatalErrorHappened) {
      val repository = next()
      val result = myGit.branchCreate(repository, branchName, startPoints[repository]!!)

      if (result.success()) {
        repository.update()
        markSuccessful(repository)
      }
      else {
        fatalError("Couldn't create new branch $branchName", result.errorOutputAsJoinedString)
        fatalErrorHappened = true
      }
    }

    if (!fatalErrorHappened) {
      notifySuccess()
      updateRecentBranch(branchName)
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
      vcsNotifier.notifySuccess("Rollback successful", "Deleted $branchName")
    }
    else {
      vcsNotifier.notifyError("Error during rollback", deleteResult.errorOutputWithReposIndication)
    }
  }

  override fun getSuccessMessage() = "Branch <b><code>$branchName</code></b> was created"

  override fun getRollbackProposal() = """
    However the branch was created in the following ${repositories()}:<br/>
    ${successfulRepositoriesJoined()}<br/>
    You may rollback (delete $branchName) not to let branches diverge.""".trimIndent()

  override fun getOperationName() = "create branch"
}
