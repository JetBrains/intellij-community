// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.VcsRoot
import com.intellij.openapi.vcs.changes.Change
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.commit.CommitToAmend
import com.intellij.vcs.commit.commitToAmend
import com.intellij.vcs.commit.commitWithoutChangesRoots
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.checkin.GitAmendSpecificCommitSquasher
import git4idea.i18n.GitBundle
import git4idea.log.refreshAndWait
import git4idea.rebase.GitSquashedCommitsMessage.canAutosquash
import git4idea.rebase.GitSquashedCommitsMessage.getSubject
import git4idea.test.GitSingleRepoContext
import git4idea.test.assertChangesWithRefresh
import git4idea.test.assertCommitted
import git4idea.test.assertLatestHistory
import git4idea.test.assertMessage
import git4idea.test.assertNoChanges
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.last
import git4idea.test.lastMessage
import git4idea.test.message
import git4idea.test.tac
import git4idea.test.tryCommit
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitCommitAmendSpecificTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test commit amend specific`(): Unit = with(context) {
    val initialContent = "initial content"
    tac("a.txt", initialContent)
    val targetHash = HashImpl.build(repo.last())
    val targetMessage = repo.lastMessage()
    tac("b.txt")

    val updatedContent = "updated content"
    overwrite("a.txt", updatedContent)

    val changes = assertChangesWithRefresh {
      modified("a.txt")
    }

    val newMessage = "new message\n"
    assertThat(amendSpecificCommit(targetHash, targetMessage, changes, newMessage)).isEmpty()

    assertNoChanges()
    assertMessage(newMessage, repo.message("HEAD~1"))

    repo.assertCommitted(1) {
      added("b.txt")
    }
    repo.assertCommitted(2) {
      added("a.txt", updatedContent)
    }
  }

  @Test
  fun `test commit amend specific without changes`(): Unit = with(context) {
    tac("a.txt")
    val targetHash = HashImpl.build(repo.last())
    val targetMessage = repo.lastMessage()
    tac("b.txt")

    val newMessage = "new message\n"
    assertThat(amendSpecificCommit(targetHash, targetMessage, emptyList(), newMessage)).isEmpty()

    assertMessage(newMessage, repo.message("HEAD~1"))
  }

  @Test
  fun `test commit amend specific with conflict`(): Unit = with(context) {
    val initialContent = "initial content"
    tac("a.txt", initialContent)
    val targetHash = HashImpl.build(repo.last())
    val targetMessage = repo.lastMessage()

    val commitedContent = "committed content"
    file("a.txt").write(commitedContent).addCommit("modify a")

    val updatedContent = "updated content"
    overwrite("a.txt", updatedContent)

    val changes = assertChangesWithRefresh {
      modified("a.txt")
    }

    val oldHead = repo.last()

    val newMessage = "new message\n"
    val exceptions = amendSpecificCommit(targetHash, targetMessage, changes, newMessage)
    val conflictException = exceptions.single() as GitAmendSpecificCommitSquasher.AmendSpecificCommitConflictException

    assertChangesWithRefresh {
      modified("a.txt")
    }

    assertThat(repo.last()).isEqualTo(oldHead)
    assertThat(file("a.txt").read()).isEqualTo(updatedContent)

    runBlocking {
      conflictException.resetToAmendCommit()
    }
    refresh()
    updateChangeListManager()

    repo.assertCommitted {
      modified("a.txt")
    }
    assertNoChanges()
    assertThat(canAutosquash(lastMessage(), setOf(getSubject(targetMessage)))).isTrue()
  }

  @Test
  fun `test commit amend specific target not in current branch`(): Unit = with(context) {
    val initialContent = "initial content"
    tac("a.txt", initialContent)
    val targetHash = HashImpl.build(repo.last())
    val targetMessage = repo.lastMessage()
    tac("b.txt")

    git("checkout --orphan orphan-branch") // create a branch without commits
    tac("c.txt")

    val updatedContent = "updated content"
    overwrite("c.txt", updatedContent)

    val changes = assertChangesWithRefresh {
      modified("c.txt")
    }

    val newMessage = "new message\n"
    val exception = amendSpecificCommit(targetHash, targetMessage, changes, newMessage).single()

    assertThat(exception.message).isEqualTo(GitBundle.message("git.commit.amend.specific.commit.not.found.error.message"))
  }

  @Test
  fun `test commit amend specific with fixup pair between commits`(): Unit = with(context) {
    val initialContent = "initial content"
    tac("a.txt", initialContent)
    val targetHash = HashImpl.build(repo.last())
    val targetMessage = repo.lastMessage()

    val baseContent = "base content"
    tac("b.txt", baseContent)
    val baseMessage = repo.lastMessage().trim()
    val fixupTargetSubject = getSubject(baseMessage)
    val fixupContent = "fixup content"
    val fixupMessage = "fixup! $fixupTargetSubject"
    file("b.txt").write(fixupContent).addCommit(fixupMessage)

    val updatedContent = "updated content"
    overwrite("a.txt", updatedContent)

    val changes = assertChangesWithRefresh {
      modified("a.txt")
    }

    val newMessage = "new message"
    assertThat(amendSpecificCommit(targetHash, targetMessage, changes, newMessage)).isEmpty()

    assertNoChanges()

    with(repo) {
      assertLatestHistory(fixupMessage, baseMessage, newMessage)

      assertCommitted(1) {
        modified("b.txt", baseContent, fixupContent)
      }
      assertCommitted(2) {
        added("b.txt", baseContent)
      }
      assertCommitted(3) {
        added("a.txt", updatedContent)
      }
    }
  }

  private fun GitSingleRepoContext.amendSpecificCommit(
    targetHash: Hash,
    targetMessage: String,
    changes: Collection<Change>,
    newMessage: String,
  ): List<VcsException> {
    commitContext.commitToAmend = CommitToAmend.Specific(targetHash, targetMessage)
    if (changes.isEmpty()) {
      commitContext.commitWithoutChangesRoots = listOf(VcsRoot(vcs, repo.root))
    }

    return runBlocking {
      coroutineToIndicator {
        val logData = runBlocking { VcsProjectLog.awaitLogIsReady(repo.project)?.dataManager }
        logData?.refreshAndWait(repo, true)
        tryCommit(changes, newMessage)
      }
    }.orEmpty()
  }
}
