// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.commit

import com.intellij.openapi.vfs.VfsUtil
import com.intellij.util.LineSeparator
import com.intellij.vcs.commit.CommitMessage
import com.intellij.vcs.commit.DefaultCommitMessagePolicy
import com.intellij.vfs.AsyncVfsEventsPostProcessorImpl
import git4idea.GitUtil
import git4idea.branch.GitRebaseParams
import git4idea.config.GitConfigUtil
import git4idea.test.GitSingleRepoTest
import git4idea.test.TestGitImpl
import git4idea.test.checkout
import org.mockito.Mockito
import org.mockito.Mockito.times
import kotlin.text.lines

class GitMergeCommitMessageProviderTest: GitSingleRepoTest() {
  private val mergeMessageProvider = GitMergeCommitMessagePolicy()
  private val commitMessageControllerMock = Mockito.mock<DefaultCommitMessagePolicy.CommitMessageController>()

  override fun setUp() {
    super.setUp()
    mergeMessageProvider.initAsyncMessageUpdate(project, commitMessageControllerMock, project)
  }

  override fun tearDown() {
    try {
      Mockito.verifyNoMoreInteractions(commitMessageControllerMock)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test merge message set and reset based on MERGE_MSG`() {
    val newBranch = "new-branch"
    prepareConflict(newBranch)
    git("merge $newBranch", ignoreExitCode = true)

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()

    val message = checkNotNull(mergeMessageProvider.getMessage(project))
    assertTrue(message.disposable)
    assertTrue(message.text, message.text.startsWith("Merge branch 'new-branch'"))

    verifyCommitMessageSet(message)

    // Message is reset once merge is canceled
    git("merge --abort")
    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()

    assertNull(mergeMessageProvider.getMessage(project))
    Mockito.verify(commitMessageControllerMock, times(1)).tryRestoreCommitMessage()
  }

  fun `test merge message set with proper comment char during rebase`() {
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
    assertTrue(message.disposable)
    assertTrue(message.text.lines().none { it.startsWith(GitUtil.COMMENT_CHAR) })
    assertTrue(message.text, message.text.contains("$commentChar Conflicts:"))

    verifyCommitMessageSet(message)
  }


  fun `test merge message based on MERGE_MSG + SQUASH_MSG`() {
    val newBranch = "new-branch"
    prepareConflict(newBranch)
    git("merge --squash $newBranch", ignoreExitCode = true)

    VfsUtil.markDirtyAndRefresh(false, true, true, projectRoot)
    AsyncVfsEventsPostProcessorImpl.waitEventsProcessed()

    val message = checkNotNull(mergeMessageProvider.getMessage(project))
    assertTrue(message.disposable)
    assertTrue(message.text, message.text.startsWith("Squashed commit of the following:"))
    assertTrue(message.text, message.text.contains("# Conflicts:"))

    verifyCommitMessageSet(message)
  }

  private fun verifyCommitMessageSet(message: CommitMessage) {
    Mockito.verify(commitMessageControllerMock, times(1)).setCommitMessage(message)
  }

  private fun prepareConflict(otherBranch: String) {
    val file = file("test")
    file.create("initial\n").addCommit("initial")
    git("checkout -b $otherBranch")
    file.write("new").addCommit("new")
    checkout("master")
    file.write("newer").addCommit("new")
  }
}
