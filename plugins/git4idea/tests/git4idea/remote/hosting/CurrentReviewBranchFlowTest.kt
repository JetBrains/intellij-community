// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remote.hosting

import com.intellij.openapi.vcs.Executor.cd
import com.intellij.testFramework.common.waitUntil
import git4idea.GitRemoteBranch
import git4idea.push.GitPushOperationBaseTest
import git4idea.remote.GitRemoteUrlCoordinates
import git4idea.repo.GitRepository
import git4idea.test.git
import git4idea.test.makeCommit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.time.Duration.Companion.seconds

class CurrentReviewBranchFlowTest : GitPushOperationBaseTest() {

  private lateinit var repository: GitRepository
  private lateinit var manager: HostedGitRepositoriesManager<HostedGitRepositoryMapping>

  override fun setUp() {
    super.setUp()

    val trinity = setupRepositories(projectPath, "parent", "bro")
    repository = trinity.projectRepo
    cd(projectPath)
    refresh()
    updateRepositories()

    // A single hosted mapping for the "origin" remote of the test repository.
    val origin = repository.remotes.single { it.name == "origin" }
    val mapping = object : HostedGitRepositoryMapping {
      override val repository: HostedRepositoryCoordinates = mock(HostedRepositoryCoordinates::class.java)
      override val remote: GitRemoteUrlCoordinates = GitRemoteUrlCoordinates(origin.firstUrl!!, origin, this@CurrentReviewBranchFlowTest.repository)
    }
    manager = object : HostedGitRepositoriesManager<HostedGitRepositoryMapping> {
      override val knownRepositoriesState = MutableStateFlow(setOf(mapping))
    }
  }

  fun `test re-emits the review branch when the current branch tip moves on push`() = runBlocking {
    val emissions = CopyOnWriteArrayList<Pair<HostedGitRepositoryMapping, GitRemoteBranch>?>()
    val job = launch(Dispatchers.Default) {
      manager.findHostedRemoteBranchTrackedByCurrent(repository).collect { emissions.add(it) }
    }
    try {
      // Initial resolution: the current branch (master) tracks origin/master.
      waitUntil("initial review branch is resolved", timeout = 10.seconds) { emissions.isNotEmpty() }
      assertEquals("master", emissions.last()?.second?.nameForRemoteOperations)
      val countBeforePush = emissions.size

      // A push advances origin/master's tip while the tracked branch identity stays the same.
      makeCommit("file.txt")
      repository.git("push origin master")
      updateRepositories()

      // The moved tip must produce a fresh emission (this is what the fix restores).
      waitUntil("review branch re-emitted after push", timeout = 10.seconds) { emissions.size > countBeforePush }
      assertEquals("master", emissions.last()?.second?.nameForRemoteOperations)
    }
    finally {
      job.cancel()
    }
  }
}
