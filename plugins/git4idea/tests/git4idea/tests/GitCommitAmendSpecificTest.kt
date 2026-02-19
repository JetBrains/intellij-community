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
import git4idea.log.refreshAndWait
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertCommitted
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
    exceptions.single() as GitAmendSpecificCommitSquasher.AmendSpecificCommitConflictException

    assertChangesWithRefresh {
      modified("a.txt")
    }

    assertEquals(oldHead, repo.last())
    assertEquals(file("a.txt").read(), updatedContent)
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
