// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.update

import com.intellij.openapi.components.service
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.actions.branch.GitForcePushedBranchUpdateExecutor
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.test.GitPlatformTestContext
import git4idea.test.cd
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.makeCommit
import git4idea.test.setupRepositories
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.file.Path

@TestApplication
internal class GitMultiRepoForcePushedBranchUpdateTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var repo1: GitRepository
  private lateinit var repo1Bro: Path
  private lateinit var repo2: GitRepository
  private lateinit var repo2Bro: Path

  @BeforeEach
  fun setUp() {
    with(context) {
      val repo1Trinity = setupRepositories(projectPath, "repo1_parent", "repo1_bro")

      val repo2Dir = File(projectPath, "repo2")
      check(repo2Dir.mkdir()) { "Couldn't create $repo2Dir" }
      val repo2Trinity = setupRepositories(repo2Dir.path, "repo2_parent", "repo2_bro")

      cd(projectPath)
      refresh()
      repositoryManager.updateAllRepositories()

      repo1 = repo1Trinity.projectRepo
      repo1Bro = repo1Trinity.bro
      repo2 = repo2Trinity.projectRepo
      repo2Bro = repo2Trinity.bro
    }
  }

  @Test
  fun `test multi repo update`(): Unit = with(context) {
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
    assertThat(repo1.commitsFrom("origin/master..master")).hasSize(2)
    repo2.assertExists("localFile3.txt")
    repo2.assertExists("localFile4.txt")
    assertThat(repo2.commitsFrom("origin/master..master")).hasSize(2)
    assertThat(repo1.branches.localBranches).hasSize(1)
    assertThat(repo2.branches.localBranches).hasSize(1)
    assertNotificationByMessage(GitBundle.message("action.git.update.force.pushed.branch.success"))
  }
}
