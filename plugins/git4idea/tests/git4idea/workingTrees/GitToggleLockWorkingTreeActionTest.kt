// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.GitWorkingTree
import git4idea.i18n.GitBundle
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys
import git4idea.workingTrees.ui.actions.GitToggleLockWorkingTreeAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitToggleLockWorkingTreeActionTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  private lateinit var holder: GitRepositoriesHolder

  private fun GitSingleRepoContext.setUpWorktree() {
    holder = GitRepositoriesHolder.getAndInit(project)
    git("worktree add -B feature ../treeRoot")
    repo.ensureWorkingTreesUpToDateForTests()
  }

  private fun GitSingleRepoContext.mainTree(): GitWorkingTree = repo.workingTreeHolder.getWorkingTrees().single { it.isMain }
  private fun GitSingleRepoContext.linkedTree(): GitWorkingTree = repo.workingTreeHolder.getWorkingTrees().single { !it.isMain }

  @Test
  fun `test action is disabled for the main working tree`(): Unit = with(context) {
    setUpWorktree()
    val event = actionEvent(listOf(mainTree()))
    GitToggleLockWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("The main working tree must not be lockable").isFalse()
  }

  @Test
  fun `test action offers Lock for an unlocked linked working tree`(): Unit = with(context) {
    setUpWorktree()
    val event = actionEvent(listOf(linkedTree()))
    GitToggleLockWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("An unlocked, linked working tree must be lockable").isTrue()
    assertThat(event.presentation.text).isEqualTo(GitBundle.message("action.Git.WorkingTrees.Lock.text"))
  }

  @Test
  fun `test action offers Unlock for a locked linked working tree`(): Unit = with(context) {
    setUpWorktree()
    git("worktree lock ${linkedTree().path.path}")
    repo.ensureWorkingTreesUpToDateForTests()

    val event = actionEvent(listOf(linkedTree()))
    GitToggleLockWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabled).describedAs("A locked, linked working tree must be unlockable").isTrue()
    assertThat(event.presentation.text).isEqualTo(GitBundle.message("action.Git.WorkingTrees.Unlock.text"))
  }

  @Test
  fun `test performing the action locks an unlocked working tree`(): Unit = with(context) {
    setUpWorktree()
    val toLock = linkedTree()
    assertThat(toLock.isLocked).isFalse()
    val event = actionEvent(listOf(toLock))

    holder.expectEvent(
      { GitToggleLockWorkingTreeAction().actionPerformed(event) },
      { e, _ -> e == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED },
    )

    assertThat(linkedTree().isLocked).describedAs("The working tree must now be locked").isTrue()
  }

  @Test
  fun `test performing the action unlocks a locked working tree`(): Unit = with(context) {
    setUpWorktree()
    git("worktree lock ${linkedTree().path.path}")
    repo.ensureWorkingTreesUpToDateForTests()
    val toUnlock = linkedTree()
    assertThat(toUnlock.isLocked).isTrue()
    val event = actionEvent(listOf(toUnlock))

    holder.expectEvent(
      { GitToggleLockWorkingTreeAction().actionPerformed(event) },
      { e, _ -> e == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED },
    )

    assertThat(linkedTree().isLocked).describedAs("The working tree must now be unlocked").isFalse()
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
    return AnActionEvent.createEvent(GitToggleLockWorkingTreeAction(), ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  }
}
