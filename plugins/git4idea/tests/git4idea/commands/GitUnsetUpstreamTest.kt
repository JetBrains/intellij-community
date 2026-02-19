// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import git4idea.test.GitSingleRepoTest
import git4idea.test.checkoutNew

class GitUnsetUpstreamTest : GitSingleRepoTest() {
  fun `test unsetUpstream removes upstream branch reference`() {
    prepareRemoteRepo(repo)

    repo.checkoutNew("feature")
    git("push -u origin feature")

    repo.update()
    assertEquals("origin/feature", getUpstream("feature"))

    val result = GitImpl().unsetUpstream(repo, "feature")
    assertTrue(result.success())

    repo.update()
    assertNull(getUpstream("feature"))
  }

  fun `test unsetUpstream fail when branch is not tracking`() {
    git("checkout -b feature")
    val result = GitImpl().unsetUpstream(repo, "feature")
    assertFalse(result.success())
  }

  fun `test unsetUpstream fail with non-existent branch`() {
    val result = GitImpl().unsetUpstream(repo, "feature")
    assertFalse(result.success())
  }

  private fun getUpstream(branch: String): String? {
    return repo.getBranchTrackInfo(branch)?.remoteBranch?.nameForLocalOperations
  }
}