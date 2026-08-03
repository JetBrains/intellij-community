// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.utils.io.deleteRecursively
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.workingTrees.ui.PruneWorkingTreesAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class PruneWorkingTreesActionTest {
  private val contextFixture = gitWorkingTreeSingleRepoFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test action is disabled when no prunable trees`() {
    val event = createActionEvent()
    PruneWorkingTreesAction().update(event)
    assertThat(event.presentation.isEnabled)
      .describedAs("Action should be disabled when there are no prunable trees")
      .isFalse()
  }

  @Test
  fun `test action is enabled when prunable tree exists`(): Unit = with(context) {
    git("worktree add -B tree ../treeRoot")
    testNioRoot.resolve("treeRoot").deleteRecursively()
    repo.ensureWorkingTreesUpToDateForTests()

    val event = createActionEvent()
    PruneWorkingTreesAction().update(event)
    assertThat(event.presentation.isEnabled)
      .describedAs("Action should be enabled when prunable trees exist")
      .isTrue()
  }

  private fun createActionEvent(): AnActionEvent {
    val action = PruneWorkingTreesAction()
    val repo = context.repo
    val ctx = DataContext { dataId -> if (dataId == GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY.name) repo else null }
    return AnActionEvent.createEvent(action, ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  }
}
