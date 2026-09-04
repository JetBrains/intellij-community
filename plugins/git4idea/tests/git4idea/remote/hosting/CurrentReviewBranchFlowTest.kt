// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.testFramework.common.waitUntil
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitRemoteBranch
import git4idea.GitStandardLocalBranch
import git4idea.push.GitPushSingleRepoContext
import git4idea.push.gitPushSingleRepoFixture
import git4idea.push.updateRepositories
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.test.git
import git4idea.test.makeCommit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mockito.mock
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

@TestApplication
internal class CurrentReviewBranchFlowTest {

  private val fixture = gitPushSingleRepoFixture()
  private val context: GitPushSingleRepoContext get() = fixture.get()

  /** A single hosted mapping for the "origin" remote of the test repository. */
  private fun hostedRepositoriesManager(): HostedGitRepositoriesManager<HostedGitRepositoryMapping> = with(context) {
    val origin = repository.remotes.single { it.name == "origin" }
    val mapping = object : HostedGitRepositoryMapping {
      override val repository: HostedRepositoryCoordinates = mock(HostedRepositoryCoordinates::class.java)
      override val remote: GitRemoteUrlCoordinates =
        GitRemoteUrlCoordinates(origin.firstUrl!!, origin, this@with.repository)
    }
    return object : HostedGitRepositoriesManager<HostedGitRepositoryMapping> {
      override val knownRepositoriesState = MutableStateFlow(setOf(mapping))
    }
  }

  @Test
  fun `test re-emits the review branch when the current branch tip moves on push`(): Unit = with(context) {
    val manager = hostedRepositoriesManager()

    runBlocking {
      val emissions = CopyOnWriteArrayList<Pair<HostedGitRepositoryMapping, GitRemoteBranch>?>()
      val job = launch(Dispatchers.Default) {
        manager.findHostedRemoteBranchTrackedByCurrent(repository).collect { emissions.add(it) }
      }
      try {
        // Initial resolution: the current branch (master) tracks origin/master.
        waitUntil("initial review branch is resolved", timeout = 10.seconds) { emissions.isNotEmpty() }
        assertThat(emissions.last()?.second?.nameForRemoteOperations).isEqualTo("master")
        val countBeforePush = emissions.size

        // A push advances origin/master's tip while the tracked branch identity stays the same.
        makeCommit("file.txt")
        repository.git("push origin master")
        updateRepositories()

        // The moved tip must produce a fresh emission (this is what the fix restores).
        waitUntil("review branch re-emitted after push", timeout = 10.seconds) { emissions.size > countBeforePush }
        assertThat(emissions.last()?.second?.nameForRemoteOperations).isEqualTo("master")
      }
      finally {
        job.cancel()
      }
    }
  }

  @Test
  fun `test findFirstRemoteBranchTrackedBy matches a GitStandardLocalBranch against a GitLocalBranch's track info`(): Unit = with(context) {
    // "feature" is created but never checked out, so it stays a GitLocalBranch only in branchTrackInfos.
    repository.git("branch --track feature origin/master")
    updateRepositories()

    // A worktree row only ever knows its branch as a GitStandardLocalBranch, not the more specific GitLocalBranch.
    val branch = GitStandardLocalBranch("feature")

    assertThat(repository.info.findFirstRemoteBranchTrackedBy(branch)?.nameForRemoteOperations).isEqualTo("master")
  }

  @Test
  fun `test resolves the review branch for an arbitrary non-current local branch`(): Unit = with(context) {
    val manager = hostedRepositoriesManager()

    repository.git("branch --track feature origin/master")
    updateRepositories()

    val branch = GitStandardLocalBranch("feature")

    runBlocking {
      val emissions = CopyOnWriteArrayList<Pair<HostedGitRepositoryMapping, GitRemoteBranch>?>()
      val job = launch(Dispatchers.Default) {
        manager.findHostedRemoteBranchTrackedBy(repository, branch).collect { emissions.add(it) }
      }
      try {
        waitUntil("review branch is resolved for the non-current branch", timeout = 10.seconds) { emissions.isNotEmpty() }
        assertThat(emissions.last()?.second?.nameForRemoteOperations).isEqualTo("master")
      }
      finally {
        job.cancel()
      }
    }
  }
}
