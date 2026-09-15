// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.testFramework.ExtensionTestUtil
import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import git4idea.GitStandardLocalBranch
import git4idea.repo.GitRepository
import git4idea.repo.getAndInit
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.gitPlatformContextFixture
import git4idea.ui.branch.GitBranchReviewPresenter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.time.Duration.Companion.seconds

/**
 * End-to-end coverage of the worktree PR/MR icon pipeline: a [GitBranchReviewPresenter] extension's flow feeds
 * [GitWorktreesViewModel.reviews], keyed by [GitWorktreeRow.resolveReviewBranchKey]. Per-host lookup
 * (GitHub, GitLab, Azure DevOps) is out of scope here - only the shared wiring that every host goes through.
 */
@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorktreesViewModelTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  private fun registerReviewPresenter(presenter: GitBranchReviewPresenter) {
    ExtensionTestUtil.maskExtensions(REVIEW_PRESENTER_EP, listOf(presenter), disposable)
  }

  @Test
  fun `test a branch with no registered review presenter maps to a null review`(): Unit = with(context) {
    createRepository(project, projectNioRoot, true)
    GitRepositoriesHolder.getAndInit(project)

    withViewModel { viewModel ->
      val key = awaitSingleRowKey(viewModel)
      waitUntil("the row's key gets a review entry", timeout = 10.seconds) { viewModel.reviews.value.containsKey(key) }
      assertThat(viewModel.reviews.value[key]).isNull()
    }
  }

  @Test
  fun `test a registered review presenter's review reaches the worktree row's key`(): Unit = with(context) {
    createRepository(project, projectNioRoot, true)
    GitRepositoriesHolder.getAndInit(project)

    var opened = false
    val review = fakeReview("Add feature") { opened = true }
    registerReviewPresenter(fakeReviewPresenter { _, branches -> flowOf(branches.associateWith { review }) })

    withViewModel { viewModel ->
      val key = awaitSingleRowKey(viewModel)
      waitUntil("the presenter's review reaches the map", timeout = 10.seconds) {
        viewModel.reviews.value[key] == review
      }

      // The map is what the renderer and the "open review" action both read - clicking it must run the host's callback.
      viewModel.reviews.value.getValue(key)!!.open()
      assertThat(opened).describedAs("open() on the map's review must run the registered presenter's callback").isTrue()
    }
  }

  @Test
  fun `test one repository's review appears before another repository's slower lookup resolves`(): Unit = with(context) {
    val fastRepo = createRepository(project, projectNioRoot, true)
    val slowRepo = createRepository(project, testNioRoot.resolve("slow-repo"), true)
    GitRepositoriesHolder.getAndInit(project)

    val fastReview = fakeReview("Add feature")
    val neverEmits = MutableSharedFlow<Map<GitStandardLocalBranch, GitBranchReviewPresenter.Review?>>()
    registerReviewPresenter(fakeReviewPresenter { repository, branches ->
      if (repository.root.path == fastRepo.root.path) flowOf(branches.associateWith { fastReview }) else neverEmits
    })

    withViewModel { viewModel ->
      waitUntil("both worktree rows are built", timeout = 10.seconds) {
        viewModel.entries.value.filterIsInstance<GitWorktreeRow>().size == 2
      }
      val rows = viewModel.entries.value.filterIsInstance<GitWorktreeRow>()
      val fastKey = rows.single { it.repository.root.path == fastRepo.root.path }.resolveReviewBranchKey()!!
      val slowKey = rows.single { it.repository.root.path == slowRepo.root.path }.resolveReviewBranchKey()!!

      waitUntil("the fast repository's review resolves", timeout = 10.seconds) {
        viewModel.reviews.value[fastKey] == fastReview
      }
      assertThat(viewModel.reviews.value)
        .describedAs("The slow repository's row must still show a (null) entry, not be missing, while its lookup is pending")
        .containsEntry(slowKey, null)
    }
  }

  @Test
  fun `test a throwing lookup for one branch does not kill reviews for a healthy branch`(): Unit = with(context) {
    val healthyRepo = createRepository(project, projectNioRoot, true)
    val throwingRepo = createRepository(project, testNioRoot.resolve("throwing-repo"), true)
    GitRepositoriesHolder.getAndInit(project)

    val healthyReview = fakeReview("Add feature")
    registerReviewPresenter(fakeReviewPresenter { repository, branches ->
      if (repository.root.path == throwingRepo.root.path) flow { throw IllegalStateException("Simulated host failure") }
      else flowOf(branches.associateWith { healthyReview })
    })

    withViewModel { viewModel ->
      waitUntil("both worktree rows are built", timeout = 10.seconds) {
        viewModel.entries.value.filterIsInstance<GitWorktreeRow>().size == 2
      }
      val rows = viewModel.entries.value.filterIsInstance<GitWorktreeRow>()
      val healthyKey = rows.single { it.repository.root.path == healthyRepo.root.path }.resolveReviewBranchKey()!!
      val throwingKey = rows.single { it.repository.root.path == throwingRepo.root.path }.resolveReviewBranchKey()!!

      waitUntil("the healthy repository's review resolves", timeout = 10.seconds) {
        viewModel.reviews.value[healthyKey] == healthyReview
      }
      waitUntil("the throwing repository's entry settles instead of hanging or being missing", timeout = 10.seconds) {
        viewModel.reviews.value.containsKey(throwingKey)
      }
      assertThat(viewModel.reviews.value[throwingKey])
        .describedAs("A branch whose lookup throws must present as null, not crash the whole pipeline")
        .isNull()
      assertThat(viewModel.reviews.value[healthyKey])
        .describedAs("The failure on the other branch must not have killed the shared collector")
        .isEqualTo(healthyReview)
    }
  }

  private suspend fun awaitSingleRowKey(viewModel: GitWorktreesViewModel): GitWorktreeBranchKey {
    waitUntil("the worktree row is built", timeout = 10.seconds) { viewModel.entries.value.filterIsInstance<GitWorktreeRow>().isNotEmpty() }
    return viewModel.entries.value.filterIsInstance<GitWorktreeRow>().single().resolveReviewBranchKey()!!
  }

  private fun withViewModel(block: suspend (GitWorktreesViewModel) -> Unit): Unit = runBlocking {
    val cs = CoroutineScope(Dispatchers.Default + SupervisorJob())
    try {
      block(GitWorktreesViewModel(context.project, cs))
    }
    finally {
      cs.cancel()
    }
  }
}

private val REVIEW_PRESENTER_EP = ExtensionPointName<GitBranchReviewPresenter>("Git4Idea.gitBranchReviewPresenter")

private fun fakeReviewPresenter(
  lookup: (GitRepository, Set<GitStandardLocalBranch>) -> Flow<Map<GitStandardLocalBranch, GitBranchReviewPresenter.Review?>>,
): GitBranchReviewPresenter = object : GitBranchReviewPresenter {
  override fun getReviewsFlow(repository: GitRepository, branches: Set<GitStandardLocalBranch>) = lookup(repository, branches)
}

private fun fakeReview(title: String, onOpen: () -> Unit = {}): GitBranchReviewPresenter.Review =
  GitBranchReviewPresenter.Review(title, onOpen)
