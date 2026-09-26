// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.actions.GitSingleRefActions
import git4idea.GitReference
import git4idea.i18n.GitBundle
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.workingTrees.actions.GitCreateWorkingTreeAction
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitCreateWorkingTreeActionTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  @RegistryKey("git.enable.working.trees.feature", "false")
  fun `test action is hidden when the feature is off`(): Unit = with(context) {
    val event = actionEvent(ref = null)
    GitCreateWorkingTreeAction().update(event)
    assertThat(event.presentation.isVisible).describedAs("The action must be hidden when the feature is unsupported").isFalse()
    assertThat(event.presentation.isEnabled).isFalse()
  }

  @Test
  fun `test action shows the generic text without a context ref`(): Unit = with(context) {
    val event = actionEvent(ref = null)
    GitCreateWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo(GitBundle.message("action.Git.CreateNewWorkingTree.text"))
  }

  @Test
  fun `test action shows the from-branch text for a branch context ref`(): Unit = with(context) {
    val branch = repo.currentBranch!!
    val event = actionEvent(ref = branch)
    GitCreateWorkingTreeAction().update(event)
    assertThat(event.presentation.isEnabledAndVisible).isTrue()
    assertThat(event.presentation.text).isEqualTo(GitBundle.message("action.Git.CreateNewWorkingTree.from.branch.text", branch.name))
  }

  private fun GitSingleRepoContext.actionEvent(ref: GitReference?): AnActionEvent {
    val ctx = DataContext { dataId ->
      when (dataId) {
        CommonDataKeys.PROJECT.name -> project
        GitSingleRefActions.SELECTED_REF_DATA_KEY.name -> ref
        else -> null
      }
    }
    return AnActionEvent.createEvent(GitCreateWorkingTreeAction(), ctx, null, ActionPlaces.UNKNOWN, ActionUiKind.NONE, null)
  }
}
