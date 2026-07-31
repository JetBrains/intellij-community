// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.repo.GitRepositoriesHolder
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.vcsTestProjectPathFixture
import git4idea.GitWorkingTree
import git4idea.actions.ref.GitSingleRefAction
import git4idea.config.GitSaveChangesPolicy
import git4idea.repo.getAndInit
import git4idea.repo.isSubmodule
import git4idea.test.git
import git4idea.test.gitPlatformContextFixture
import git4idea.test.registerRepo
import git4idea.update.GitSubmoduleProjectContext
import git4idea.update.gitSubmoduleProjectFixture
import git4idea.workingTrees.ui.GitRepositoryHeader
import git4idea.workingTrees.ui.GitRepositoryKind
import git4idea.workingTrees.ui.GitWorktreeRow
import git4idea.workingTrees.ui.GitWorktreesUiUtil
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitSubmoduleWorkingTreeTest {
  private val contextFixture = gitPlatformContextFixture(vcsTestProjectPathFixture(), saveChangesPolicy = GitSaveChangesPolicy.STASH)
    .gitSubmoduleProjectFixture(checkoutSubmoduleBranch = true, updateSubmoduleRepo = true)
  private val context: GitSubmoduleProjectContext get() = contextFixture.get()

  @Test
  fun `test submodule main worktree is recognized as current`(): Unit = with(context) {
    sub.ensureWorkingTreesUpToDateForTests()

    assertThat(sub.workingTreeHolder.getWorkingTrees()).containsExactlyInAnyOrderElementsOf(
      listOf(GitWorkingTree(sub.root.path, sub.currentBranch!!.fullName, true, true))
    )
  }

  @Test
  @RegistryKey("git.enable.working.trees.feature", "true")
  fun `test submodule and its parent are both worktree-capable`(): Unit = with(context) {
    // Worktrees can be created for nested repositories too, so both the main repo and the submodule qualify.
    val capable = GitWorkingTreesService.findWorktreeCapableRepositories(project)
    assertThat(capable).containsExactlyInAnyOrder(main, sub)

    val status = GitWorkingTreesService.getWorktreeSupportStatus(project)
    assertThat(status)
      .describedAs("A project with a submodule is a multi-repository project")
      .isInstanceOf(GitWorktreeSupportStatus.MultipleRepository::class.java)
    assertThat((status as GitWorktreeSupportStatus.MultipleRepository).repositories).containsExactlyInAnyOrder(main, sub)

    // The tab marks nested repositories; this is the signal it relies on.
    assertThat(sub.isSubmodule()).describedAs("The submodule must be detected as a submodule").isTrue()
    assertThat(main.isSubmodule()).describedAs("The main repository is not a submodule").isFalse()
  }

  @Test
  @RegistryKey("git.enable.working.trees.feature", "true")
  fun `test branch is not reported as checked out in another worktree`(): Unit = with(context) {
    sub.ensureWorkingTreesUpToDateForTests()

    val branch = sub.currentBranch!!
    assertThat(GitSingleRefAction.findCheckedOutWorkingTree(branch, listOf(sub), skipCurrentWorkingTree = true))
      .describedAs("Submodule branch must not be reported as checked out in another worktree")
      .isNull()
  }

  @Test
  @RegistryKey("git.enable.working.trees.feature", "true")
  fun `test the worktrees tab classifies the submodule and its parent`(): Unit = with(context) {
    GitRepositoriesHolder.getAndInit(project)

    val headers = GitWorktreesUiUtil.buildEntries(project)
      .filterIsInstance<GitRepositoryHeader>()
      .associateBy { it.repository.root.path }

    assertThat(headers[sub.root.path]?.kind)
      .describedAs("The submodule must be marked as a submodule")
      .isEqualTo(GitRepositoryKind.SUBMODULE)
    assertThat(headers[main.root.path]?.kind)
      .describedAs("The parent repository must be top-level")
      .isEqualTo(GitRepositoryKind.TOP_LEVEL)
  }

  /**
   * `git worktree list` reports a submodule's main working tree at the submodule's git directory rather than at its
   * checkout, and only the submodule itself rewrites that to its own root. Merging must therefore not key on the main
   * working-tree path, or a submodule and its linked working tree would look like two repositories.
   */
  @Test
  @RegistryKey("git.enable.working.trees.feature", "true")
  fun `test a linked working tree of the submodule is merged into the submodule`(): Unit = with(context) {
    val subWorktreePath = testNioRoot.resolve("sub-feature")
    cd(sub.root.path)
    git("worktree add $subWorktreePath -b feature")
    refresh()
    val subWorktree = registerRepo(project, subWorktreePath)

    sub.ensureWorkingTreesUpToDateForTests()
    subWorktree.ensureWorkingTreesUpToDateForTests()

    // The precondition that breaks a key based on the main working-tree path: the two roots disagree about it.
    val mainPathPerSub = sub.workingTreeHolder.getWorkingTrees().single { it.isMain }.path.path
    val mainPathPerWorktree = subWorktree.workingTreeHolder.getWorkingTrees().single { it.isMain }.path.path
    assertThat(mainPathPerSub).describedAs("The submodule reports its main working tree at its own root").isEqualTo(sub.root.path)
    assertThat(mainPathPerWorktree)
      .describedAs("The linked working tree reports it at the submodule's git directory")
      .isEqualTo(sub.repositoryFiles.commonGitDir.path)

    // ... while the git directory identifies both as the same repository.
    assertThat(subWorktree.repositoryFiles.commonGitDir.path).isEqualTo(sub.repositoryFiles.commonGitDir.path)
    val capable = GitWorkingTreesService.findWorktreeCapableRepositories(project)
    assertThat(capable)
      .describedAs("The linked working tree of the submodule must merge into the submodule")
      .containsExactlyInAnyOrder(main, sub)

    // The tab shows the parent and the submodule only, and the submodule lists both of its working trees once.
    GitRepositoriesHolder.getAndInit(project)
    val entries = GitWorktreesUiUtil.buildEntries(project)
    assertThat(entries.filterIsInstance<GitRepositoryHeader>().map { it.repository.root.path })
      .describedAs("The tab must show only the parent repository and the submodule")
      .containsExactlyInAnyOrder(main.root.path, sub.root.path)
    assertThat(
      entries.filterIsInstance<GitWorktreeRow>()
        .filter { it.repository.root.path == sub.root.path }
        .map { it.gitWorkingTree.path.path }
    )
      .describedAs("The submodule must list both of its working trees exactly once")
      .containsExactlyInAnyOrder(sub.root.path, subWorktree.root.path)
  }
}
