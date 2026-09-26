// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.repo

import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.components.service
import com.intellij.platform.project.projectId
import com.intellij.testFramework.assertErrorLogged
import com.intellij.testFramework.junit5.EnableTracingFor
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.rpc.GitRepositoryApi
import com.intellij.vcs.git.rpc.GitUiSettingsApi
import com.intellij.vcsUtil.VcsUtil.getFilePath
import git4idea.GitStandardLocalBranch
import git4idea.GitTag
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchesCollection
import git4idea.test.GitSingleRepoContext
import git4idea.test.checkoutNew
import git4idea.test.createSubRepository
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.ui.branch.GitBranchManager
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.condition.OS

@TestApplication
@EnableTracingFor(categoryClasses = [GitRepositoriesHolder::class])
internal class GitRepositoriesFrontendHolderTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test single repository data is available`(): Unit = with(context) {
    val holder = GitRepositoriesHolder.getInstance(project)
    assertThat(holder.initialized).isFalse()
    assertErrorLogged<Throwable> {
      holder.getAll()
    }

    runBlocking {
      holder.awaitInitialization()
    }

    val allReposInHolder = holder.getAll()
    assertThat(allReposInHolder).hasSize(1)
    val singleRepoInHolder = allReposInHolder.single()
    assertThat(singleRepoInHolder.repositoryId).isEqualTo(repo.repositoryId())

    assertThat(singleRepoInHolder).isEqualTo(holder.get(repo.repositoryId()))

    assertThat(holder.initialized).isTrue()
  }

  @Test
  fun `test data is updated after repository is removed`(): Unit = with(context) {
    val holder = GitRepositoriesHolder.getAndInit(project)
    assertThat(holder.getAll()).hasSize(1)
    holder.expectEvent(
      { vcsManager.unregisterVcs(vcs) },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.REPOSITORY_DELETED })

    assertThat(holder.getAll()).isEmpty()
  }

  @Test
  fun `test new repository is added`(): Unit = with(context) {
    val holder = GitRepositoriesHolder.getAndInit(project)
    assertThat(holder.getAll()).hasSize(1)

    holder.expectEvent(
      { repo.createSubRepository("nested") },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.REPOSITORY_CREATED })

    assertThat(holder.getAll()).hasSize(2)
  }

  @Test
  fun `test favorite branches are updated`(): Unit = with(context) {
    val holder = GitRepositoriesHolder.getAndInit(project)

    assertThat(holder.getTestRepo().favoriteRefs.contains(GitStandardLocalBranch("master")))
      .describedAs("master should be favorite")
      .isTrue()

    holder.expectEvent(
      {
        project.service<GitBranchManager>().setFavorite(
          GitBranchType.LOCAL,
          repo,
          "master",
          false
        )
      },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.FAVORITE_REFS_UPDATED })

    assertThat(holder.getTestRepo().favoriteRefs.contains(GitStandardLocalBranch("master")))
      .describedAs("master should not be favorite anymore")
      .isFalse()
  }

  @Test
  fun `test repo state is updated`(): Unit = with(context) {
    val holder = GitRepositoriesHolder.getAndInit(project)

    val masterBranch = GitStandardLocalBranch("master")
    assertThat(holder.getTestRepo().state.isCurrentRef(masterBranch)).describedAs("master is current branch").isTrue()

    val branchesToCheckout = (0..5).map { GitStandardLocalBranch("branch-$it") }
    branchesToCheckout.forEach { branch -> repo.checkoutNew(branch.name) }

    val newCurrentBranch = GitStandardLocalBranch("new-branch")
    holder.expectEvent(
      {
        repo.checkoutNew(newCurrentBranch.name)
        // todo: fix the test to avoid special windows case
        // for WINDOWS need to call update explicitly, because the scheduled updates in UpdateRequestsQueue are triggered once per 300 ms
        // and can happen at any moment, which leads to getting the wrong branch as a current state
        if (OS.current() == OS.WINDOWS) repo.update()
      },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.REPOSITORY_STATE_UPDATED })

    val stateAfterUpdate = holder.getTestRepo().state
    assertThat(stateAfterUpdate.isCurrentRef(newCurrentBranch)).describedAs("new-branch is current branch").isTrue()

    // max 5 branches in order of checkout
    val expectedRecentBranches =
      listOf(newCurrentBranch) + branchesToCheckout.reversed().take(GitBranchesCollection.MAX_RECENT_CHECKOUT_BRANCHES - 1)
    assertThat(stateAfterUpdate.recentBranches).describedAs("recent branches are updated").isEqualTo(expectedRecentBranches)
  }

  @Test
  fun `test tags can be hidden`(): Unit = with(context) {
    val tagName = "hello"
    repo.git("tag $tagName")
    (repo.tagsHolder as? GitRepositoryTagsHolderImpl)?.updateForTests()

    val holder = GitRepositoriesHolder.getAndInit(project)

    assertThat(holder.getTestRepo().state.tags).isEqualTo(setOf(GitTag(tagName)))

    holder.expectEvent(
      { GitUiSettingsApi.getInstance().setShowTags(project.projectId(), false) },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.TAGS_HIDDEN }
    )

    assertThat(holder.getTestRepo().state.tags).isEmpty()
  }

  @Test
  fun `test tags can be hidden and shown`(): Unit = with(context) {
    val tagName = "hello"
    repo.git("tag $tagName")
    (repo.tagsHolder as? GitRepositoryTagsHolderImpl)?.updateForTests()

    val holder = GitRepositoriesHolder.getAndInit(project)

    holder.expectEvent(
      {
        GitUiSettingsApi.getInstance().setShowTags(project.projectId(), false)
        GitUiSettingsApi.getInstance().setShowTags(project.projectId(), true)
      },
      { _, events ->
        events.contains(GitRepositoriesHolder.UpdateType.TAGS_HIDDEN) && events.contains(GitRepositoriesHolder.UpdateType.TAGS_LOADED)
      }
    )

    assertThat(holder.getTestRepo().state.tags).isEqualTo(setOf(GitTag(tagName)))
  }

  @Test
  fun `test force state sync`(): Unit = with(context) {
    val holder = GitRepositoriesHolder.getAndInit(project)

    holder.expectEvent(
      { GitRepositoryApi.getInstance().forceSync(project.projectId()) },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.RELOAD_STATE }
    )
  }

  @Test
  fun `test update of unknown repo forces sync`(): Unit = with(context) {
    val holder = GitRepositoriesHolder.getAndInit(project)

    holder.expectEvent(
      {
        holder.clearRepositories()
        assertThat(holder.getAll()).isEmpty()
        repo.checkoutNew("whatever")
      },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.RELOAD_STATE }
    )

    holder.getTestRepo()
  }

  private fun GitRepositoriesHolder.getTestRepo(): GitRepositoryModel {
    val repo = context.repo
    val holderRepo = checkNotNull(get(repo.repositoryId()))
    assertThat(holderRepo.root).isEqualTo(getFilePath(repo.root))
    return holderRepo
  }
}
