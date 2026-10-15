// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.ide.RecentProjectsManagerBase
import com.intellij.ide.impl.ProjectUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ex.ProjectManagerEx
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialog
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.platform.eel.provider.asNioPath
import com.intellij.platform.testFramework.junit5.eel.params.api.DockerTest
import com.intellij.platform.testFramework.junit5.eel.params.api.EelHolder
import com.intellij.platform.testFramework.junit5.eel.params.api.EelType
import com.intellij.platform.testFramework.junit5.eel.params.api.TestApplicationWithEel
import com.intellij.platform.testFramework.junit5.eel.params.api.WslTest
import com.intellij.testFramework.LoggedErrorProcessor
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitLocalBranch
import git4idea.GitNotificationIdsHolder
import git4idea.GitOperationsCollector
import git4idea.commands.GitBranchAlreadyCheckedOutInOtherWorktreeDetector
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.test.GitSingleRepoContext
import git4idea.test.branch
import git4idea.test.gitSingleRepoContextFixture
import git4idea.workingTrees.dialog.GitWorktreeCreationRequest
import git4idea.workingTrees.dialog.WorktreeBranchSpec
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS
import org.junit.jupiter.params.ParameterizedClass
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.mockito.ArgumentMatchers
import org.mockito.Mockito
import kotlin.io.path.Path
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class GitCreateWorkingTreeServiceTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @AfterEach
  fun afterEach() {
    TestDialogManager.setTestDialog(TestDialog.DEFAULT)
  }

  @Test
  fun `test pre-check dialog message includes worktree path and proceeding sets force`(): Unit = with(context) {
    val branch = createBranch(repo, "feature")
    var shownMessage: String? = null
    TestDialogManager.setTestDialog { message ->
      shownMessage = message
      Messages.YES
    }

    val proceed = runBlocking {
      GitCreateWorkingTreeService.getInstance().confirmCreateNewWorktreeInsteadOfOpening(project, branch, "/other/worktree/path")
    }

    assertThat(proceed).isTrue()
    assertThat(shownMessage).contains("/other/worktree/path")
  }

  @Test
  fun `test pre-check dialog declining proceed opens existing worktree instead`(): Unit = with(context) {
    val branch = createBranch(repo, "feature")
    TestDialogManager.setTestDialog(TestDialog.NO)

    val proceed = runBlocking {
      GitCreateWorkingTreeService.getInstance().confirmCreateNewWorktreeInsteadOfOpening(project, branch, null)
    }

    assertThat(proceed).isFalse()
  }

  @Test
  fun `test post-failure retry dialog message includes worktree path and proceeding retries`(): Unit = with(context) {
    val match = GitBranchAlreadyCheckedOutInOtherWorktreeDetector.matchInOutput(
      listOf("fatal: 'feature' is already used by worktree at '/other/worktree/path'"))!!
    var shownMessage: String? = null
    TestDialogManager.setTestDialog { message ->
      shownMessage = message
      Messages.YES
    }

    val retry = runBlocking {
      GitCreateWorkingTreeService.getInstance().confirmCreateWorktreeIgnoringOtherWorktree(project, match.branchName, match.worktreePath)
    }

    assertThat(retry).isTrue()
    assertThat(shownMessage).contains("/other/worktree/path")
  }

  @Test
  fun `test post-failure retry dialog cancelled does not retry`(): Unit = with(context) {
    val match = GitBranchAlreadyCheckedOutInOtherWorktreeDetector.matchInOutput(
      listOf("fatal: 'feature' is already used by worktree at '/other/worktree/path'"))!!
    TestDialogManager.setTestDialog(TestDialog.NO)

    val retry = runBlocking {
      GitCreateWorkingTreeService.getInstance().confirmCreateWorktreeIgnoringOtherWorktree(project, match.branchName, match.worktreePath)
    }

    assertThat(retry).isFalse()
  }

  @Test
  fun `test cancelling waitForReturnToDialogAction clears the pending notification state without hanging`(): Unit = with(context) {
    touch("notes.txt", "content")
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repo.root.toNioPath().resolve("notes.txt"))!!
    val session = GitWorktreeIncludeEditingSessionService.getInstance(project)

    timeoutRunBlocking(timeout = 10.seconds) {
      val job = launch { GitCreateWorkingTreeService.getInstance().waitForReturnToDialogAction(project, file) }
      delay(500.milliseconds)
      assertThat(session.hasPendingReturn(file)).isTrue()
      job.cancel()
      job.join()
    }
    assertThat(session.hasPendingReturn(file)).isFalse()
  }

  @Test
  fun `test returnToDialog resumes a waiting waitForReturnToDialogAction with return to dialog result`(): Unit = with(context) {
    touch("notes.txt", "content")
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repo.root.toNioPath().resolve("notes.txt"))!!
    val session = GitWorktreeIncludeEditingSessionService.getInstance(project)

    val resultDeferred = CompletableDeferred<WorktreeIncludeEditResult>()
    timeoutRunBlocking(timeout = 10.seconds) {
      val job = launch {
        val result = GitCreateWorkingTreeService.getInstance().waitForReturnToDialogAction(project, file)
        resultDeferred.complete(result)
      }
      delay(500.milliseconds)
      session.returnToDialog(file)
      job.join()
    }
    assertThat(session.hasPendingReturn(file)).isFalse()
    assertThat(runBlocking { resultDeferred.await() }).isEqualTo(WorktreeIncludeEditResult.RETURN_TO_DIALOG)
  }

  @Test
  fun `test closing editor tab resumes waitForReturnToDialogAction with aborted result and clears session`(): Unit = with(context) {
    touch("notes.txt", "content")
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(repo.root.toNioPath().resolve("notes.txt"))!!
    val session = GitWorktreeIncludeEditingSessionService.getInstance(project)

    val resultDeferred = CompletableDeferred<WorktreeIncludeEditResult>()
    timeoutRunBlocking(timeout = 10.seconds) {
      val job = launch {
        val result = GitCreateWorkingTreeService.getInstance().waitForReturnToDialogAction(project, file)
        resultDeferred.complete(result)
      }
      delay(500.milliseconds)
      assertThat(session.hasPendingReturn(file)).isTrue()
      val editorManager = FileEditorManager.getInstance(project)
      project.messageBus.syncPublisher(FileEditorManagerListener.FILE_EDITOR_MANAGER).fileClosed(editorManager, file)
      job.join()
    }
    assertThat(session.hasPendingReturn(file)).isFalse()
    assertThat(runBlocking { resultDeferred.await() }).isEqualTo(WorktreeIncludeEditResult.ABORTED)
  }

  @Test
  @RegistryKey("git.enable.working.trees.feature", "true")
  fun `test config copy failure still opens the worktree and posts an error notification`(@TestDisposable disposable: Disposable): Unit = with(context) {
    val branch = createBranch(repo, "feature")
    val configService = Mockito.mock(GitWorktreeProjectConfigService::class.java)
    runBlocking {
      Mockito.`when`(configService.copyAndCleanUpWorktreeIncludeFiles(anyNonNull(), anyNonNull())).thenThrow(RuntimeException("config copy failed"))
    }
    project.replaceService(GitWorktreeProjectConfigService::class.java, configService, disposable)

    val parentDir = testNioRoot.resolve("worktree-parent")
    val worktreeDir = parentDir.resolve("featureWorktree")
    val openedProjectDeferred = CompletableDeferred<Project>()
    val ideActivity = GitOperationsCollector.logCreateWorktreeActionInvoked(project, "Test", branch)
    val request = GitWorktreeCreationRequest(
      repo, VcsUtil.getFilePath(worktreeDir, true), WorktreeBranchSpec.CheckoutExisting(branch),
      copyWorktreeIncludeMatches = true,
    )

    LoggedErrorProcessor.executeWith<RuntimeException>(object : LoggedErrorProcessor() {
      override fun processError(category: String, message: String, details: Array<String>, t: Throwable?): Set<Action> = setOf(Action.LOG)
    }) {
      timeoutRunBlocking(timeout = 120.seconds) {
        GitCreateWorkingTreeService.getInstance().doCreateWorkingTree(
          ideActivity, request,
          onProjectOpened = { openedProjectDeferred.complete(it) },
        )

        val openedProject = openedProjectDeferred.await()
        try {
          assertThat(worktreeDir).exists()
          assertThat(vcsNotifier.notifications.map { it.displayId })
            .contains(GitNotificationIdsHolder.WORKTREE_CONFIG_COPY_FAILED)
        }
        finally {
          // Mockito records every mock invocation, including this one with the real project's
          // VirtualFile and Path, in a ThreadLocal that outlives the test; clear it here.
          Mockito.reset(configService)
          ProjectManagerEx.getInstanceEx().forceCloseProjectAsync(openedProject)
          // The open path's StartupManager.runAfterOpened schedules an invokeLater that can still hold
          // openedProject when the leak check runs; flush the EDT queue to release it.
          withContext(Dispatchers.EDT) {
            PlatformTestUtil.dispatchAllInvocationEventsInIdeEventQueue()
          }
        }
      }
    }
  }

  @ParameterizedTest
  @ValueSource(booleans = [false, true])
  @RegistryKey("git.enable.working.trees.feature", "true")
  fun `test creation without opening registers the worktree and reports its branch`(createNewBranch: Boolean): Unit = with(context) {
    val sourceBranch = createBranch(repo, "source")
    val branchName = if (createNewBranch) "feature" else "source"
    val branch = if (createNewBranch) WorktreeBranchSpec.CreateNewBranch(sourceBranch, branchName)
    else WorktreeBranchSpec.CheckoutExisting(sourceBranch)
    val worktreeDir = testNioRoot.resolve("featureWorktree")
    val request = GitWorktreeCreationRequest(repo, VcsUtil.getFilePath(worktreeDir, true), branch)
    val ideActivity = GitOperationsCollector.logCreateWorktreeActionInvoked(project, "Test", sourceBranch)
    val recentProjectsManager = RecentProjectsManagerBase.getInstanceEx()
    val openedProject = CompletableDeferred<Project>()

    try {
      timeoutRunBlocking(timeout = 120.seconds) {
        GitCreateWorkingTreeService.getInstance().doCreateWorkingTree(
          ideActivity, request,
          onProjectOpened = { openedProject.complete(it) },
          openAfterCreation = false,
        )
      }

      assertThat(worktreeDir).exists()
      assertThat(openedProject.isCompleted).isFalse()
      assertThat(recentProjectsManager.getRecentPaths()).contains(worktreeDir.invariantSeparatorsPathString)
      assertThat(recentProjectsManager.getProjectMetaInfo(worktreeDir)?.opened).isEqualTo(false)
      val notification = vcsNotifier.notifications.single { it.displayId == GitNotificationIdsHolder.WORKTREE_CREATED }
      assertThat(notification.content)
        .isEqualTo(GitBundle.message("notification.content.worktree.created", branchName, worktreeDir.toString()))
    }
    finally {
      recentProjectsManager.removePath(worktreeDir.invariantSeparatorsPathString)
    }
  }

  private fun <T> anyNonNull(): T {
    ArgumentMatchers.any<T>()
    @Suppress("UNCHECKED_CAST")
    return null as T
  }

  private fun createBranch(repo: GitRepository, branchName: String): GitLocalBranch {
    repo.branch(branchName)
    repo.update()
    val newBranch = repo.branches.findLocalBranch(branchName)
    assertThat(newBranch).describedAs("Branch $branchName was not created").isNotNull()
    return newBranch!!
  }
}

@ParameterizedClass
@TestApplicationWithEel(osesMayNotHaveRemoteEels = [OS.WINDOWS, OS.LINUX, OS.MAC])
@WslTest(mandatory = false)
@DockerTest(image = "alpine/git", mandatory = false)
internal class GitCreateWorkingTreeServiceEelTest(private val eelHolder: EelHolder) {

  @Test
  fun `test getDefaultParentDir returns the IDE default project directory on the local machine and the environment's home directory otherwise`() {
    val result = GitCreateWorkingTreeService.getDefaultParentDir(eelHolder.eel)

    if (eelHolder.type == EelType.Local) {
      assertThat(result).isEqualTo(Path(ProjectUtil.getBaseDir()))
    } else {
      assertThat(result).isEqualTo(eelHolder.eel.userInfo.home.asNioPath())
    }
  }
}
