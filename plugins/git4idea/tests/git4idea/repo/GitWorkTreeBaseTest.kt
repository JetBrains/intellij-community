// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
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
    git(project, "worktree add $projectPath")
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
