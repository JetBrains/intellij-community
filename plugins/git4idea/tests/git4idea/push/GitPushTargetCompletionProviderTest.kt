// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.push

import git4idea.config.GitPushTargetHistoryEntry
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

internal class GitPushTargetCompletionProviderTest {

  @Test
  fun `recent targets come first and shadow the equal remote branches`() {
    val variants = GitPushTargetCompletionProvider.getVariants(
      recentEntries = listOf(entry("review-b"), entry("review-a")),
      currentRemote = "origin",
      remoteBranchNames = listOf("master", "review-a"),
    )

    assertThat(variants.map { it.branchName }).containsExactly("review-b", "review-a", "master")
    assertThat(variants.map { it.isRecent }).containsExactly(true, true, false)
    assertThat(variants.map { it.order }).containsExactly(0, 1, 2)
  }

  @Test
  fun `recent targets of another remote are hidden`() {
    val variants = GitPushTargetCompletionProvider.getVariants(
      recentEntries = listOf(entry("review-a", remote = "upstream")),
      currentRemote = "origin",
      remoteBranchNames = listOf("master"),
    )

    assertThat(variants.map { it.branchName }).containsExactly("master")
    assertThat(variants.none { it.isRecent }).isTrue()
  }

  private fun entry(branch: String, remote: String = "origin"): GitPushTargetHistoryEntry {
    return GitPushTargetHistoryEntry().apply {
      repositoryRootPath = ROOT
      sourceBranch = "master"
      targetRemote = remote
      targetBranch = branch
    }
  }

  companion object {
    private const val ROOT = "/project/repo"
  }
}
