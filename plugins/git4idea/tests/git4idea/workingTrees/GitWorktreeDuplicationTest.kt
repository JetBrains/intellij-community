// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.test.refresh
import git4idea.repo.GitRepository
import git4idea.repo.GitWorkingTreeHolderImpl
import git4idea.repo.expectEvent
import git4idea.repo.getAndInit
import git4idea.test.GitPlatformTestContext
import git4idea.test.cloneRepo
import git4idea.test.createRepository
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.initRepo
import git4idea.test.registerRepo
import git4idea.workingTrees.ui.GitWorktreesUiUtil
import git4idea.workingTrees.ui.GitRepositoryHeader
import git4idea.workingTrees.ui.GitWorktreeRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * A linked working tree can itself be registered as a VCS root of a multi-root project. Because every working
 * tree of a repository reports the same worktree list, such a root must not appear as a *separate* repository
 * with a duplicated list — it belongs to the same underlying repository and must be merged into it.
 */
@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorktreeDuplicationTest {
  private val contextFixture = gitPlatformContextFixture()
  private val context: GitPlatformTestContext get() = contextFixture.get()

  private lateinit var holder: GitRepositoriesHolder

  @Test
  fun `test a linked worktree of the main repository registered as a root is merged`(): Unit = with(context) {
    val main = createRepository(project, projectNioRoot, true)
    val worktreePath = testNioRoot.resolve("main-feature")
    cd(main.root.path)
    git("worktree add -B feature $worktreePath")
    refresh()
    val worktreeRepo = registerRepo(project, worktreePath)

    holder = GitRepositoriesHolder.getAndInit(project)
    syncWorktrees(main)
    syncWorktrees(worktreeRepo)

    // A repository and its linked worktree count as a single repository, not a multi-repo project.
    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)
    assertThat(status)
      .describedAs("A repository and its linked worktree must count as one repository")
      .isInstanceOf(GitWorktreeSupportStatus.SingleRepository::class.java)

    // The linked worktree root is collapsed into the main checkout.
    val capable = GitWorkingTreesService.findWorktreeCapableRepositories(project)
    assertThat(capable.map { it.root.path }).isEqualTo(listOf(main.root.path))

    // The tab lists both worktrees exactly once, with no repository headers and no duplication.
    val entries = GitWorktreesUiUtil.buildEntries(project)
    assertThat(entries.filterIsInstance<GitRepositoryHeader>())
      .describedAs("A single underlying repository must not produce repository headers")
      .isEmpty()
    val rows = entries.filterIsInstance<GitWorktreeRow>()
    assertThat(rows).describedAs("Both worktrees must be listed exactly once").hasSize(2)
    assertThat(rows.count { it.gitWorkingTree.isMain }).describedAs("Exactly one worktree is the main one").isEqualTo(1)
  }

  @Test
  fun `test a linked worktree of a nested repository registered as a root is merged`(): Unit = with(context) {
    val main = createRepository(project, projectNioRoot, true)
    val sub = createRepository(project, projectNioRoot.resolve("sub"), true)
    val subWorktreePath = testNioRoot.resolve("sub-feature")
    cd(sub.root.path)
    git("worktree add -B feature $subWorktreePath")
    refresh()
    val subWorktreeRepo = registerRepo(project, subWorktreePath)

    holder = GitRepositoriesHolder.getAndInit(project)
    syncWorktrees(main)
    syncWorktrees(sub)
    syncWorktrees(subWorktreeRepo)

    // Only the two genuine repositories remain; the nested repo's linked worktree merges into it.
    val capable = GitWorkingTreesService.findWorktreeCapableRepositories(project)
    assertThat(capable.map { it.root.path })
      .describedAs("The linked worktree of the nested repository must merge into it")
      .containsExactlyInAnyOrder(main.root.path, sub.root.path)

    val headerRoots = GitWorktreesUiUtil.buildEntries(project)
      .filterIsInstance<GitRepositoryHeader>()
      .map { it.repository.root.path }
    assertThat(headerRoots)
      .describedAs("The tab must show only the two genuine repositories")
      .containsExactlyInAnyOrder(main.root.path, sub.root.path)
  }

  /**
   * A bare repository has no main checkout: its administrative entry is reported by `git worktree list` but is not a
   * working tree, so none of its worktrees is reported as the main one. Merging must fall back to the shared git
   * directory instead of the (absent) main working-tree path.
   */
  @Test
  fun `test the working trees of a bare repository are merged into one repository`(): Unit = with(context) {
    val source = testNioRoot.resolve("source")
    initRepo(project, source, true)
    val bareRoot = testNioRoot.resolve("bare-repo")
    cloneRepo(project, source.toString(), bareRoot.resolve(".git").toString(), true)

    cd(bareRoot.resolve(".git"))
    val wtMainPath = bareRoot.resolve("wt-main")
    val wtFeaturePath = bareRoot.resolve("wt-feature")
    git("worktree add $wtMainPath -b wt-main")
    git("worktree add $wtFeaturePath -b feature")
    refresh()
    val wtMain = registerRepo(project, wtMainPath)
    val wtFeature = registerRepo(project, wtFeaturePath)

    holder = GitRepositoriesHolder.getAndInit(project)
    syncWorktrees(wtMain)
    syncWorktrees(wtFeature)

    // The precondition that breaks a key based on the main working-tree path.
    assertThat(wtMain.workingTreeHolder.getWorkingTrees().filter { it.isMain })
      .describedAs("A bare repository reports no main working tree")
      .isEmpty()
    assertThat(wtFeature.repositoryFiles.commonGitDir.path)
      .describedAs("Both working trees share the git directory of the bare repository")
      .isEqualTo(wtMain.repositoryFiles.commonGitDir.path)

    // Both roots belong to the same bare repository, so they count as one.
    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)
    assertThat(status)
      .describedAs("The working trees of one bare repository must count as one repository")
      .isInstanceOf(GitWorktreeSupportStatus.SingleRepository::class.java)
    val capable = GitWorkingTreesService.findWorktreeCapableRepositories(project)
    assertThat(capable).hasSize(1)
    assertThat(listOf(wtMain.root.path, wtFeature.root.path)).containsAll(capable.map { it.root.path })

    // The tab lists both working trees exactly once, with no repository headers and no duplication.
    val entries = GitWorktreesUiUtil.buildEntries(project)
    assertThat(entries.filterIsInstance<GitRepositoryHeader>())
      .describedAs("A single underlying repository must not produce repository headers")
      .isEmpty()
    val rows = entries.filterIsInstance<GitWorktreeRow>()
    assertThat(rows.map { it.gitWorkingTree.path.path })
      .describedAs("Both working trees must be listed exactly once")
      .containsExactlyInAnyOrder(wtMain.root.path, wtFeature.root.path)
  }

  private fun syncWorktrees(repository: GitRepository) {
    holder.expectEvent(
      { withContext(Dispatchers.IO) { (repository.workingTreeHolder as GitWorkingTreeHolderImpl).updateState() } },
      { event, _ -> event == GitRepositoriesHolder.UpdateType.WORKING_TREES_LOADED },
    )
  }
}
