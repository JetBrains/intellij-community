// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.deleteRecursively
import com.intellij.vcs.test.refresh
import git4idea.GitWorkingTree
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys
import git4idea.workingTrees.ui.actions.OpenWorkingTreeAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class OpenWorkingTreeActionTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

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
}
