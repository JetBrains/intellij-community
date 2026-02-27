// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import com.intellij.agent.workbench.sessions.git.GitWorktreeDiscovery
import com.intellij.agent.workbench.sessions.git.shortBranchName
import com.intellij.agent.workbench.sessions.git.worktreeDisplayName
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitWorktreeDiscoveryTest {
  @Test
  fun parseGitFileExtractsAbsoluteGitDir() {
    val content = "gitdir: /home/user/repo/.git/worktrees/feature-x\n"
    assertThat(GitWorktreeDiscovery.parseGitFile(content))
      .isEqualTo("/home/user/repo/.git/worktrees/feature-x")
  }

  @Test
  fun parseGitFileExtractsRelativeGitDir() {
    val content = "gitdir: ../.git/worktrees/feature-x\n"
    assertThat(GitWorktreeDiscovery.parseGitFile(content))
      .isEqualTo("../.git/worktrees/feature-x")
  }

  @Test
  fun parseGitFileTrimsWhitespace() {
    val content = "gitdir:   /repo/.git/worktrees/wt  \n"
    assertThat(GitWorktreeDiscovery.parseGitFile(content))
      .isEqualTo("/repo/.git/worktrees/wt")
  }

  @Test
  fun parseGitFileReturnsNullForEmptyContent() {
    assertThat(GitWorktreeDiscovery.parseGitFile("")).isNull()
    assertThat(GitWorktreeDiscovery.parseGitFile("\n")).isNull()
  }

  @Test
  fun parseGitFileReturnsNullForNonGitdirContent() {
    assertThat(GitWorktreeDiscovery.parseGitFile("not a gitdir line")).isNull()
  }

  @Test
  fun parseGitFileReturnsNullForGitdirWithEmptyPath() {
    assertThat(GitWorktreeDiscovery.parseGitFile("gitdir: ")).isNull()
    assertThat(GitWorktreeDiscovery.parseGitFile("gitdir:")).isNull()
  }

  @Test
  fun resolveRepoRootFromGitDirExtractsRepoRoot() {
    val gitDir = "/home/user/my-project/.git/worktrees/feature-branch"
    assertThat(GitWorktreeDiscovery.resolveRepoRootFromGitDir(gitDir))
      .isEqualTo("/home/user/my-project")
  }

  @Test
  fun resolveRepoRootFromGitDirHandlesNestedPath() {
    val gitDir = "/work/repos/org/project/.git/worktrees/wt-1"
    assertThat(GitWorktreeDiscovery.resolveRepoRootFromGitDir(gitDir))
      .isEqualTo("/work/repos/org/project")
  }

  @Test
  fun resolveRepoRootFromGitDirReturnsNullForSubmodule() {
    val gitDir = "/home/user/repo/.git/modules/submod"
    assertThat(GitWorktreeDiscovery.resolveRepoRootFromGitDir(gitDir)).isNull()
  }

  @Test
  fun resolveRepoRootFromGitDirReturnsNullForPlainGitDir() {
    assertThat(GitWorktreeDiscovery.resolveRepoRootFromGitDir("/repo/.git")).isNull()
  }

  @Test
  fun resolveRepoRootFromGitDirReturnsNullForEmptyString() {
    assertThat(GitWorktreeDiscovery.resolveRepoRootFromGitDir("")).isNull()
  }

  @Test
  fun parseWorktreeListPorcelainSingleMainWorktree() {
    val output = """
      worktree /home/user/project
      HEAD abc123
      branch refs/heads/main

    """.trimIndent()

    val result = GitWorktreeDiscovery.parseWorktreeListPorcelain(output)
    assertThat(result).hasSize(1)
    assertThat(result[0].path).isEqualTo("/home/user/project")
    assertThat(result[0].branch).isEqualTo("refs/heads/main")
    assertThat(result[0].isMain).isTrue()
  }

  @Test
  fun parseWorktreeListPorcelainMultipleWorktrees() {
    val output = """
      worktree /home/user/project
      HEAD abc123
      branch refs/heads/main

      worktree /home/user/project-feature
      HEAD def456
      branch refs/heads/feature-x

      worktree /home/user/project-bugfix
      HEAD 789aaa
      branch refs/heads/bugfix-y

    """.trimIndent()

    val result = GitWorktreeDiscovery.parseWorktreeListPorcelain(output)
    assertThat(result).hasSize(3)
    assertThat(result[0].isMain).isTrue()
    assertThat(result[1].isMain).isFalse()
    assertThat(result[2].isMain).isFalse()
    assertThat(result.map { it.path }).containsExactly(
      "/home/user/project",
      "/home/user/project-feature",
      "/home/user/project-bugfix",
    )
    assertThat(result.map { it.branch }).containsExactly(
      "refs/heads/main",
      "refs/heads/feature-x",
      "refs/heads/bugfix-y",
    )
  }

  @Test
  fun parseWorktreeListPorcelainDetachedHead() {
    val output = """
      worktree /home/user/project
      HEAD abc123
      branch refs/heads/main

      worktree /home/user/project-detached
      HEAD def456
      detached

    """.trimIndent()

    val result = GitWorktreeDiscovery.parseWorktreeListPorcelain(output)
    assertThat(result).hasSize(2)
    assertThat(result[1].branch).isNull()
  }

  @Test
  fun parseWorktreeListPorcelainEmptyOutput() {
    assertThat(GitWorktreeDiscovery.parseWorktreeListPorcelain("")).isEmpty()
  }

  @Test
  fun parseWorktreeListPorcelainNoTrailingBlankLine() {
    val output = """
      worktree /home/user/project
      HEAD abc123
      branch refs/heads/main
    """.trimIndent()

    val result = GitWorktreeDiscovery.parseWorktreeListPorcelain(output)
    assertThat(result).hasSize(1)
    assertThat(result[0].path).isEqualTo("/home/user/project")
  }

  @Test
  fun shortBranchNameStripsRefsHeadsPrefix() {
    assertThat(shortBranchName("refs/heads/feature-x")).isEqualTo("feature-x")
    assertThat(shortBranchName("refs/heads/main")).isEqualTo("main")
  }

  @Test
  fun shortBranchNamePassesThroughNonRefPaths() {
    assertThat(shortBranchName("main")).isEqualTo("main")
  }

  @Test
  fun shortBranchNameReturnsNullForNull() {
    assertThat(shortBranchName(null)).isNull()
  }

  @Test
  fun worktreeDisplayNameReturnsLastPathComponent() {
    assertThat(worktreeDisplayName("/home/user/my-feature")).isEqualTo("my-feature")
    assertThat(worktreeDisplayName("/work/project")).isEqualTo("project")
  }
}
