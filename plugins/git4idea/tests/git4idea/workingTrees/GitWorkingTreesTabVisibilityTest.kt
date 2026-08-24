// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.openapi.vcs.Executor.cd
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.test.refresh
import git4idea.repo.GitRepository
import git4idea.repo.GitWorkingTreeHolderImpl
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitPlatformTestContext
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.workingTrees.ui.GitWorkingTreesContentVisibilityPredicate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/** Auto-show heuristics and user-override persistence for the Worktrees tab. */
@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorkingTreesTabVisibilityTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private var holder: GitRepositoriesHolder? = null

  private val GitPlatformTestContext.service get() = GitWorkingTreesService.getInstance(project)

  @Test
  fun `test tab is hidden when there are no repositories`(): Unit = with(context) {
    assertThat(service.shouldWorkingTreesTabBeShown()).isFalse()
  }

  @Test
  fun `test tab is hidden for a single-worktree repository by default`(): Unit = with(context) {
    createRepo()
    assertThat(service.shouldWorkingTreesTabBeShown()).isFalse()
  }

  @Test
  fun `test tab is shown when a repository has multiple worktrees`(): Unit = with(context) {
    val repo = createRepo()
    addWorktree(repo)
    assertThat(service.shouldWorkingTreesTabBeShown()).isTrue()
  }

  @Test
  fun `test closed-by-user hides the tab even with multiple worktrees`(): Unit = with(context) {
    val repo = createRepo()
    addWorktree(repo)
    service.workingTreesTabClosedByUser()
    assertThat(service.shouldWorkingTreesTabBeShown()).isFalse()
  }

  @Test
  fun `test opened-by-user shows the tab even with a single worktree`(): Unit = with(context) {
    createRepo()
    service.workingTreesTabOpenedByUser()
    assertThat(service.shouldWorkingTreesTabBeShown()).isTrue()
  }

  @Test
  fun `test the visibility predicate mirrors the service`(): Unit = with(context) {
    val repo = createRepo()
    addWorktree(repo)
    assertThat(GitWorkingTreesContentVisibilityPredicate().test(project)).isEqualTo(service.shouldWorkingTreesTabBeShown())
  }

  private fun GitPlatformTestContext.createRepo(): GitRepository {
    val repo = createRepository(project, projectNioRoot, true)
    holder = GitRepositoriesHolder.getAndInit(project)
    return repo
  }

  private fun GitPlatformTestContext.addWorktree(repository: GitRepository) {
    cd(repository.root.path)
    git("worktree add -B feature ../treeRoot")
    refresh()
    holder!!.expectEvent(
      { withContext(Dispatchers.IO) { (repository.workingTreeHolder as GitWorkingTreeHolderImpl).updateState() } },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED },
    )
  }
}
