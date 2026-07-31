// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.test.refresh
import git4idea.GitWorkingTree
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys
import git4idea.workingTrees.ui.actions.RemoveWorkingTreeAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class RemoveWorkingTreeActionTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  private lateinit var holder: GitRepositoriesHolder

  private fun GitSingleRepoContext.setUpWorktree() {
    holder = GitRepositoriesHolder.getAndInit(project)
    git("worktree add -B feature ../treeRoot")
    repo.ensureWorkingTreesUpToDateForTests()
    refresh()
  }

  private fun GitSingleRepoContext.mainTree(): GitWorkingTree = repo.workingTreeHolder.getWorkingTrees().single { it.isMain }
  private fun GitSingleRepoContext.linkedTree(): GitWorkingTree = repo.workingTreeHolder.getWorkingTrees().single { !it.isMain }

  @Test
  fun `test action is disabled for the main working tree`(): Unit = with(context) {
    setUpWorktree()
    val event = actionEvent(listOf(mainTree()))
    RemoveWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("The main working tree must not be removable").isFalse()
  }

  @Test
  fun `test action is enabled for a linked non-current working tree`(): Unit = with(context) {
    setUpWorktree()
    val event = actionEvent(listOf(linkedTree()))
    RemoveWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("A linked, non-current working tree must be removable").isTrue()
  }

  @Test
  fun `test action is disabled for a selection that includes the main working tree`(): Unit = with(context) {
    setUpWorktree()
    val event = actionEvent(listOf(linkedTree(), mainTree()))
    RemoveWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("A mixed selection containing the main working tree must be disabled").isFalse()
  }

  @Test
  fun `test performing the action removes the linked working tree`(): Unit = with(context) {
    setUpWorktree()
    // RemoveWorkingTreeAction uses the platform Messages.showYesNoDialog, so drive the platform test dialog.
    val oldTestDialog = TestDialogManager.setTestDialog(TestDialog.YES)
    try {
      val toDelete = linkedTree()
      val event = actionEvent(listOf(toDelete))

      holder.expectEvent(
        { RemoveWorkingTreeAction().actionPerformed(event) },
        { e, _ -> e == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED },
      )

      val remaining = repo.workingTreeHolder.getWorkingTrees()
      assertThat(remaining).describedAs("Only the main working tree must remain").hasSize(1)
      assertThat(remaining.single().isMain).describedAs("The remaining working tree must be the main one").isTrue()
    }
    finally {
      TestDialogManager.setTestDialog(oldTestDialog)
    }
  }

  private fun GitSingleRepoContext.actionEvent(selection: List<GitWorkingTree>): AnActionEvent {
    val ctx = DataContext { dataId ->
      when (dataId) {
        GitWorkingTreeTabActionsDataKeys.SELECTED_WORKING_TREES.name -> selection
        GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY.name -> repo
        CommonDataKeys.PROJECT.name -> project
        else -> null
      }
    }
    return AnActionEvent.createEvent(RemoveWorkingTreeAction(), ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  }
}
