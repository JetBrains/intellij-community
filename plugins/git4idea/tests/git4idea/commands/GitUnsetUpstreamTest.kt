// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commands

import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitSingleRepoContext
import git4idea.test.checkoutNew
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.prepareRemoteRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class GitUnsetUpstreamTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test unsetUpstream removes upstream branch reference`(): Unit = with(context) {
    prepareRemoteRepo(repo)

    repo.checkoutNew("feature")
    git("push -u origin feature")

    repo.update()
    assertThat(getUpstream()).isEqualTo("origin/feature")

    val result = GitImpl().unsetUpstream(repo, "feature")
    assertThat(result.success()).isTrue()

    repo.update()
    assertThat(getUpstream()).isNull()
  }

  @Test
  fun `test unsetUpstream fail when branch is not tracking`(): Unit = with(context) {
    git("checkout -b feature")
    val result = GitImpl().unsetUpstream(repo, "feature")
    assertThat(result.success()).isFalse()
  }

  @Test
  fun `test unsetUpstream fail with non-existent branch`(): Unit = with(context) {
    assertThat(GitImpl().unsetUpstream(repo, "feature").success()).isFalse()
  }

  private fun getUpstream(): String? {
    return context.repo.getBranchTrackInfo("feature")?.remoteBranch?.nameForLocalOperations
  }
}