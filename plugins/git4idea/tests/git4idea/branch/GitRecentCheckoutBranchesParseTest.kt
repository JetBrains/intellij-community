// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.branch

import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import git4idea.GitLocalBranch
import git4idea.test.GitSingleRepoTest
import git4idea.test.checkout
import git4idea.test.checkoutNew
import git4idea.test.deleteBranch

class GitRecentCheckoutBranchesParseTest : GitSingleRepoTest() {

  fun `test recent checkout branches collection`() {
    val expected = listOf("test/feature3", "feature2", "feature1")
    expected.reversed().forEach { branch -> repo.checkoutNew(branch) }
    waitForRepoUpdate()

    val branchNames = repo.branches.recentCheckoutBranches.map(GitLocalBranch::name)
    assertOrderedEquals(branchNames, expected)
  }

  fun `test recent checkout branches collection no duplicates`() {
    val initialBranch = "master"
    val expected = mutableListOf("test/feature3", "feature2", "feature1")
    expected.reversed().forEach { branch -> repo.checkoutNew(branch) }
    repo.checkout(initialBranch)
    expected.reversed().forEach { repo.deleteBranch(it) }
    expected.reversed().forEach { branch -> repo.checkoutNew(branch) } // make reflog entries duplication
    expected += initialBranch // previously initialBranch was explicitly checkout with reflog entry
    waitForRepoUpdate()

    val branchNames = repo.branches.recentCheckoutBranches.map(GitLocalBranch::name)
    assertOrderedEquals(branchNames, expected)
  }

  fun `test recent checkout branches collection with branch remove`() {
    val expected = listOf("test/feature3", "feature2", "feature1")
    expected.reversed().forEach { branch -> repo.checkoutNew(branch) }
    waitForRepoUpdate()

    var branchNames = repo.branches.recentCheckoutBranches.map(GitLocalBranch::name)
    assertOrderedEquals(branchNames, expected)

    repo.deleteBranch("feature1")
    waitForRepoUpdate()

    branchNames = repo.branches.recentCheckoutBranches.map(GitLocalBranch::name)
    assertOrderedEquals(branchNames, expected.dropLast(1))
  }

  private fun waitForRepoUpdate() {
    repo.update()
    ChangeListManagerImpl.getInstanceImpl(project).waitEverythingDoneInTestMode()
  }
}
