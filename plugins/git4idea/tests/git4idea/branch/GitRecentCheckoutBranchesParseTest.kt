// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitLocalBranch
import git4idea.test.GitSingleRepoContext
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.checkout
import git4idea.test.checkoutNew
import git4idea.test.deleteBranch
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class GitRecentCheckoutBranchesParseTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test recent checkout branches collection`(): Unit = with(context) {
    val expected = listOf("test/feature3", "feature2", "feature1")
    expected.reversed().forEach { branch -> repo.checkoutNew(branch) }
    waitForRepoUpdate()

    val branchNames = repo.branches.recentCheckoutBranches.map(GitLocalBranch::name)
    assertThat(branchNames).containsExactlyElementsOf(expected)
  }

  @Test
  fun `test recent checkout branches collection no duplicates`(): Unit = with(context) {
    val initialBranch = "master"
    val expected = mutableListOf("test/feature3", "feature2", "feature1")
    expected.reversed().forEach { branch -> repo.checkoutNew(branch) }
    repo.checkout(initialBranch)
    expected.reversed().forEach { repo.deleteBranch(it) }
    expected.reversed().forEach { branch -> repo.checkoutNew(branch) } // make reflog entries duplication
    expected += initialBranch // previously initialBranch was explicitly checkout with reflog entry
    waitForRepoUpdate()

    val branchNames = repo.branches.recentCheckoutBranches.map(GitLocalBranch::name)
    assertThat(branchNames).containsExactlyElementsOf(expected)
  }

  @Test
  fun `test recent checkout branches collection with branch remove`(): Unit = with(context) {
    val expected = listOf("test/feature3", "feature2", "feature1")
    expected.reversed().forEach { branch -> repo.checkoutNew(branch) }
    waitForRepoUpdate()

    var branchNames = repo.branches.recentCheckoutBranches.map(GitLocalBranch::name)
    assertThat(branchNames).containsExactlyElementsOf(expected)

    repo.deleteBranch("feature1")
    waitForRepoUpdate()

    branchNames = repo.branches.recentCheckoutBranches.map(GitLocalBranch::name)
    assertThat(branchNames).containsExactlyElementsOf(expected.dropLast(1))
  }

  private fun waitForRepoUpdate(): Unit = with(context) {
    repo.update()
    ChangeListManagerImpl.getInstanceImpl(project).waitEverythingDoneInTestMode()
  }
}
