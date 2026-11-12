// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.actions.addCommit

import com.intellij.dvcs.push.ui.VcsPushDialog
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsFullCommitDetails
import git4idea.GitStandardRemoteBranch
import git4idea.commands.Git
import git4idea.commands.GitCommand
import git4idea.commands.GitLineHandler
import git4idea.test.GitSingleRepoContext
import git4idea.test.TestFile
import git4idea.test.commitDetails
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.last
import git4idea.test.prepareRemoteRepo
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
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

    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(middleCommit), remoteBranch, this).execute()
    }

    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(remoteCommitMessages).isEqualTo("""
      Add feature
      initial
    """.trimIndent())
  }

  @Test
  fun `test cherry-pick multiple commits applies parents first`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")
    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }

    val first = file("feature.txt").create("first content\n").addCommit("First").details()
    val second = file("feature.txt").write("second content\n").addCommit("Second").details()

    dialogManager.onDialog(VcsPushDialog::class.java) {
      it.performOKAction()
      DialogWrapper.OK_EXIT_CODE
    }

    // The log hands the selection newest-first; the operation must apply parents first
    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(second, first), remoteBranch, this).execute()
    }

    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(remoteCommitMessages).isEqualTo("""
      Second
      First
      initial
    """.trimIndent())
  }

  @Test
  fun `test commits on the remote tip keep their hashes`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")
    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }

    val first = file("feature.txt").create("first content\n").addCommit("First").details()
    val second = file("feature.txt").write("second content\n").addCommit("Second").details()

    dialogManager.onDialog(VcsPushDialog::class.java) {
      it.performOKAction()
      DialogWrapper.OK_EXIT_CODE
    }

    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(second, first), remoteBranch, this).execute()
    }

    // The whole selection sits on the remote tip, so the push is a fast-forward and the hashes must not change
    val remoteTip = git("--git-dir '${remoteRepo}' rev-parse $remoteBranchRemoteRef")
    assertThat(remoteTip).isEqualTo(second.id.asString())
  }

  @Test
  fun `test selection with a gap applies ancestors first when commit times are equal`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")
    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }

    // Equal commit times leave the ancestry as the only way to order the selection
    val date = "2026-01-01T12:00:00+00:00"
    val first = addCommitWithDate(file("feature.txt").create("first content\n"), "First", date)
    addCommitWithDate(file("gap.txt").create("gap content\n"), "Gap", date)
    val third = addCommitWithDate(file("feature.txt").write("third content\n"), "Third", date)

    dialogManager.onDialog(VcsPushDialog::class.java) {
      it.performOKAction()
      DialogWrapper.OK_EXIT_CODE
    }

    // "Gap" is not selected; applying "Third" before "First" would report a false conflict on feature.txt
    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(third, first), remoteBranch, this).execute()
    }

    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(remoteCommitMessages).isEqualTo("""
      Third
      First
      initial
    """.trimIndent())
  }

  @Test
  @Suppress("NonAsciiCharacters")
  fun `test cherry-pick preserves non-UTF-8 commit message text`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")
    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }

    git("config i18n.commitEncoding ISO-8859-1")
    file("pre.txt").create("pre content\n").addCommit("pre-commit")

    val messageFile = Files.createTempFile("commit-message", ".txt")
    Files.write(messageFile, "Café".toByteArray(Charsets.ISO_8859_1))
    file("feature.txt").create("feature content\n").add()
    git("commit -F '$messageFile'")
    val commit = commitDetails(repo.last())

    dialogManager.onDialog(VcsPushDialog::class.java) {
      it.performOKAction()
      DialogWrapper.OK_EXIT_CODE
    }

    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(commit), remoteBranch, this).execute()
    }

    // The remote repository has no i18n settings, so `git log` re-encodes the message to UTF-8
    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(remoteCommitMessages).isEqualTo("""
      Café
      initial
    """.trimIndent())
  }

  private fun GitSingleRepoContext.addCommitWithDate(file: TestFile, message: String, date: String): VcsFullCommitDetails {
    file.add()
    val handler = GitLineHandler(project, repo.root, GitCommand.COMMIT)
    handler.addParameters("--date=$date", "-m", message)
    handler.addCustomEnvironmentVariable("GIT_COMMITTER_DATE", date)
    Git.getInstance().runCommand(handler).throwOnError()
    return file.details()
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

    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(middleCommit), remoteBranch, this).execute()
    }
    assertThat(pushDialogShown.get()).describedAs("Push dialog must be shown on the first run").isEqualTo(1)

    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(middleCommit), remoteBranch, this).execute()
    }
    assertThat(pushDialogShown.get()).describedAs("Push dialog must not be shown when there is nothing to add").isEqualTo(1)

    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(remoteCommitMessages).isEqualTo("""
      Add feature
      initial
    """.trimIndent())
  }

  @Test
  fun `test merge-base mode ignores a diverged remote-tracking ref`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")
    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }

    // Point the remote-tracking ref at a commit that is not on the current branch
    file("remote-only.txt").create("remote content\n").addCommit("Remote only")
    val remoteOnly = repo.last()
    git("reset --hard HEAD~1")
    git("update-ref refs/remotes/origin/$remoteBranchBareName $remoteOnly")
    repo.update()

    val first = file("feature.txt").create("first content\n").addCommit("First").details()
    val second = file("feature.txt").write("second content\n").addCommit("Second").details()

    dialogManager.onDialog(VcsPushDialog::class.java) {
      it.performOKAction()
      DialogWrapper.OK_EXIT_CODE
    }

    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(second, first), remoteBranch, this,
                                          GitAddCommitToRemoteBranchOperation.BaseMode.MERGE_BASE_WITH_HEAD).execute()
    }

    // The base is the merge base, not the diverged tracking ref, so the pushed chain is the current HEAD as is
    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(remoteCommitMessages).isEqualTo("""
      Second
      First
      initial
    """.trimIndent())
    val remoteTip = git("--git-dir '${remoteRepo}' rev-parse $remoteBranchRemoteRef")
    assertThat(remoteTip).isEqualTo(second.id.asString())
  }

  @Test
  fun `test merge-base mode pushes the current head minus the excluded commit`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")
    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }

    val first = file("feature.txt").create("first content\n").addCommit("First").details()
    file("gap.txt").create("gap content\n").addCommit("Gap").details()
    val third = file("feature.txt").write("third content\n").addCommit("Third").details()

    dialogManager.onDialog(VcsPushDialog::class.java) {
      it.performOKAction()
      DialogWrapper.OK_EXIT_CODE
    }

    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(third, first), remoteBranch, this,
                                          GitAddCommitToRemoteBranchOperation.BaseMode.MERGE_BASE_WITH_HEAD).execute()
    }

    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(remoteCommitMessages).isEqualTo("""
      Third
      First
      initial
    """.trimIndent())
    // "First" sits on the base, so it keeps its hash; "Third" is rebuilt over the excluded "Gap"
    val remoteParent = git("--git-dir '${remoteRepo}' rev-parse $remoteBranchRemoteRef~1")
    assertThat(remoteParent).isEqualTo(first.id.asString())
    val remoteTip = git("--git-dir '${remoteRepo}' rev-parse $remoteBranchRemoteRef")
    assertThat(remoteTip).isNotEqualTo(third.id.asString())
  }

  @Test
  fun `test merge-base mode does not fetch the remote branch`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    val remoteRepo = prepareRemoteRepo(repo)
    git("push origin HEAD:$remoteBranchBareName")
    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }
    val initial = repo.last()

    // Move the server ahead, then rewind the local branch and the tracking ref to make the clone stale
    file("server.txt").create("server content\n").addCommit("Server only")
    git("push origin HEAD:$remoteBranchBareName")
    git("reset --hard HEAD~1")
    git("update-ref refs/remotes/origin/$remoteBranchBareName $initial")
    repo.update()

    val local = file("local.txt").create("local content\n").addCommit("Local change").details()

    val pushDialogShown = AtomicInteger(0)
    dialogManager.onDialog(VcsPushDialog::class.java) {
      pushDialogShown.incrementAndGet()
      // The real push would not fast-forward, so only check that the dialog opens
      DialogWrapper.CANCEL_EXIT_CODE
    }

    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(local), remoteBranch, this,
                                          GitAddCommitToRemoteBranchOperation.BaseMode.MERGE_BASE_WITH_HEAD).execute()
    }

    assertThat(pushDialogShown.get()).describedAs("Push dialog must be shown").isEqualTo(1)
    // A fetch would move the tracking ref to the server tip
    val trackingRef = git("rev-parse refs/remotes/origin/$remoteBranchBareName")
    assertThat(trackingRef).isEqualTo(initial)
    val serverTip = git("--git-dir '${remoteRepo}' log -1 --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(serverTip).isEqualTo("Server only")
  }

  @Test
  fun `test reverted commits on the base apply parents first`(): Unit = with(context) {
    val remoteBranchBareName = "my-remote-branch"
    val remoteBranchRemoteRef = "refs/heads/$remoteBranchBareName"

    val remoteRepo = prepareRemoteRepo(repo)

    // The base reaches both selected commits, but the reverts removed their changes
    file("feature.txt").create("x content\n").addCommit("Set up feature.txt")
    val first = file("feature.txt").write("y content\n").addCommit("Change A").details()
    val second = file("feature.txt").write("z content\n").addCommit("Change B").details()
    git("revert --no-edit ${second.id.asString()}")
    git("revert --no-edit ${first.id.asString()}")

    git("push origin HEAD:$remoteBranchBareName")
    repo.update()
    val remoteBranch = repo.branches.remoteBranches
      .filterIsInstance<GitStandardRemoteBranch>()
      .first { it.nameForRemoteOperations == remoteBranchBareName }

    dialogManager.onDialog(VcsPushDialog::class.java) {
      it.performOKAction()
      DialogWrapper.OK_EXIT_CODE
    }

    // Applying "Change B" before "Change A" would report a false conflict on feature.txt
    timeoutRunBlocking {
      GitAddCommitToRemoteBranchOperation(project, repo, listOf(second, first), remoteBranch, this).execute()
    }

    val remoteContent = git("--git-dir '${remoteRepo}' show $remoteBranchRemoteRef:feature.txt")
    assertThat(remoteContent).isEqualTo("z content")
    val remoteCommitMessages = git("--git-dir '${remoteRepo}' log --pretty=format:%s $remoteBranchRemoteRef")
    assertThat(remoteCommitMessages).isEqualTo("""
      Change B
      Change A
      Revert "Change A"
      Revert "Change B"
      Change B
      Change A
      Set up feature.txt
      initial
    """.trimIndent())
  }
}
