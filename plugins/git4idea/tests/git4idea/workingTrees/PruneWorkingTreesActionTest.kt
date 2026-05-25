// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.utils.io.deleteRecursively
import git4idea.actions.workingTree.GitWorkingTreeTabActionsDataKeys
import git4idea.test.GitSingleRepoTest
import git4idea.workingTrees.GitWorkingTreeTestBase.Companion.ensureWorkingTreesUpToDateForTests
import git4idea.workingTrees.ui.PruneWorkingTreesAction

class PruneWorkingTreesActionTest : GitSingleRepoTest() {

  fun `test action is disabled when no prunable trees`() {
    val event = createActionEvent()
    PruneWorkingTreesAction().update(event)
    assertFalse("Action should be disabled when there are no prunable trees", event.presentation.isEnabled)
  }

  fun `test action is enabled when prunable tree exists`() {
    git("worktree add -B tree ../treeRoot")
    testNioRoot.resolve("treeRoot").deleteRecursively()
    repo.ensureWorkingTreesUpToDateForTests()

    val event = createActionEvent()
    PruneWorkingTreesAction().update(event)
    assertTrue("Action should be enabled when prunable trees exist", event.presentation.isEnabled)
  }

  private fun createActionEvent(): AnActionEvent {
    val action = PruneWorkingTreesAction()
    val ctx = DataContext { dataId -> if (dataId == GitWorkingTreeTabActionsDataKeys.CURRENT_REPOSITORY.name) repo else null }
    return AnActionEvent.createEvent(action, ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  }
}