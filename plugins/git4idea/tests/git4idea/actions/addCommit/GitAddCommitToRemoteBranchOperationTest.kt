// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.addCommit

import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.junit5.TestApplication
import git4idea.GitStandardRemoteBranch
import git4idea.test.GitSingleRepoContext
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.prepareRemoteRepo
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class GitAddCommitToRemoteBranchOperationTest {
  private val fixture = gitSingleRepoContextFixture(hasRemoteGitOperation = true)
  private val context: GitSingleRepoContext get() = fixture.get()

  @Test
  fun `test cherry-pick single commit to remote branch`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    // Set up remote repository
    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")

    // Verify ref exists in the remote
    val remoteRefs = git("--git-dir '${remoteRepo}' show-ref --verify --quiet $remoteBranchRemoteRef")
    assertThat(remoteRefs).describedAs("Remote branch ref should exist").isEmpty()

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
    assertThat(remoteCommitMessages).isEqualTo("""
      Add feature
      initial
    """.trimIndent())
  }

  @Test
  fun `test cherry-pick same commit twice does not create empty commit`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")
    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }

    file("some.txt").create("pre content\n").addCommit("pre-commit").details()
    val middleCommit = file("feature.txt").create("feature content\n").addCommit("Add feature").details()
    file("feature.txt").write("post content\n").addCommit("post-commit").details()

    val pushDialogShown = AtomicInteger(0)
    dialogManager.onDialog(VcsPushDialog::class.java) {
      pushDialogShown.incrementAndGet()
      it.performOKAction()
      DialogWrapper.OK_EXIT_CODE
    }

    runBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(middleCommit), remoteBranch, this).execute()
    }
    assertThat(pushDialogShown.get()).describedAs("Push dialog must be shown on the first run").isEqualTo(1)

    runBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(middleCommit), remoteBranch, this).execute()
    }
    assertThat(pushDialogShown.get()).describedAs("Push dialog must not be shown when there is nothing to add").isEqualTo(1)

    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(remoteCommitMessages).isEqualTo("""
      Add feature
      initial
    """.trimIndent())
  }
}
