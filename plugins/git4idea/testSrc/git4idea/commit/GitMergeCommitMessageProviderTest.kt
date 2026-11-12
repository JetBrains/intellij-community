// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.util.LineSeparator
import com.intellij.vcs.commit.CommitMessage
import com.intellij.vcs.commit.DefaultCommitMessagePolicy
import com.intellij.vcs.test.refresh
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.GitUtil
import git4idea.branch.GitRebaseParams
import git4idea.config.GitConfigUtil
import git4idea.test.GitSingleRepoContext
import git4idea.test.TestGitImpl
import git4idea.test.checkout
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.times

@TestApplication
class GitMergeCommitMessageProviderTest {
  private val fixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = fixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  private val mergeMessageProvider = GitMergeCommitMessagePolicy()
  private val commitMessageControllerMock = Mockito.mock<DefaultCommitMessagePolicy.CommitMessageController>()

  @BeforeEach
  fun setUp(): Unit = with(context) {
    mergeMessageProvider.initAsyncMessageUpdate(project, commitMessageControllerMock, disposable)
  }

  @AfterEach
  fun tearDown() {
    Mockito.verifyNoMoreInteractions(commitMessageControllerMock)
  }

  @Test
  fun `test merge message set and reset based on MERGE_MSG`(): Unit = with(context) {
    val newBranch = "new-branch"
    prepareConflict(newBranch)
    git("merge $newBranch", ignoreExitCode = true)

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()

    val message = checkNotNull(mergeMessageProvider.getMessage(project))
    assertThat(message.disposable).isTrue()
    assertThat(message.text).startsWith("Merge branch 'new-branch'")

    verifyCommitMessageSet(message)

    // Message is reset once merge is canceled
    git("merge --abort")
    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()

    assertThat(mergeMessageProvider.getMessage(project)).isNull()
    Mockito.verify(commitMessageControllerMock, times(1)).tryRestoreCommitMessage()
  }

  @Test
  fun `test merge message set with proper comment char during rebase`(): Unit = with(context) {
    val commentChar = "!"
    GitConfigUtil.setValue(project, repo.root, GitConfigUtil.CORE_COMMENT_CHAR, commentChar)

    val file = file("test")
    file.create("initial\n").addCommit("initial")
    file.write("more\n").addCommit("more")

    git.setInteractiveRebaseEditor(
      TestGitImpl.InteractiveRebaseEditor({
                                            it.lines().mapIndexed { i, s ->
                                              if (i == 0) s.replace("pick", "drop") else s
                                            }.joinToString(LineSeparator.getSystemLineSeparator().separatorString)
                                          }, null))
    git.rebase(repo, GitRebaseParams(vcs.version, null, null, "HEAD~2", true, false))

    refresh()
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()

    val message = checkNotNull(mergeMessageProvider.getMessage(project))
    assertThat(message.disposable).isTrue()
    assertThat(message.text.lines()).noneMatch { it.startsWith(GitUtil.COMMENT_CHAR) }
    assertThat(message.text).contains("$commentChar Conflicts:")

    verifyCommitMessageSet(message)
  }


  @Test
  fun `test merge message based on MERGE_MSG + SQUASH_MSG`(): Unit = with(context) {
    val newBranch = "new-branch"
    prepareConflict(newBranch)
    git("merge --squash $newBranch", ignoreExitCode = true)

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()

    val message = checkNotNull(mergeMessageProvider.getMessage(project))
    assertThat(message.disposable).isTrue()
    assertThat(message.text).startsWith("Squashed commit of the following:")
    assertThat(message.text).contains("# Conflicts:")

    verifyCommitMessageSet(message)
  }

  private fun verifyCommitMessageSet(message: CommitMessage) {
    Mockito.verify(commitMessageControllerMock, times(1)).setCommitMessage(message)
  }

  private fun GitSingleRepoContext.prepareConflict(otherBranch: String) {
    val file = file("test")
    file.create("initial\n").addCommit("initial")
    git("checkout -b $otherBranch")
    file.write("new").addCommit("new")
    checkout("master")
    file.write("newer").addCommit("new")
  }
}
