// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.addCommit

import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.openapi.ui.DialogWrapper
import git4idea.GitStandardRemoteBranch
import git4idea.test.GitSingleRepoTest
import kotlinx.coroutines.runBlocking

class GitAddCommitToRemoteBranchOperationTest : GitSingleRepoTest() {
  override fun hasRemoteGitOperation() = true

  fun `test cherry-pick single commit to remote branch`() {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    // Set up remote repository
    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")

    // Verify ref exists in the remote
    val remoteRefs = git("--git-dir '${remoteRepo}' show-ref --verify --quiet $remoteBranchRemoteRef")
    assertTrue("Remote branch ref should exist", remoteRefs.isEmpty())

    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }

    // Create a commit to cherry-pick (on a separate branch to simulate the use case)
    file("some.txt").create("pre content\n").addCommit("pre-commit").details()
    val middleCommit = file("feature.txt").create("feature content\n").addCommit("Add feature").details()
    file("feature.txt").write("post content\n").addCommit("post-commit").details()

    dialogManager.onDialog(VcsPushDialog::class.java) {
      it.performOKAction()
      DialogWrapper.OK_EXIT_CODE
    }

    runBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(middleCommit), remoteBranch, this).execute()
    }

    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertEquals("""
      Add feature
      initial
    """.trimIndent(), remoteCommitMessages)
  }
}
