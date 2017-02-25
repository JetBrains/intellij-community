/*
 * Copyright 2000-2015 JetBrains s.r.o.
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
package git4idea.push

import com.intellij.dvcs.DvcsUtil.getPushSupport
import com.intellij.openapi.util.Trinity
import com.intellij.openapi.vcs.Executor
import git4idea.GitBranch
import git4idea.repo.GitRepository
import git4idea.test.*
import git4idea.update.GitUpdateResult
import java.io.File

abstract class GitPushOperationBaseTest : GitPlatformTest() {

  protected lateinit var myPushSupport: GitPushSupport

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myPushSupport = getPushSupport(myVcs) as GitPushSupport
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#" + GitPushOperation::class.java.name)

  protected fun updateRepositories() {
    myGitRepositoryManager.updateAllRepositories()
  }

  protected fun setupRepositories(repoRoot: String, parentName: String, broName: String): Trinity<GitRepository, File, File> {
    val parentRepo = createParentRepo(parentName)
    val broRepo = createBroRepo(broName, parentRepo)

    val repository = createRepository(myProject, repoRoot)
    cd(repository)
    git("remote add origin " + parentRepo.path)
    git("push --set-upstream origin master:master")

    Executor.cd(broRepo.path)
    git("pull")

    return Trinity.create(repository, parentRepo, broRepo)
  }

  private fun createParentRepo(parentName: String): File {
    Executor.cd(myTestRoot)
    git("init --bare $parentName.git")
    return File(myTestRoot, parentName + ".git")
  }

  private fun createBroRepo(broName: String, parentRepo: File): File {
    Executor.cd(myTestRoot)
    git("clone " + parentRepo.name + " " + broName)
    return File(myTestRoot, broName)
  }

  protected fun agreeToUpdate(exitCode: Int) {
    myDialogManager.registerDialogHandler(GitRejectedPushUpdateDialog::class.java, object : TestDialogHandler<GitRejectedPushUpdateDialog> {
      override fun handleDialog(dialog: GitRejectedPushUpdateDialog): Int {
        return exitCode
      }
    })
  }

  internal fun assertResult(type: GitPushRepoResult.Type, pushedCommits: Int, from: String, to: String,
                             updateResult: GitUpdateResult?,
                             actualResult: GitPushRepoResult) {
    val message = "Result is incorrect: " + actualResult
    assertEquals(message, type, actualResult.type)
    assertEquals(message, pushedCommits, actualResult.numberOfPushedCommits)
    assertEquals(message, GitBranch.REFS_HEADS_PREFIX + from, actualResult.sourceBranch)
    assertEquals(message, GitBranch.REFS_REMOTES_PREFIX + to, actualResult.targetBranch)
    assertEquals(message, updateResult, actualResult.updateResult)
  }
}
