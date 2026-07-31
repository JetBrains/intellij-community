// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees

import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.git.workingTrees.GitWorkingTreesUtil
import git4idea.GitStandardLocalBranch
import git4idea.GitTag
import git4idea.GitWorkingTree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
@RegistryKey("git.enable.working.trees.feature", "true")
internal class GitWorkingTreesUtilTest {

  // Built lazily: the GitWorkingTree constructor needs the application (VcsContextFactory), which is only
  // available once the @TestApplication extension has started it, not while fields are being initialized.
  private val mainTree by lazy { GitWorkingTree("/repo", "main", isMain = true, isCurrent = true) }
  private val featureTree by lazy { GitWorkingTree("/repo-feature", "feature", isMain = false, isCurrent = false) }
  private val detachedTree by lazy { GitWorkingTree("/repo-detached", null, isMain = false, isCurrent = false) }
  private val trees by lazy { listOf(mainTree, featureTree, detachedTree) }

  @Test
  fun `test finds a linked working tree that has the local branch checked out`() {
    val feature = GitStandardLocalBranch("feature")

    assertThat(GitWorkingTreesUtil.findCheckedOutWorkingTree(feature, trees, skipCurrentWorkingTree = true)).isEqualTo(featureTree)
    assertThat(GitWorkingTreesUtil.findCheckedOutWorkingTree(feature, trees, skipCurrentWorkingTree = false)).isEqualTo(featureTree)
  }

  @Test
  fun `test skipCurrentWorkingTree excludes the current working tree`() {
    val main = GitStandardLocalBranch("main")

    assertThat(GitWorkingTreesUtil.findCheckedOutWorkingTree(main, trees, skipCurrentWorkingTree = true))
      .describedAs("The current working tree must be skipped").isNull()
    assertThat(GitWorkingTreesUtil.findCheckedOutWorkingTree(main, trees, skipCurrentWorkingTree = false))
      .describedAs("Without skipping, the current working tree matches").isEqualTo(mainTree)
  }

  @Test
  fun `test a tag reference never matches a working tree`() {
    assertThat(GitWorkingTreesUtil.findCheckedOutWorkingTree(GitTag("v1.0"), trees, skipCurrentWorkingTree = true)).isNull()
  }

  @Test
  fun `test a branch that is not checked out anywhere is not found`() {
    val other = GitStandardLocalBranch("other")
    assertThat(GitWorkingTreesUtil.findCheckedOutWorkingTree(other, trees, skipCurrentWorkingTree = true)).isNull()
  }

  @Test
  @RegistryKey("git.enable.working.trees.feature", "false")
  fun `test nothing is found when the feature is disabled`() {
    val feature = GitStandardLocalBranch("feature")

    assertThat(GitWorkingTreesUtil.findCheckedOutWorkingTree(feature, trees, skipCurrentWorkingTree = true)).isNull()
  }

  private data class Repo(val root: String, val commonGitDir: String, val worktrees: List<GitWorkingTree>)

  private fun merge(repositories: List<Repo>): List<String> =
    GitWorkingTreesUtil.mergeLinkedWorktreeRepositories(repositories, { it.root }, { it.commonGitDir }, { it.worktrees })
      .map { it.root }

  /** A working tree list as the root at [currentPath] reports it: exactly its own entry is the current one. */
  private fun sharedList(mainPath: String?, linkedPaths: List<String>, currentPath: String): List<GitWorkingTree> =
    listOfNotNull(mainPath?.let { GitWorkingTree(it, "main", isMain = true, isCurrent = it == currentPath) }) +
    linkedPaths.map { GitWorkingTree(it, it.substringAfterLast('/'), isMain = false, isCurrent = it == currentPath) }

  @Test
  fun `test merge keeps a single repository per underlying repo, preferring the main checkout`() {
    val main = Repo("/repo", "/repo/.git", sharedList("/repo", listOf("/repo-feature"), currentPath = "/repo"))
    val linked = Repo("/repo-feature", "/repo/.git", sharedList("/repo", listOf("/repo-feature"), currentPath = "/repo-feature"))

    // Both the main checkout and its linked worktree are registered as roots and report the same list.
    assertThat(merge(listOf(linked, main))).isEqualTo(listOf("/repo"))
  }

  @Test
  fun `test merge keeps genuinely distinct repositories`() {
    val a = Repo("/a", "/a/.git", sharedList("/a", emptyList(), currentPath = "/a"))
    val b = Repo("/b", "/b/.git", sharedList("/b", emptyList(), currentPath = "/b"))

    assertThat(merge(listOf(a, b))).containsExactlyInAnyOrder("/a", "/b")
  }

  @Test
  fun `test merge keeps a lone linked worktree whose main checkout is not registered`() {
    val linked = Repo("/repo-feature", "/repo/.git", sharedList("/repo", listOf("/repo-feature"), currentPath = "/repo-feature"))

    // Only the linked worktree is a registered root; it must still be shown (once).
    assertThat(merge(listOf(linked))).isEqualTo(listOf("/repo-feature"))
  }

  @Test
  fun `test merge groups repositories whose working trees are not loaded yet`() {
    // Before the working-tree lists arrive there is no isMain entry to key on, but the git directory already identifies
    // the repository, so the roots must not show up as two repositories and then collapse into one.
    val main = Repo("/repo", "/repo/.git", emptyList())
    val linked = Repo("/repo-feature", "/repo/.git", emptyList())

    assertThat(merge(listOf(linked, main))).isEqualTo(listOf("/repo"))
  }

  @Test
  fun `test merge collapses the working trees of a bare repository, which has no main checkout`() {
    // A bare repository's own entry is not a working tree and is not reported as one, so neither root has an isMain
    // entry: only the shared git directory ties them together.
    val worktrees = listOf("/repo/wt-feature", "/repo/wt-main")
    val wtMain = Repo("/repo/wt-main", "/repo/.git", sharedList(null, worktrees, currentPath = "/repo/wt-main"))
    val wtFeature = Repo("/repo/wt-feature", "/repo/.git", sharedList(null, worktrees, currentPath = "/repo/wt-feature"))

    // No main checkout exists, so the first-sorting root survives — deterministically, whatever the input order.
    assertThat(merge(listOf(wtMain, wtFeature))).isEqualTo(listOf("/repo/wt-feature"))
    assertThat(merge(listOf(wtFeature, wtMain))).isEqualTo(listOf("/repo/wt-feature"))
  }

  @Test
  fun `test merge collapses a submodule and its linked working tree despite the reported main path`() {
    // For a submodule, git reports the main working tree at the submodule's git directory. Only the root that is the
    // submodule itself rewrites that to its checkout, so the two roots disagree about the main path.
    val gitDir = "/parent/.git/modules/sub"
    val sub = Repo("/parent/sub", gitDir, sharedList("/parent/sub", listOf("/sub-feature"), currentPath = "/parent/sub"))
    val subLinked = Repo("/sub-feature", gitDir, sharedList(gitDir, listOf("/sub-feature"), currentPath = "/sub-feature"))

    assertThat(merge(listOf(subLinked, sub))).isEqualTo(listOf("/parent/sub"))
  }

  @Test
  fun `test merge keeps a submodule distinct from its parent repository`() {
    val parent = Repo("/parent", "/parent/.git", sharedList("/parent", emptyList(), currentPath = "/parent"))
    val sub = Repo("/parent/sub", "/parent/.git/modules/sub", sharedList("/parent/sub", emptyList(), currentPath = "/parent/sub"))

    assertThat(merge(listOf(parent, sub))).containsExactlyInAnyOrder("/parent", "/parent/sub")
  }
}
