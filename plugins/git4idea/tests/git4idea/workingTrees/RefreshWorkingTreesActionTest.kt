// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.workingTrees.ui.actions.RefreshWorkingTreesAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class RefreshWorkingTreesActionTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test action is enabled when the project has a worktree-capable repository`(): Unit = with(context) {
    val event = actionEvent()
    RefreshWorkingTreesAction().update(event)
    assertThat(event.presentation.isEnabled).isTrue()
  }

  @Test
  @RegistryKey("git.enable.working.trees.feature", "false")
  fun `test action is disabled when the feature is off`(): Unit = with(context) {
    val event = actionEvent()
    RefreshWorkingTreesAction().update(event)
    assertThat(event.presentation.isEnabled).isFalse()
  }

  private fun GitSingleRepoContext.actionEvent(): AnActionEvent {
    val ctx = DataContext { dataId -> if (dataId == CommonDataKeys.PROJECT.name) project else null }
    return AnActionEvent.createEvent(RefreshWorkingTreesAction(), ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  }
}
