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

import com.intellij.dvcs.push.PushSpec
import com.intellij.dvcs.push.PushSupport
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.util.Condition
import com.intellij.openapi.util.Trinity
import com.intellij.openapi.vcs.AbstractVcsHelper
import com.intellij.openapi.vcs.Executor
import com.intellij.util.ObjectUtils
import com.intellij.util.containers.ContainerUtil
import git4idea.GitBranch
import git4idea.GitRemoteBranch
import git4idea.GitStandardRemoteBranch
import git4idea.GitUtil
import git4idea.repo.GitRepository
import git4idea.test.GitExecutor.cd
import git4idea.test.GitExecutor.git
import git4idea.test.GitPlatformTest
import git4idea.test.GitTestUtil
import git4idea.test.MockVcsHelper
import git4idea.test.TestDialogHandler
import git4idea.update.GitUpdateResult
import java.io.File

abstract class GitPushOperationBaseTest : GitPlatformTest() {

  protected lateinit var myPushSupport: GitPushSupport
  protected lateinit var myVcsHelper: MockVcsHelper

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    myPushSupport = findGitPushSupport()
    myVcsHelper = GitTestUtil.overrideService(myProject, AbstractVcsHelper::class.java, MockVcsHelper::class.java)
  }

  override fun refresh() {
    super.refresh()
    myGitRepositoryManager.updateAllRepositories()
  }

  override fun getDebugLogCategories(): Collection<String> {
    return listOf("#" + GitPushOperation::class.java.name)
  }

  protected fun setupRepositories(repoRoot: String, parentName: String, broName: String): Trinity<GitRepository, File, File> {
    val parentRepo = createParentRepo(parentName)
    val broRepo = createBroRepo(broName, parentRepo)

    val repository = GitTestUtil.createRepository(myProject, repoRoot)
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


  private fun findGitPushSupport(): GitPushSupport {
    return ObjectUtils.assertNotNull(ContainerUtil.find(Extensions.getExtensions(PushSupport.PUSH_SUPPORT_EP, myProject),
        object : Condition<PushSupport<*, *, *>> {
          override fun value(support: PushSupport<*, *, *>): Boolean {
            return support is GitPushSupport
          }
        })) as GitPushSupport
  }

  protected fun makePushSpec(repository: GitRepository,
                             from: String,
                             to: String): PushSpec<GitPushSource, GitPushTarget> {
    val source = repository.branches.findLocalBranch(from)!!
    var target: GitRemoteBranch? = repository.branches.findBranchByName(to) as GitRemoteBranch?
    val newBranch: Boolean
    if (target == null) {
      val firstSlash = to.indexOf('/')
      val remote = GitUtil.findRemoteByName(repository, to.substring(0, firstSlash))!!
      target = GitStandardRemoteBranch(remote, to.substring(firstSlash + 1))
      newBranch = true
    }
    else {
      newBranch = false
    }
    return PushSpec(GitPushSource.create(source), GitPushTarget(target, newBranch))
  }
}
