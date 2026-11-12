// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class GitBranchAlreadyCheckedOutInOtherWorktreeDetectorTest {
  @Test
  fun `test detects git 2_43+ wording`() {
    val match = GitBranchAlreadyCheckedOutInOtherWorktreeDetector.matchInOutput(
      listOf("fatal: 'master' is already used by worktree at '/other/worktree/path'"))

    assertThat(match).isNotNull()
    assertThat(match!!.branchName).isEqualTo("master")
    assertThat(match.worktreePath).isEqualTo("/other/worktree/path")
  }

  @Test
  fun `test detects pre-2_43 wording`() {
    val match = GitBranchAlreadyCheckedOutInOtherWorktreeDetector.matchInOutput(
      listOf("fatal: 'master' is already checked out at '/other/worktree/path'"))

    assertThat(match).isNotNull()
    assertThat(match!!.branchName).isEqualTo("master")
    assertThat(match.worktreePath).isEqualTo("/other/worktree/path")
  }

  @Test
  fun `test does not detect unrelated fatal error`() {
    val match = GitBranchAlreadyCheckedOutInOtherWorktreeDetector.matchInOutput(
      listOf("fatal: invalid reference: master"))

    assertThat(match).isNull()
  }
}
