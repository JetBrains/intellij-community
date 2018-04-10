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
import git4idea.GitBranch
import git4idea.test.GitPlatformTest
import git4idea.test.TestDialogHandler
import git4idea.update.GitUpdateResult

abstract class GitPushOperationBaseTest : GitPlatformTest() {

  protected lateinit var pushSupport: GitPushSupport

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    pushSupport = getPushSupport(vcs) as GitPushSupport
  }

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus("#" + GitPushOperation::class.java.name)

  protected fun updateRepositories() {
    repositoryManager.updateAllRepositories()
  }

  protected fun agreeToUpdate(exitCode: Int) {
    dialogManager.registerDialogHandler(GitRejectedPushUpdateDialog::class.java,
                                        TestDialogHandler<GitRejectedPushUpdateDialog> { exitCode })
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
