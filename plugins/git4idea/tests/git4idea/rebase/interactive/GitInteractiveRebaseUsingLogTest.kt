// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.rebase.interactive

import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import git4idea.branch.GitRebaseParams
import git4idea.i18n.GitBundle
import git4idea.log.createLogDataIn
import git4idea.log.refreshAndWait
import git4idea.rebase.GitInteractiveRebaseEditorHandler
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseUtils
import git4idea.rebase.interactive.dialog.GitInteractiveRebaseDialog
import git4idea.test.GitSingleRepoTest
import kotlinx.coroutines.*

class GitInteractiveRebaseUsingLogTest : GitSingleRepoTest() {
  private lateinit var testCs: CoroutineScope
  private lateinit var logData: VcsLogData

  override fun setUp() {
    super.setUp()
    @Suppress("RAW_SCOPE_CREATION")
    testCs = CoroutineScope(SupervisorJob())
    logData = createLogDataIn(testCs, repo, logProvider)
  }

  override fun tearDown() {
    try {
      runBlocking {
        testCs.coroutineContext.job.cancelAndJoin()
      }
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test simple commits`() {
    val commit0 = file("firstFile.txt").create("").addCommit("0").details()
    build {
      1()
      2()
      3()
      4()
    }
    checkEntriesGeneration(commit0)
  }

  fun `test commit with trailing spaces`() {
    checkEntryGenerationForSingleCommitWithMessage {
      "Subject with trailing spaces  \n\nBody \nwith \nspaces."
    }
  }

  fun `test commit with tag in subject`() {
    checkEntryGenerationForSingleCommitWithMessage {
      "Subject with #tag trailing spaces"
    }
  }

  // IDEA-254399
  fun `test commit with spaces at the beginning`() {
    checkEntryGenerationForSingleCommitWithMessage {
      "     Commit with spaces at the beginning"
    }
  }

  fun `test commit with spaces at the end`() {
    checkEntryGenerationForSingleCommitWithMessage {
      "Commit with spaces at the end    "
    }
  }

  fun `test commit with huge length`() {
    checkEntryGenerationForSingleCommitWithMessage {
      buildString {
        repeat(1000) {
          append('a')
        }
      }
    }
  }

  fun `test rebase with merge commit`() {
    val firstFile = "firstFile.txt"
    val commit0 = file(firstFile).create("").addCommit("0").details()
    build {
      master {
        1()
        2()
      }
      feature {
        3()
        4()
      }
      master {
        5()
        6()
      }
    }
    git("checkout master")
    git("merge feature", true)
    build {
      master {
        7()
        8()
      }
    }
    assertExceptionDuringEntriesGeneration(commit0, CantRebaseUsingLogException.Reason.MERGE) {
      "We shouldn't generate entries if merge commit between HEAD and Rebase Base. Generated entries: $it"
    }
  }

  fun `test rebase with squash commit`() {
    val firstFile = "firstFile.txt"
    val commit0 = file(firstFile).create("").addCommit("0").details()
    build {
      master {
        1(commitMessage = "commit1")
        2(commitMessage = "commit2")
        3(commitMessage = "fixup! commit2")
        4(commitMessage = "commit3")
      }
    }
    assertExceptionDuringEntriesGeneration(commit0, CantRebaseUsingLogException.Reason.FIXUP_SQUASH) {
      "We shouldn't generate entries if squash!/fixup! prefix used. Generated entries: $it"
    }
  }

  // IJPL-156329
  fun `test incorrect git-rebase-todo file was generated`() {
    val commit = file("firstFile.txt").create("").addCommit("0").details()
    build {
        1()
        2()
    }
    logData.refreshAndWait(repo, true)
    updateChangeListManager()

    dialogManager.onDialog(GitInteractiveRebaseDialog::class.java) {
      git("reset HEAD~ --hard")
      DialogWrapper.OK_EXIT_CODE
    }

    interactivelyRebaseUsingLog(repo, commit, logData)

    assertErrorNotification("Rebase failed", GitBundle.message("rebase.using.log.couldnt.start.error"))
  }

  private fun getRebaseEntriesUsingGit(commit: VcsCommitMetadata): List<GitRebaseEntry> {
    lateinit var entriesGeneratedUsingGit: List<GitRebaseEntry>
    val editorHandler = object : GitInteractiveRebaseEditorHandler(project, repo.root) {
      override fun collectNewEntries(entries: List<GitRebaseEntry>): List<GitRebaseEntry> {
        entriesGeneratedUsingGit = entries
        return entries
      }
    }

    refresh()
    updateChangeListManager()

    val params = GitRebaseParams.editCommits(repo.vcs.version, commit.parents.first().asString(), editorHandler, false)
    GitRebaseUtils.rebase(repo.project, listOf(repo), params, EmptyProgressIndicator())
    return entriesGeneratedUsingGit
  }

  private fun checkEntriesGeneration(commit: VcsCommitMetadata) {
    logData.refreshAndWait(repo, true)
    val entriesGeneratedUsingLog = getEntriesUsingLog(repo, commit, logData)
    val entriesGeneratedUsingGit = getRebaseEntriesUsingGit(commit)
    assertTrue(entriesGeneratedUsingGit.isNotEmpty() && entriesGeneratedUsingLog.isNotEmpty())
    entriesGeneratedUsingLog.forEachIndexed { i, generatedEntry ->
      val realEntry = entriesGeneratedUsingGit[i]
      assertTrue("Generated entry: $generatedEntry, Real entry: $realEntry", generatedEntry.equalsWithReal(realEntry))
    }
  }

  private fun checkEntryGenerationForSingleCommitWithMessage(message: () -> String) {
    val commit = file("firstFile.txt").create("").addCommit(message()).details()
    checkEntriesGeneration(commit)
  }

  private fun assertExceptionDuringEntriesGeneration(
    commit: VcsCommitMetadata,
    reason: CantRebaseUsingLogException.Reason,
    failMessage: (entries: List<GitRebaseEntry>) -> String
  ) {
    logData.refreshAndWait(repo, true)
    try {
      val entries = getEntriesUsingLog(repo, commit, logData)
      fail(failMessage(entries))
    }
    catch (e: CantRebaseUsingLogException) {
      assertEquals(reason, e.reason)
    }
  }
}