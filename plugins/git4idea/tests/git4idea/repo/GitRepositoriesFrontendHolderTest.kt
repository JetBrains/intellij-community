// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.openapi.components.service
import com.intellij.platform.project.projectId
import com.intellij.testFramework.assertErrorLogged
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.rpc.GitRepositoryApi
import com.intellij.vcs.git.rpc.GitUiSettingsApi
import git4idea.GitStandardLocalBranch
import git4idea.GitTag
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchesCollection
import git4idea.test.GitSingleRepoTest
import git4idea.test.checkoutNew
import git4idea.test.createSubRepository
import git4idea.test.git
import git4idea.ui.branch.GitBranchManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

@OptIn(ExperimentalCoroutinesApi::class)
class GitRepositoriesFrontendHolderTest : GitSingleRepoTest() {
  private val updatesChanel = Channel<GitRepositoriesHolder.UpdateType>(capacity = 1000, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  override fun getDebugLogCategories() = super.getDebugLogCategories().plus(GitRepositoriesHolder::class.java.name)

  override fun setUp() {
    super.setUp()

    project.messageBus.connect().subscribe(GitRepositoriesHolder.UPDATES,
                                           GitRepositoriesHolder.UpdatesListener { updateType -> updatesChanel.trySend(updateType) })
  }

  fun `test single repository data is available`() {
    val holder = GitRepositoriesHolder.getInstance(project)
    assertFalse(holder.initialized)
    assertErrorLogged<Throwable> {
      holder.getAll()
    }

    runBlocking {
      holder.init()
    }

    val allReposInHolder = holder.getAll()
    assertSize(1, allReposInHolder)
    val singleRepoInHolder = allReposInHolder.single()
    assertEquals(repo.rpcId, singleRepoInHolder.repositoryId)

    assertEquals(holder.get(repo.rpcId), singleRepoInHolder)

    assertTrue(holder.initialized)
  }

  fun `test data is updated after repository is removed`() {
    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }
    assertSize(1, holder.getAll())
    executeAndExpectEvent(
      { vcsManager.unregisterVcs(vcs) },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.REPOSITORY_DELETED })

    assertSize(0, holder.getAll())
  }

  fun `test new repository is added`() {
    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }
    assertSize(1, holder.getAll())

    executeAndExpectEvent(
      { repo.createSubRepository("nested") },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.REPOSITORY_CREATED })

    assertSize(2, holder.getAll())
  }

  fun `test favorite branches are updated`() {
    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }

    assertTrue("master should be favorite",
               holder.getTestRepo().favoriteRefs.contains(GitStandardLocalBranch("master")))

    executeAndExpectEvent(
      {
        project.service<GitBranchManager>().setFavorite(
          GitBranchType.LOCAL,
          repo,
          "master",
          false
        )
      },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.FAVORITE_REFS_UPDATED })

    assertFalse("master should not be favorite anymore",
                holder.getTestRepo().favoriteRefs.contains(GitStandardLocalBranch("master")))
  }

  fun `test repo state is updated`() {
    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }

    val masterBranch = GitStandardLocalBranch("master")
    assertTrue("master is current branch", holder.getTestRepo().state.isCurrentRef(masterBranch))

    val branchesToCheckout = (0..5).map { GitStandardLocalBranch("branch-$it") }
    branchesToCheckout.forEach { branch -> repo.checkoutNew(branch.name) }

    val newCurrentBranch = GitStandardLocalBranch("new-branch")
    executeAndExpectEvent(
      { repo.checkoutNew(newCurrentBranch.name) },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.REPOSITORY_STATE_UPDATED })

    val stateAfterUpdate = holder.getTestRepo().state
    assertTrue("master is current branch", stateAfterUpdate.isCurrentRef(newCurrentBranch))

    // max 5 branches in order of checkout
    val expectedRecentBranches = listOf(newCurrentBranch) + branchesToCheckout.reversed().take(GitBranchesCollection.MAX_RECENT_CHECKOUT_BRANCHES - 1)
    assertEquals("recent branches are updated", expectedRecentBranches, stateAfterUpdate.recentBranches)
  }

  fun `test tags can be hidden`() {
    val tagName = "hello"
    repo.git("tag $tagName")
    repo.tagHolder.ensureUpToDateForTests()

    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }

    assertEquals(setOf(GitTag(tagName)), holder.getTestRepo().state.tags)

    executeAndExpectEvent(
      { GitUiSettingsApi.getInstance().setShowTags(project.projectId(), false) },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.TAGS_HIDDEN }
    )

    assertEquals(emptySet<GitTag>(), holder.getTestRepo().state.tags)
  }

  fun `test tags can be hidden and shown`() {
    val tagName = "hello"
    repo.git("tag $tagName")
    repo.tagHolder.ensureUpToDateForTests()

    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }

    executeAndExpectEvent(
      {
        GitUiSettingsApi.getInstance().setShowTags(project.projectId(), false)
        GitUiSettingsApi.getInstance().setShowTags(project.projectId(), true)
      },
      { _, events ->
        events.contains(GitRepositoriesHolder.UpdateType.TAGS_HIDDEN) && events.contains(GitRepositoriesHolder.UpdateType.TAGS_LOADED)
      }
    )

    assertEquals(setOf(GitTag(tagName)), holder.getTestRepo().state.tags)
  }

  fun `test force state sync`() {
    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }

    executeAndExpectEvent(
      { GitRepositoryApi.getInstance().forceSync(project.projectId()) },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.RELOAD_STATE }
    )
  }

  fun `test update of unknown repo forces sync`() {
    val holder = GitRepositoriesHolder.getInstance(project)
    runBlocking {
      holder.init()
    }

    executeAndExpectEvent(
      {
        holder.clearRepositories()
        assertTrue(holder.getAll().isEmpty())
        repo.checkoutNew("whatever")
      },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.RELOAD_STATE }
    )

    holder.getTestRepo()
  }

  private fun GitRepositoriesHolder.getTestRepo(): GitRepositoryModel {
    val holderRepo = checkNotNull(get(repo.rpcId))
    assertEquals(holderRepo.root, repo.root)
    return holderRepo
  }

  private fun executeAndExpectEvent(
    operation: suspend () -> Unit,
    condition: (currentEvent: GitRepositoriesHolder.UpdateType, previousEvents: List<GitRepositoriesHolder.UpdateType>) -> Boolean,
  ) {
    runBlocking {
      skipEvents()
      operation()
      val collected = mutableListOf<GitRepositoriesHolder.UpdateType>()
      withTimeout(5.seconds) {
        updatesChanel.consumeAsFlow().first {
          LOG.info("Received update: $it")
          collected.add(it)
          condition(it, collected)
        }
      }
    }
  }

  private suspend fun skipEvents() {
    withTimeout(1.seconds) {
      while (!updatesChanel.isEmpty) {
        updatesChanel.tryReceive()
      }
    }
  }
}