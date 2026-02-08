// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.remoteApi

import com.intellij.dvcs.repo.repositoryId
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.vcs.Executor.cd
import com.intellij.platform.project.projectId
import com.intellij.vcs.git.rpc.GitOperationsApi
import git4idea.GitStandardLocalBranch
import git4idea.repo.GitRepository
import git4idea.test.GitScenarios.unmergedFiles
import git4idea.test.assertCurrentBranch
import git4idea.test.assertCurrentRevision
import git4idea.test.assertLatestSubjects
import git4idea.test.checkout
import git4idea.test.git
import git4idea.test.last
import git4idea.test.resolveConflicts
import git4idea.test.tac
import git4idea.update.GitMultiRepoUpdateBaseTest
import kotlinx.coroutines.runBlocking

class GitCheckoutAndUpdateTest : GitMultiRepoUpdateBaseTest() {
  override fun setUp() {
    super.setUp()

    cd(bro)
    git("checkout -b feature")
    tac("a.txt")
    git("push origin feature")

    cd(bromunity)
    git("checkout -b feature")
    tac("community_a.txt")
    git("push origin feature")

    listOf(repository, community).forEach {
      with(it) {
        git("fetch origin feature")
        git("checkout -b feature --track origin/feature")
        checkout("master")
      }
    }
  }

  fun `test checkoutAndUpdate on multiple repositories with same branch name`() {
    cd(bro)
    val newHashInRepository = tac("b.txt")
    git("push origin feature")

    cd(bromunity)
    val newHashInCommunity = tac("community_b.txt")
    git("push origin feature")

    checkoutAndUpdateBranch(listOf(repository, community), "feature")

    repository.assertCurrentBranch("feature")
    community.assertCurrentBranch("feature")

    repository.assertCurrentRevision(newHashInRepository)
    community.assertCurrentRevision(newHashInCommunity)
  }

  fun `test partial checkout failure prompts rollback and rolls back successful repositories without updating them`() {
    community.checkout("feature")
    val oldHash = community.last()
    community.checkout("master")

    cd(bromunity)
    tac("community_b.txt")
    git("push origin feature")

    unmergedFiles(repository)

    // Answer YES to rollback
    dialogManager.onMessage {
      Messages.YES
    }

    checkoutAndUpdateBranch(listOf(repository, community), "feature")

    repository.assertCurrentBranch("master")
    community.assertCurrentBranch("master")

    community.checkout("feature")
    community.assertCurrentRevision(oldHash)
  }

  fun `test partial checkout failure without rollback updates successful repository`() {
    repository.checkout("feature")
    val oldHashInRepository = repository.last()
    repository.checkout("master")

    cd(bro)
    tac("b.txt")
    git("push origin feature")

    cd(bromunity)
    val newHashInCommunity = tac("community_b.txt")
    git("push origin feature")

    unmergedFiles(repository)

    // Answer NO to rollback
    dialogManager.onMessage {
      Messages.NO
    }
    // Update prompts to resolve conflicts in the repository
    // even though we update only the community
    vcsHelper.onMerge {
      repository.resolveConflicts()
    }

    checkoutAndUpdateBranch(listOf(repository, community), "feature")

    repository.assertCurrentBranch("master")

    repository.checkout("feature")
    repository.assertCurrentRevision(oldHashInRepository)

    community.assertCurrentBranch("feature")
    community.assertCurrentRevision(newHashInCommunity)
  }

  fun `test checkoutAndUpdate performs non fast-forward update via merge when histories diverged`() {
    repository.checkout("feature")
    tac("local.txt")
    repository.checkout("master")

    cd(bro)
    checkout("feature")
    tac("remote.txt")
    git("push origin feature")

    checkoutAndUpdateBranch(listOf(repository, community), "feature")

    repository.assertCurrentBranch("feature")
    repository.assertLatestSubjects("Merge remote-tracking branch 'origin/feature' into feature")

    community.assertCurrentBranch("feature")
  }

  private fun checkoutAndUpdateBranch(
    repositories: List<GitRepository>,
    branchName: String,
  ) {
    runBlocking {
      val projectId = project.projectId()
      val repositoryIds = repositories.map { it.repositoryId() }
      GitOperationsApi.getInstance()
        .checkoutAndUpdate(projectId, repositoryIds, GitStandardLocalBranch(branchName))
        .await()
    }
  }
}
