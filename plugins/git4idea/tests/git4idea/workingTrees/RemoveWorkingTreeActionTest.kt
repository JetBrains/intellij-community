// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionPlaces
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.test.assertSuccessfulNotification
import com.intellij.vcs.test.refresh
import git4idea.GitWorkingTree
import git4idea.commands.Git
import git4idea.i18n.GitBundle
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.workingTrees.ui.actions.GitWorkingTreeTabActionsDataKeys
import git4idea.workingTrees.ui.actions.RemoveWorkingTreeAction
import kotlinx.coroutines.CancellationException
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Proxy

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class RemoveWorkingTreeActionTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @TestDisposable
  private lateinit var testDisposable: Disposable

  private lateinit var holder: GitRepositoriesHolder

  private fun GitSingleRepoContext.setUpWorktree() {
    holder = GitRepositoriesHolder.getAndInit(project)
    git("worktree add -B feature ../treeRoot")
    repo.ensureWorkingTreesUpToDateForTests()
    refresh()
  }

  private fun GitSingleRepoContext.setUpTwoWorktrees() {
    holder = GitRepositoriesHolder.getAndInit(project)
    git("worktree add -B feature ../treeRoot")
    git("worktree add -B feature2 ../treeRoot2")
    repo.ensureWorkingTreesUpToDateForTests()
    refresh()
  }

  private fun GitSingleRepoContext.mainTree(): GitWorkingTree = repo.workingTreeHolder.getWorkingTrees().single { it.isMain }
  private fun GitSingleRepoContext.linkedTree(): GitWorkingTree = repo.workingTreeHolder.getWorkingTrees().single { !it.isMain }
  private fun GitSingleRepoContext.linkedTrees(): List<GitWorkingTree> = repo.workingTreeHolder.getWorkingTrees().filter { !it.isMain }

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

  @Test
  fun `test performing the action removes every selected linked working tree`(): Unit = with(context) {
    setUpTwoWorktrees()
    // RemoveWorkingTreeAction uses the platform Messages.showYesNoDialog, so drive the platform test dialog.
    val oldTestDialog = TestDialogManager.setTestDialog(TestDialog.YES)
    try {
      val toDelete = linkedTrees()
      assertThat(toDelete).describedAs("Both linked working trees must be set up").hasSize(2)
      val event = actionEvent(toDelete)

      RemoveWorkingTreeAction().update(event)
      assertThat(event.presentation.isEnabled).describedAs("A multi-selection of linked working trees must be removable").isTrue()

      // The trees are deleted one after another, so wait for the reload that reports both of them gone.
      holder.expectEvent(
        { RemoveWorkingTreeAction().actionPerformed(event) },
        { e, _ ->
          e == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED &&
          repo.workingTreeHolder.getWorkingTrees().none { !it.isMain }
        },
      )

      val remaining = repo.workingTreeHolder.getWorkingTrees()
      assertThat(remaining).describedAs("Only the main working tree must remain").hasSize(1)
      assertThat(remaining.single().isMain).describedAs("The remaining working tree must be the main one").isTrue()

      // The notification is shown after the last deletion, which may happen after the reload above.
      timeoutRunBlocking {
        waitUntil("the deletion notification is shown") { vcsNotifier.notifications.isNotEmpty() }
      }
      // A multi-selection is reported with a single notification counting the deleted worktrees.
      assertSuccessfulNotification(GitBundle.message("Git.WorkingTrees.delete.worktrees.success.message", 2))
      assertThat(vcsNotifier.notifications)
        .describedAs("Deleting a multi-selection must notify only once per all deleted worktrees")
        .hasSize(1)
    }
    finally {
      TestDialogManager.setTestDialog(oldTestDialog)
    }
  }

  @Test
  fun `test cancellation reports worktrees deleted before interruption`(): Unit = with(context) {
    setUpTwoWorktrees()
    val realGit = Git.getInstance()
    var deletionAttempt = 0
    // Delegate all Git operations except the second deletion, which simulates cancellation after one success.
    val interruptingGit = Proxy.newProxyInstance(Git::class.java.classLoader, arrayOf(Git::class.java)) { _, method, arguments ->
      if (method.name == "deleteWorkingTree") {
        deletionAttempt++
        if (deletionAttempt == 2) throw CancellationException("Test cancellation")
      }
      try {
        method.invoke(realGit, *(arguments ?: emptyArray()))
      }
      catch (e: InvocationTargetException) {
        throw e.targetException
      }
    } as Git
    ApplicationManager.getApplication().replaceService(Git::class.java, interruptingGit, testDisposable)

    val oldTestDialog = TestDialogManager.setTestDialog(TestDialog.YES)
    try {
      val toDelete = linkedTrees()
      val deletedBeforeCancellation = toDelete.first()
      val event = actionEvent(toDelete)

      holder.expectEvent(
        { RemoveWorkingTreeAction().actionPerformed(event) },
        { e, _ ->
          e == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED &&
          repo.workingTreeHolder.getWorkingTrees().count { !it.isMain } == 1
        },
      )

      timeoutRunBlocking {
        waitUntil("The partial deletion notification is shown") { vcsNotifier.notifications.isNotEmpty() }
      }
      assertSuccessfulNotification(
        GitBundle.message("Git.WorkingTrees.delete.worktree.success.message", deletedBeforeCancellation.path.name)
      )
      assertThat(vcsNotifier.notifications)
        .describedAs("Cancellation must not suppress the notification for already deleted worktrees")
        .hasSize(1)
      assertThat(deletionAttempt).describedAs("Deletion must be cancelled on the second worktree").isEqualTo(2)
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
