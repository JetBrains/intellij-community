// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.progress.coroutineToIndicator
import com.intellij.openapi.vcs.Executor.overwrite
import com.intellij.openapi.vcs.VcsException
import com.intellij.openapi.vcs.changes.Change
import com.intellij.vcs.commit.CommitToAmend
import com.intellij.vcs.commit.commitToAmend
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.impl.HashImpl
import com.intellij.vcs.log.impl.VcsProjectLog
import git4idea.checkin.GitAmendSpecificCommitSquasher
import git4idea.i18n.GitBundle
import git4idea.log.refreshAndWait
import git4idea.rebase.GitSquashedCommitsMessage.canAutosquash
import git4idea.rebase.GitSquashedCommitsMessage.getSubject
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertCommitted
import git4idea.test.assertLatestHistory
import git4idea.test.assertMessage
import git4idea.test.last
import git4idea.test.lastMessage
import git4idea.test.message
import git4idea.test.tac
import kotlinx.coroutines.runBlocking

internal class GitCommitAmendSpecificTest : GitSingleRepoTest() {
  fun `test commit amend specific`() {
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
    val exceptions = amendSpecificCommit(targetHash, targetMessage, changes, newMessage)
    assertEmpty(exceptions)

    assertNoChanges()
    assertMessage(newMessage, repo.message("HEAD~1"))

    repo.assertCommitted(1) {
      added("b.txt")
    }
    repo.assertCommitted(2) {
      added("a.txt", updatedContent)
    }
  }

  fun `test commit amend specific with conflict`() {
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

    assertEquals(oldHead, repo.last())
    assertEquals(file("a.txt").read(), updatedContent)

    runBlocking {
      conflictException.resetToAmendCommit()
    }
    refresh()
    updateChangeListManager()

    repo.assertCommitted {
      modified("a.txt")
    }
    assertNoChanges()
    assertTrue(canAutosquash(lastMessage(), setOf(getSubject(targetMessage))))
  }

  fun `test commit amend specific target not in current branch`() {
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

    assertEquals(GitBundle.message("git.commit.amend.specific.commit.not.found.error.message"), exception.message)
  }

  fun `test commit amend specific with fixup pair between commits`() {
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
    val exceptions = amendSpecificCommit(targetHash, targetMessage, changes, newMessage)
    assertEmpty(exceptions)

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

  private fun amendSpecificCommit(
    targetHash: Hash,
    targetMessage: String,
    changes: Collection<Change>,
    newMessage: String,
  ): List<VcsException> {
    commitContext.commitToAmend = CommitToAmend.Specific(targetHash, targetMessage)

    return runBlocking {
      coroutineToIndicator {
        val logData = runBlocking { VcsProjectLog.awaitLogIsReady(repo.project)?.dataManager }
        logData?.refreshAndWait(repo, true)
        tryCommit(changes, newMessage)
      }
    }.orEmpty()
  }
}
