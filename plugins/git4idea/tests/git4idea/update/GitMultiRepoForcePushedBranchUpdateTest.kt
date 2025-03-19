// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.components.service
import com.intellij.openapi.vcs.Executor.cd
import git4idea.actions.branch.GitForcePushedBranchUpdateExecutor
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.test.*
import java.io.File
import java.nio.file.Path

class GitMultiRepoForcePushedBranchUpdateTest : GitForcePushedBranchUpdateBaseTest() {

  private lateinit var repo1: GitRepository
  private lateinit var repo1Bro: Path

  private lateinit var repo2: GitRepository
  private lateinit var repo2Bro: Path

  @Throws(Exception::class)
  override fun setUp() {
    super.setUp()

    val repo1Trinity = setupRepositories(projectPath, "repo1_parent", "repo1_bro")
    repo1 = repo1Trinity.projectRepo
    repo1Bro = repo1Trinity.bro

    val repo2Dir = File(projectPath, "repo2")
    assertTrue(repo2Dir.mkdir())

    val repo2Trinity = setupRepositories(repo2Dir.path, "repo2_parent", "repo2_bro")
    repo2 = repo2Trinity.projectRepo
    repo2Bro = repo2Trinity.bro

    cd(projectPath)
    refresh()
    repositoryManager.updateAllRepositories()
  }

  fun `test multi repo update`() {
    cd(repo1Bro)
    makeCommit("fileInRepo1.txt")
    git("push -f")
    repo1.assertNotExists("fileInRepo1.txt")

    cd(repo1)
    makeCommit("localFile1.txt")
    makeCommit("localFile2.txt")

    cd(repo2Bro)
    makeCommit("fileInRepo2.txt")
    git("push -f")
    repo2.assertNotExists("fileInRepo2.txt")

    cd(repo2)
    makeCommit("localFile3.txt")
    makeCommit("localFile4.txt")

    updateChangeListManager()

    val updateExecutor = project.service<GitForcePushedBranchUpdateExecutor>()
    updateExecutor.updateCurrentBranch()
    updateExecutor.waitForUpdate()

    repo1.assertExists("fileInRepo1.txt")
    repo1.assertExists("localFile1.txt")
    repo1.assertExists("localFile2.txt")
    assertTrue(repo1.commitsFrom("origin/master..master").size == 2)
    repo2.assertExists("localFile3.txt")
    repo2.assertExists("localFile4.txt")
    assertTrue(repo2.commitsFrom("origin/master..master").size == 2)
    assertTrue(repo1.branches.localBranches.size == 1)
    assertTrue(repo2.branches.localBranches.size == 1)
    assertNotificationByMessage(GitBundle.message("action.git.update.force.pushed.branch.success"))
  }
}
