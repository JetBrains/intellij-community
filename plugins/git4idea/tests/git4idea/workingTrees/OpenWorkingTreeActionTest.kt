// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.vcs.test.refresh
import git4idea.GitWorkingTree
import git4idea.commands.Git
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys
import git4idea.workingTrees.ui.actions.OpenWorkingTreeAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.spy
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class OpenWorkingTreeActionTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @TestDisposable
  private lateinit var testDisposable: Disposable

  private fun GitSingleRepoContext.setUpWorktree() {
    git("worktree add -B feature ../treeRoot")
    repo.ensureWorkingTreesUpToDateForTests()
    refresh()
  }

  private fun GitSingleRepoContext.mainTree(): GitWorkingTree = repo.workingTreeHolder.getWorkingTrees().single { it.isMain }
  private fun GitSingleRepoContext.linkedTree(): GitWorkingTree = repo.workingTreeHolder.getWorkingTrees().single { !it.isMain }

  @Test
  fun `test action is enabled for a linked non-current working tree`(): Unit = with(context) {
    setUpWorktree()
    val event = actionEvent(listOf(linkedTree()))
    OpenWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("A linked, non-current working tree can be opened").isTrue()
  }

  @Test
  fun `test action is disabled for the current working tree`(): Unit = with(context) {
    setUpWorktree()
    val event = actionEvent(listOf(mainTree()))
    OpenWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("The current working tree is already open").isFalse()
  }

  @Test
  fun `test action is disabled for a multiple selection`(): Unit = with(context) {
    setUpWorktree()
    val event = actionEvent(listOf(mainTree(), linkedTree()))
    OpenWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("Open supports exactly one working tree").isFalse()
  }

  @Test
  fun `test action is disabled for a prunable working tree`(): Unit = with(context) {
    setUpWorktree()
    testNioRoot.resolve("treeRoot").deleteRecursively()
    repo.ensureWorkingTreesUpToDateForTests()

    val event = actionEvent(listOf(linkedTree()))
    OpenWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("A prunable working tree cannot be opened").isFalse()
  }

  @Test
  fun `test action is disabled while the working tree is being deleted`(): Unit = with(context) {
    setUpWorktree()
    val deletionStarted = CountDownLatch(1)
    val releaseDeletion = CountDownLatch(1)
    val spiedGit = GitSpies.register(spy(Git.getInstance()))
    // Hold `git worktree remove` open, so the assertion below runs while the deletion is genuinely in flight.
    doAnswer { invocation ->
      deletionStarted.countDown()
      releaseDeletion.await(1, TimeUnit.MINUTES)
      invocation.callRealMethod()
    }.`when`(spiedGit).deleteWorkingTree(any(), any())
    ApplicationManager.getApplication().replaceService(Git::class.java, spiedGit, testDisposable)

    val toDelete = linkedTree()
    val deletion = GitWorkingTreesService.getInstance(project).deleteWorkingTrees(project, listOf(toDelete), repo)
    try {
      assertThat(deletionStarted.await(1, TimeUnit.MINUTES))
        .describedAs("The deletion must reach `git worktree remove`")
        .isTrue()

      val event = actionEvent(listOf(toDelete))
      OpenWorkingTreeAction().update(event)
      assertThat(event.presentation.isEnabled)
        .describedAs("A working tree that is being deleted must not be opened")
        .isFalse()
    }
    finally {
      releaseDeletion.countDown()
    }
    timeoutRunBlocking { deletion.join() }
    verify(spiedGit, times(1)).deleteWorkingTree(any(), any())
  }

  private fun GitSingleRepoContext.actionEvent(selection: List<GitWorkingTree>): AnActionEvent {
    val ctx = DataContext { dataId ->
      when (dataId) {
        GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES.name -> selection
        CommonDataKeys.PROJECT.name -> project
        else -> null
      }
    }
    return AnActionEvent.createEvent(OpenWorkingTreeAction(), ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  }

  companion object {
    /** A spy records the calls of a whole class, so the recorded calls are cleared once the class ends. */
    @AfterAll
    @JvmStatic
    fun clearRecordedSpyCalls() {
      GitSpies.clearRecordedCalls()
    }
  }
}
