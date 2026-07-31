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

  private data class Repo(val root: String, val worktrees: List<GitWorkingTree>)

  private fun merge(repositories: List<Repo>): List<String> =
    GitWorkingTreesUtil.mergeLinkedWorktreeRepositories(repositories, { it.root }, { it.worktrees })
      .map { it.root }

  @Test
  fun `test merge keeps a single repository per underlying repo, preferring the main checkout`() {
    val main = GitWorkingTree("/repo", "main", isMain = true, isCurrent = true)
    val linked = GitWorkingTree("/repo-feature", "feature", isMain = false, isCurrent = false)
    val shared = listOf(main, linked)

    // Both the main checkout and its linked worktree are registered as roots and report the same list.
    val merged = merge(listOf(Repo("/repo-feature", shared), Repo("/repo", shared)))

    assertThat(merged).isEqualTo(listOf("/repo"))
  }

  @Test
  fun `test merge keeps genuinely distinct repositories`() {
    val a = Repo("/a", listOf(GitWorkingTree("/a", "main", isMain = true, isCurrent = true)))
    val b = Repo("/b", listOf(GitWorkingTree("/b", "main", isMain = true, isCurrent = true)))

    assertThat(merge(listOf(a, b))).containsExactlyInAnyOrder("/a", "/b")
  }

  @Test
  fun `test merge keeps a lone linked worktree whose main checkout is not registered`() {
    val shared = listOf(
      GitWorkingTree("/repo", "main", isMain = true, isCurrent = false),
      GitWorkingTree("/repo-feature", "feature", isMain = false, isCurrent = true),
    )

    // Only the linked worktree is a registered root; it must still be shown (once).
    assertThat(merge(listOf(Repo("/repo-feature", shared)))).isEqualTo(listOf("/repo-feature"))
  }
}
