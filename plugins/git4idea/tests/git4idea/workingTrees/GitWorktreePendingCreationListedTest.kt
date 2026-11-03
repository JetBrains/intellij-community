// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.Disposable
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.replaceService
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.repo.GitRepositoryState
import com.intellij.vcsUtil.VcsUtil
import git4idea.GitLocalBranch
import git4idea.GitWorkingTree
import git4idea.repo.GitWorkingTreeHolder
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitSingleRepoContext
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.gitSingleRepoFixture
import git4idea.workingTrees.dialog.GitWorktreeCreationRequest
import git4idea.workingTrees.dialog.WorktreeBranchSpec
import git4idea.workingTrees.ui.GitWorktreeCreatingRow
import git4idea.workingTrees.ui.GitWorktreeRow
import git4idea.workingTrees.ui.GitWorktreesUiUtil
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.yield
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.time.Duration.Companion.seconds

/**
 * `git worktree add` lists the new worktree before the creation ends, so the Worktrees tab model can contain a worktree
 * that is still pending. The tab must show one row for it, not a [GitWorktreeRow] and a [GitWorktreeCreatingRow].
 * When the service removes the pending creation, the tab must show the unlocked [GitWorktreeRow].
 */
@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorktreePendingCreationListedTest {
  private val contextFixture = gitPlatformContextFixture().gitSingleRepoFixture(makeInitialCommit = true)
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test an unrelated update does not complete the wait for a fresh worktree`(
    @TestDisposable disposable: Disposable,
  ): Unit = with(context) {
    timeoutRunBlocking {
      val targetPath = VcsUtil.getFilePath(projectNioRoot.resolve("new-worktree"), true)
      val lockedTree = GitWorkingTree(targetPath, currentBranch = null, isMain = false, isCurrent = false, isLocked = true)
      val state = mock<GitRepositoryState>()
      `when`(state.workingTrees).thenReturn(listOf(lockedTree))
      val model = mock<GitRepositoryModel>()
      `when`(model.state).thenReturn(state)

      val updates = MutableSharedFlow<GitRepositoriesHolder.UpdateType>()
      val holder = mock<GitRepositoriesHolder>()
      `when`(holder.initialized).thenReturn(true)
      `when`(holder.updates).thenReturn(updates)
      `when`(holder.get(repo.repositoryId())).thenReturn(model)
      project.replaceService(GitRepositoriesHolder::class.java, holder, disposable)

      val workingTreeHolder = mock<GitWorkingTreeHolder>()
      val repository = spy(repo)
      doReturn(workingTreeHolder).`when`(repository).workingTreeHolder
      val service = GitCreateWorkingTreeService(this)
      val waiting = async(start = CoroutineStart.UNDISPATCHED) {
        service.awaitWorkingTreeListed(repository, targetPath)
      }
      try {
        verify(workingTreeHolder).scheduleReload()
        assertThat(waiting.isActive).isTrue()

        updates.emit(GitRepositoriesHolder.UpdateType.TAGS_LOADED)
        yield()

        assertThat(waiting.isActive)
          .describedAs("A tag update must not complete the wait while the cached worktree is still locked")
          .isTrue()

        updates.emit(GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED)
        yield()

        assertThat(waiting.isActive)
          .describedAs("A worktree update must not complete the wait while the target worktree is still locked")
          .isTrue()

        `when`(state.workingTrees).thenReturn(listOf(lockedTree.copy(isLocked = false)))
        updates.emit(GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED)
        waiting.await()
      }
      finally {
        waiting.cancelAndJoin()
      }
    }
  }

  @Test
  fun `test a pending worktree that the model lists shows its creating row until the service removes it`(): Unit = with(context) {
    val treeRoot = "treeRoot"
    val holder = GitRepositoriesHolder.getAndInit(project)
    holder.expectEvent({
                         git("worktree add -B tree ../$treeRoot")
                         repo.workingTreeHolder.scheduleReload()
                       }, { event, _ -> event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED })
    val targetPath = holder.get(repo.repositoryId())!!.state.workingTrees.first { it.path.path.endsWith(treeRoot) }.path
    val request = GitWorktreeCreationRequest(repo, targetPath, WorktreeBranchSpec.CheckoutExisting(GitLocalBranch("tree")))
    val service = GitCreateWorkingTreeService.getInstance()

    service.addPendingCreation(request)
    val entries = GitWorktreesUiUtil.buildEntries(project, service.pendingCreations.value)

    assertThat(entries.filterIsInstance<GitWorktreeCreatingRow>().map { it.targetPath }).containsExactly(targetPath)
    assertThat(entries.filterIsInstance<GitWorktreeRow>().map { it.gitWorkingTree.path }).isNotEmpty().doesNotContain(targetPath)

    service.removePendingCreationOnceListed(request)
    // Shorter than the service timeout, so the test fails if only the timeout removes the pending creation.
    timeoutRunBlocking(5.seconds) {
      service.pendingCreations.first { targetPath !in it }
    }
    val finalEntries = GitWorktreesUiUtil.buildEntries(project, service.pendingCreations.value)

    assertThat(finalEntries.filterIsInstance<GitWorktreeCreatingRow>().map { it.targetPath }).doesNotContain(targetPath)
    val worktreeRow = finalEntries.filterIsInstance<GitWorktreeRow>().single { it.gitWorkingTree.path == targetPath }
    assertThat(worktreeRow.gitWorkingTree.isLocked).isFalse()
  }
}
