// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.ide.RecentProjectMetaInfo
import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.createTestOpenProjectOptions
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.disposableFixture
import com.intellij.util.application
import com.intellij.vcs.test.assertSuccessfulNotification
import com.intellij.vcs.test.refresh
import git4idea.GitNotificationIdsHolder
import git4idea.GitWorkingTree
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepositoryManager
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.initRepo
import git4idea.test.registerRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Path
import java.util.concurrent.CopyOnWriteArrayList

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitDeleteCurrentProjectWorktreeTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()
  private val disposableFixture = disposableFixture()

  @Test
  fun `test current worktree is deleted through the open main project`(): Unit = with(context) {
    val worktreeRoot = testNioRoot.resolve("treeRoot")
    git("worktree add -B feature $worktreeRoot")
    repo.ensureWorkingTreesUpToDateForTests()
    refresh()

    withWorktreeProject(worktreeRoot) { worktreeProject ->
      val currentWorktree = currentWorktreeOf(worktreeProject)
      GitWorkingTreesService.getInstance(worktreeProject).deleteCurrentProjectWorktree()

      timeoutRunBlocking {
        waitUntil("the main project deletes the current worktree") {
          worktreeProject.isDisposed &&
          vcsNotifier.notifications.isNotEmpty() &&
          repo.workingTreeHolder.getWorkingTrees().none { !it.isMain }
        }
      }

      assertThat(worktreeRoot).describedAs("The current worktree must be gone from disk").doesNotExist()
      assertSuccessfulNotification(GitBundle.message("Git.WorkingTrees.delete.worktree.success.message", currentWorktree.path.name))
    }
  }

  @Test
  fun `test current worktree is deleted when no open project owns the main worktree`(): Unit = with(context) {
    val mainRoot = testNioRoot.resolve("standalone")
    val worktreeRoot = testNioRoot.resolve("standaloneTree")
    createStandaloneRepoWithWorktree(mainRoot, worktreeRoot)

    withRecentPaths(worktreeRoot) { worktreeRecentPaths, siblingRecentPath ->
      withAppNotifications { notifications ->
        withWorktreeProject(worktreeRoot) { worktreeProject ->
          val currentWorktree = currentWorktreeOf(worktreeProject)
          GitWorkingTreesService.getInstance(worktreeProject).deleteCurrentProjectWorktree()

          timeoutRunBlocking {
            waitUntil("the current worktree is deleted without a project") {
              worktreeProject.isDisposed && notifications.isNotEmpty()
            }
          }

          assertThat(worktreeRoot).describedAs("The current worktree must be gone from disk").doesNotExist()
          cd(mainRoot)
          assertThat(git(project, "worktree list --porcelain").lines().filter { it.startsWith("worktree ") })
            .describedAs("Only the main worktree must stay registered in the repository")
            .hasSize(1)
          assertThat(RecentProjectsManagerBase.getInstanceEx().getRecentPaths())
            .describedAs("The deleted worktree must be removed from the recent projects")
            .doesNotContainAnyElementsOf(worktreeRecentPaths)
            .contains(siblingRecentPath)

          val notification = notifications.single()
          assertThat(notification.displayId).isEqualTo(GitNotificationIdsHolder.WORKING_TREE_DELETED)
          assertThat(notification.type).isEqualTo(NotificationType.INFORMATION)
          assertThat(notification.content)
            .isEqualTo(GitBundle.message("Git.WorkingTrees.delete.worktree.success.message", currentWorktree.path.name))
        }
      }
    }
  }

  @Test
  fun `test failure is reported when no open project owns the main worktree`(): Unit = with(context) {
    val mainRoot = testNioRoot.resolve("standalone")
    val worktreeRoot = testNioRoot.resolve("standaloneTree")
    createStandaloneRepoWithWorktree(mainRoot, worktreeRoot)
    // A single `--force` does not remove a locked worktree, so the deletion fails.
    cd(mainRoot)
    git(project, "worktree lock $worktreeRoot")

    withRecentPaths(worktreeRoot) { worktreeRecentPaths, _ ->
      withAppNotifications { notifications ->
        withWorktreeProject(worktreeRoot) { worktreeProject ->
          GitWorkingTreesService.getInstance(worktreeProject).deleteCurrentProjectWorktree()

          timeoutRunBlocking {
            waitUntil("the failed deletion is reported") {
              worktreeProject.isDisposed && notifications.isNotEmpty()
            }
          }

          assertThat(worktreeRoot).describedAs("The locked worktree must stay on disk").exists()
          assertThat(RecentProjectsManagerBase.getInstanceEx().getRecentPaths())
            .describedAs("A worktree that is not deleted must stay in the recent projects")
            .containsAll(worktreeRecentPaths)

          val notification = notifications.single()
          assertThat(notification.displayId).isEqualTo(GitNotificationIdsHolder.WORKING_TREE_COULD_NOT_DELETE)
          assertThat(notification.type).isEqualTo(NotificationType.ERROR)
          assertThat(notification.title).isEqualTo(GitBundle.message("Git.WorkingTrees.delete.worktrees.failure.notification.title"))
        }
      }
    }
  }

  /** Makes a repository in [mainRoot], which is not a project, with a linked worktree in [worktreeRoot]. */
  private fun GitSingleRepoContext.createStandaloneRepoWithWorktree(mainRoot: Path, worktreeRoot: Path) {
    initRepo(project, mainRoot, makeInitialCommit = true)
    git(project, "worktree add -B feature $worktreeRoot")
  }

  private fun currentWorktreeOf(worktreeProject: Project): GitWorkingTree {
    val service = GitWorkingTreesService.getInstance(worktreeProject)
    assertThat(service.isCurrentProjectLinkedWorktree())
      .describedAs("The worktree project must detect that it is a linked worktree")
      .isTrue()
    return GitRepositoryManager.getInstance(worktreeProject).repositories.single()
      .workingTreeHolder.getWorkingTrees().single { it.isCurrent }
  }

  /**
   * Opens [worktreeRoot] as a separate project with the worktree repository registered, then runs [action] on it.
   * The code under test closes the project, so the project is closed here only when [action] leaves it open.
   */
  private fun withWorktreeProject(worktreeRoot: Path, action: (Project) -> Unit) {
    val projectManager = ProjectManagerEx.getInstanceEx()
    val worktreeProject = timeoutRunBlocking {
      projectManager.openProjectAsync(worktreeRoot, createTestOpenProjectOptions())!!
    }
    try {
      registerRepo(worktreeProject, worktreeRoot).ensureWorkingTreesUpToDateForTests()
      action(worktreeProject)
    }
    finally {
      if (!worktreeProject.isDisposed) {
        timeoutRunBlocking { projectManager.forceCloseProjectAsync(worktreeProject) }
      }
    }
  }

  /** Collects the notifications that are sent without a project, which the project-level test notifier does not see. */
  private fun withAppNotifications(action: (List<Notification>) -> Unit) {
    val notifications = CopyOnWriteArrayList<Notification>()
    application.messageBus.connect(disposableFixture.get()).subscribe(Notifications.TOPIC, object : Notifications {
      override fun notify(notification: Notification) {
        if (notification.displayId == GitNotificationIdsHolder.WORKING_TREE_DELETED ||
            notification.displayId == GitNotificationIdsHolder.WORKING_TREE_COULD_NOT_DELETE) {
          notifications.add(notification)
        }
      }
    })
    action(notifications)
  }

  /** Adds [worktreeRoot], a path inside it, and a sibling path to the recent projects for the time of [action]. */
  private fun withRecentPaths(worktreeRoot: Path, action: (worktreeRecentPaths: List<String>, siblingRecentPath: String) -> Unit) {
    val recentProjectsManager = RecentProjectsManagerBase.getInstanceEx()
    val worktreeRecentPaths = listOf(worktreeRoot.toString(), worktreeRoot.resolve("MODULE.bazel").toString())
    val siblingRecentPath = "$worktreeRoot-other/MODULE.bazel"
    val recentPaths = worktreeRecentPaths + siblingRecentPath
    try {
      recentPaths.forEach { recentProjectsManager.addRecentPath(it, RecentProjectMetaInfo()) }
      action(worktreeRecentPaths, siblingRecentPath)
    }
    finally {
      recentPaths.forEach(recentProjectsManager::removePath)
    }
  }
}
