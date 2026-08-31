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
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

      val service = GitWorkingTreesService.getInstance(project)
      timeoutRunBlocking {
        waitUntil("the cancelled deletion released its claim") {
          toDelete.none { service.isWorkingTreeDeletionInProgress(it) }
        }
      }
    }
    finally {
      TestDialogManager.setTestDialog(oldTestDialog)
    }
  }

  @Test
  fun `test overlapping delete requests remove the working tree once`(): Unit = with(context) {
    setUpWorktree()
    val deletionAttempts = CopyOnWriteArrayList<String>()
    val firstAttemptStarted = CountDownLatch(1)
    val releaseFirstAttempt = CountDownLatch(1)
    // Hold the first `git worktree remove` inside the command, so the second request provably overlaps it.
    recordDeletions(deletionAttempts) {
      firstAttemptStarted.countDown()
      releaseFirstAttempt.await(1, TimeUnit.MINUTES)
    }

    val toDelete = linkedTree()
    val service = GitWorkingTreesService.getInstance(project)
    val first = service.deleteWorkingTrees(project, listOf(toDelete), repo)
    try {
      assertThat(firstAttemptStarted.await(1, TimeUnit.MINUTES))
        .describedAs("The first deletion must reach `git worktree remove`")
        .isTrue()

      val event = actionEvent(listOf(toDelete))
      RemoveWorkingTreeAction().update(event)
      assertThat(event.presentation.isEnabled)
        .describedAs("A working tree whose deletion is in flight must not be removable again")
        .isFalse()

      // The worktrees tab is not modal and refreshes only after an asynchronous reload, so the very same row can be
      // selected and deleted again while the first deletion is still running.
      val second = service.deleteWorkingTrees(project, listOf(toDelete), repo)
      timeoutRunBlocking { second.join() }

      assertThat(deletionAttempts)
        .describedAs("The duplicate request must not run `git worktree remove` a second time")
        .containsExactly(toDelete.path.path)
    }
    finally {
      releaseFirstAttempt.countDown()
    }
    timeoutRunBlocking { first.join() }

    assertThat(deletionAttempts)
      .describedAs("`git worktree remove` must run exactly once per working tree path")
      .containsExactly(toDelete.path.path)
    timeoutRunBlocking {
      waitUntil("the deletion notification is shown") { vcsNotifier.notifications.isNotEmpty() }
    }
    assertSuccessfulNotification(GitBundle.message("Git.WorkingTrees.delete.worktree.success.message", toDelete.path.name))
    assertThat(vcsNotifier.notifications)
      .describedAs("A rejected duplicate request must not add a notification of its own")
      .hasSize(1)
  }

  @Test
  fun `test a working tree queued in a running batch cannot be deleted concurrently`(): Unit = with(context) {
    setUpTwoWorktrees()
    val deletionAttempts = CopyOnWriteArrayList<String>()
    val firstAttemptStarted = CountDownLatch(1)
    val releaseFirstAttempt = CountDownLatch(1)
    // Hold only the batch's first `git worktree remove`, so its second working tree stays queued and unstarted.
    recordDeletions(deletionAttempts) {
      if (deletionAttempts.size == 1) {
        firstAttemptStarted.countDown()
        releaseFirstAttempt.await(1, TimeUnit.MINUTES)
      }
    }

    val toDelete = linkedTrees()
    assertThat(toDelete).describedAs("Both linked working trees must be set up").hasSize(2)
    val queued = toDelete[1]
    val service = GitWorkingTreesService.getInstance(project)
    val batch = service.deleteWorkingTrees(project, toDelete, repo)
    try {
      assertThat(firstAttemptStarted.await(1, TimeUnit.MINUTES))
        .describedAs("The batch must reach `git worktree remove` for its first working tree")
        .isTrue()

      assertThat(service.isWorkingTreeDeletionInProgress(queued))
        .describedAs("A working tree queued in a running batch must be claimed before its turn comes")
        .isTrue()

      val event = actionEvent(listOf(queued))
      RemoveWorkingTreeAction().update(event)
      assertThat(event.presentation.isEnabled)
        .describedAs("A working tree queued in a running batch must not be removable")
        .isFalse()

      val concurrent = service.deleteWorkingTrees(project, listOf(queued), repo)
      timeoutRunBlocking { concurrent.join() }
      assertThat(deletionAttempts)
        .describedAs("A concurrent request must not delete a working tree the running batch has queued")
        .containsExactly(toDelete[0].path.path)
    }
    finally {
      releaseFirstAttempt.countDown()
    }
    timeoutRunBlocking { batch.join() }

    assertThat(deletionAttempts)
      .describedAs("Each working tree of the batch must be deleted exactly once")
      .containsExactly(toDelete[0].path.path, queued.path.path)
  }

  /**
   * Replaces [Git] with a proxy that records the path of every `git worktree remove` and runs [beforeDeletion] before
   * delegating, so a test can hold one deletion open while it issues a second request.
   */
  private fun recordDeletions(deletionAttempts: MutableList<String>, beforeDeletion: () -> Unit = {}) {
    val realGit = Git.getInstance()
    val recordingGit = Proxy.newProxyInstance(Git::class.java.classLoader, arrayOf(Git::class.java)) { _, method, arguments ->
      if (method.name == "deleteWorkingTree") {
        deletionAttempts.add((arguments!![1] as GitWorkingTree).path.path)
        beforeDeletion()
      }
      try {
        method.invoke(realGit, *(arguments ?: emptyArray()))
      }
      catch (e: InvocationTargetException) {
        throw e.targetException
      }
    } as Git
    ApplicationManager.getApplication().replaceService(Git::class.java, recordingGit, testDisposable)
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
