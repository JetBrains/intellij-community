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
package git4idea.repo

import com.intellij.openapi.vcs.Executor.cd
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.GitUtil
import git4idea.branch.GitBranchesCollection
import git4idea.config.GitVersion
import git4idea.test.GitPlatformTest
import git4idea.test.git
import git4idea.test.registerRepo
import git4idea.test.setupDefaultUsername
import org.junit.Assume.assumeTrue
import java.io.File

abstract class GitWorkTreeBaseTest : GitPlatformTest() {

  protected lateinit var myMainRoot: String
  protected lateinit var myRepo : GitRepository

  private fun supportsWorktrees(version: GitVersion) = version.isLaterOrEqual(GitVersion(2, 5, 0, 0))

  override fun setUp() {
    super.setUp()
    cd(testRoot)
    assumeTrue("Worktrees are not supported in " + vcs.version, supportsWorktrees(vcs.version))

    myMainRoot = initMainRepo()
    cd(myMainRoot)
    git("worktree add $projectPath")
    val gitDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(projectPath, GitUtil.DOT_GIT))
    assertNotNull(gitDir)
    myRepo = registerRepo(project, projectPath)
    setupDefaultUsername()
    assertEquals(1, repositoryManager.repositories.size)
    assertNotNull(repositoryManager.getRepositoryForRoot(projectRoot))
  }

  protected abstract fun initMainRepo(): String

  protected fun assertBranchHash(expectedHash: String, branches: GitBranchesCollection, branchName: String) {
    assertEquals(expectedHash, branches.getHash(branches.findBranchByName(branchName)!!)!!.asString())
  }
}
