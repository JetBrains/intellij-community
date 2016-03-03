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

import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.GitUtil
import git4idea.branch.GitBranchesCollection
import git4idea.config.GitVersion
import git4idea.test.GitExecutor.cd
import git4idea.test.GitExecutor.git
import git4idea.test.GitPlatformTest
import git4idea.test.GitTestUtil
import org.junit.Assume.assumeTrue
import java.io.File

abstract class GitWorkTreeBaseTest : GitPlatformTest() {

  protected lateinit var myMainRoot: String
  protected lateinit var myRepo : GitRepository

  override fun setUp() {
    super.setUp()
    cd(myTestRoot)
    assumeTrue(GitVersion.parse(git("version")).isLaterOrEqual(GitVersion(2, 5, 0, 0)))

    myMainRoot = initMainRepo()
    cd(myMainRoot)
    git("worktree add $myProjectPath")
    val gitDir = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(File(myProjectPath, GitUtil.DOT_GIT))
    assertNotNull(gitDir)
    myRepo = GitTestUtil.registerRepo(project, myProjectPath)
    assertEquals(1, myGitRepositoryManager.repositories.size)
    assertNotNull(myGitRepositoryManager.getRepositoryForRoot(myProjectRoot))
  }

  protected abstract fun initMainRepo(): String

  protected fun assertBranchHash(expectedHash: String, branches: GitBranchesCollection, branchName: String) {
    assertEquals(expectedHash, branches.getHash(branches.findBranchByName(branchName)!!)!!.asString())
  }
}
