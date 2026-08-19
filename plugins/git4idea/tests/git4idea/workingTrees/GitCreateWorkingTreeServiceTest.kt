// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitLocalBranch
import git4idea.commands.GitBranchAlreadyCheckedOutInOtherWorktreeDetector
import git4idea.repo.GitRepository
import git4idea.test.GitSingleRepoContext
import git4idea.test.branch
import git4idea.test.gitSingleRepoContextFixture
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

@TestApplication
internal class GitCreateWorkingTreeServiceTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @AfterEach
  fun afterEach() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  @Test
  fun `test pre-check dialog message includes worktree path and proceeding sets force`(): Unit = with(context) {
    val branch = createBranch(repo, "feature")
    var shownMessage: String? = null
    TestDialogManager.setTestDialog { message ->
      shownMessage = message
      Messages.YES
    }

    val proceed = runBlocking {
      GitCreateWorkingTreeService.getInstance().confirmCreateNewWorktreeInsteadOfOpening(project, branch, "/other/worktree/path")
    }

    assertThat(proceed).isTrue()
    assertThat(shownMessage).contains("/other/worktree/path")
  }

  @Test
  fun `test pre-check dialog declining proceed opens existing worktree instead`(): Unit = with(context) {
    val branch = createBranch(repo, "feature")
    TestDialogManager.setTestDialog(TestDialog.NO)

    val proceed = runBlocking {
      GitCreateWorkingTreeService.getInstance().confirmCreateNewWorktreeInsteadOfOpening(project, branch, null)
    }

    assertThat(proceed).isFalse()
  }

  @Test
  fun `test post-failure retry dialog message includes worktree path and proceeding retries`(): Unit = with(context) {
    val match = GitBranchAlreadyCheckedOutInOtherWorktreeDetector.matchInOutput(
      listOf("fatal: 'feature' is already used by worktree at '/other/worktree/path'"))!!
    var shownMessage: String? = null
    TestDialogManager.setTestDialog { message ->
      shownMessage = message
      Messages.YES
    }

    val retry = runBlocking {
      GitCreateWorkingTreeService.getInstance().confirmCreateWorktreeIgnoringOtherWorktree(project, match.branchName, match.worktreePath)
    }

    assertThat(retry).isTrue()
    assertThat(shownMessage).contains("/other/worktree/path")
  }

  @Test
  fun `test post-failure retry dialog cancelled does not retry`(): Unit = with(context) {
    val match = GitBranchAlreadyCheckedOutInOtherWorktreeDetector.matchInOutput(
      listOf("fatal: 'feature' is already used by worktree at '/other/worktree/path'"))!!
    TestDialogManager.setTestDialog(TestDialog.NO)

    val retry = runBlocking {
      GitCreateWorkingTreeService.getInstance().confirmCreateWorktreeIgnoringOtherWorktree(project, match.branchName, match.worktreePath)
    }

    assertThat(retry).isFalse()
  }

  private fun createBranch(repo: GitRepository, branchName: String): GitLocalBranch {
    repo.branch(branchName)
    repo.update()
    val newBranch = repo.branches.findLocalBranch(branchName)
    assertThat(newBranch).describedAs("Branch $branchName was not created").isNotNull()
    return newBranch!!
  }
}
