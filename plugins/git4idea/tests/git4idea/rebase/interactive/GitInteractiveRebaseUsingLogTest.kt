// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.rebase.interactive

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.vcs.log.VcsCommitMetadata
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.test.assertErrorNotification
import com.intellij.vcs.test.refresh
import com.intellij.vcs.test.updateChangeListManager
import git4idea.branch.GitRebaseParams
import git4idea.i18n.GitBundle
import git4idea.log.createLogDataIn
import git4idea.log.refreshAndWait
import git4idea.rebase.GitInteractiveRebaseEditorHandler
import git4idea.rebase.GitRebaseEntry
import git4idea.rebase.GitRebaseUtils
import git4idea.rebase.interactive.dialog.GitInteractiveRebaseDialog
import git4idea.rebase.log.GetEntriesUsingLogResult
import git4idea.rebase.log.GitInteractiveRebaseEntriesProvider
import git4idea.test.GitSingleRepoContext
import git4idea.test.build
import git4idea.test.file
import git4idea.test.git
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.runUnderProgress
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.job
import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.fail
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test


@TestApplication
internal class GitInteractiveRebaseUsingLogTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  private lateinit var testCs: CoroutineScope
  private lateinit var logData: VcsLogData

  @BeforeEach
  fun setUp() {
    @Suppress("RAW_SCOPE_CREATION")
    testCs = CoroutineScope(SupervisorJob())
    logData = createLogDataIn(testCs, context.repo, context.logProvider)
  }

  @AfterEach
  fun tearDown() {
    runBlocking {
      testCs.coroutineContext.job.cancelAndJoin()
    }
  }

  @Test
  fun `test simple commits`(): Unit = with(context) {
    val commit0 = file("firstFile.txt").create("").addCommit("0").details()
    build {
      1()
      2()
      3()
      4()
    }
    checkEntriesGeneration(commit0)
  }

  @Test
  fun `test commit with trailing spaces`(): Unit = with(context) {
    checkEntryGenerationForSingleCommitWithMessage {
      "Subject with trailing spaces  \n\nBody \nwith \nspaces."
    }
  }

  @Test
  fun `test commit with tag in subject`(): Unit = with(context) {
    checkEntryGenerationForSingleCommitWithMessage {
      "Subject with #tag trailing spaces"
    }
  }

  // IDEA-254399
  @Test
  fun `test commit with spaces at the beginning`(): Unit = with(context) {
    checkEntryGenerationForSingleCommitWithMessage {
      "     Commit with spaces at the beginning"
    }
  }

  @Test
  fun `test commit with spaces at the end`(): Unit = with(context) {
    checkEntryGenerationForSingleCommitWithMessage {
      "Commit with spaces at the end    "
    }
  }

  @Test
  fun `test commit with huge length`(): Unit = with(context) {
    checkEntryGenerationForSingleCommitWithMessage {
      buildString {
        repeat(1000) {
          append('a')
        }
      }
    }
  }

  @Test
  fun `test rebase with merge commit`(): Unit = with(context) {
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
    assertFailureDuringEntriesGeneration(commit0, GetEntriesUsingLogResult.FailureReason.MERGE) {
      "We shouldn't generate entries if merge commit between HEAD and Rebase Base. Generated entries: $it"
    }
  }

  @Test
  fun `test rebase with squash commit`(): Unit = with(context) {
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
    assertFailureDuringEntriesGeneration(commit0, GetEntriesUsingLogResult.FailureReason.FIXUP_SQUASH) {
      "We shouldn't generate entries if squash!/fixup! prefix used. Generated entries: $it"
    }
  }

  // IJPL-156329
  @Test
  fun `test incorrect git-rebase-todo file was generated`(): Unit = with(context) {
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

    runBlocking { interactivelyRebaseUsingLog(repo, commit, logData) }

    assertErrorNotification("Rebase failed", GitBundle.message("rebase.using.log.couldnt.start.error"))
  }

  // "Interactive Rebase from Here" collects commits from the VCS log (GitInteractiveRebaseEntriesProvider). While the
  // post-commit log refresh is still running, a just-made commit is already shown (via the overlay pack) but the
  // traversable data pack (VcsLogData.graphData) does not contain it yet, so a naive traversal would silently omit it.
  // Reproduces that state deterministically: refresh the log, then advance the real HEAD without refreshing the log,
  // so the data pack is an older-but-valid pack that does not contain the current HEAD.
  @Test
  fun `test entries generated from a stale log omit the just-added commit`(): Unit = with(context) {
    val base = file("firstFile.txt").create("").addCommit("0").details()
    build {
      1()
      2()
    }
    logData.refreshAndWait(repo, true) // data pack now contains 0..2

    // A commit lands and GitRepository catches up (as it would right after an IDE commit),
    // but the VCS log data pack has NOT been refreshed yet -> same state as "action triggered during loading".
    val justAdded = file("secondFile.txt").create("content").addCommit("3").details()
    repo.update()
    assertThat(repo.currentRevision).describedAs("Sanity: the real repo HEAD must be the just-added commit")
      .isEqualTo(justAdded.id.asString())

    val entries = runBlocking {
      repo.project.service<GitInteractiveRebaseEntriesProvider>().tryGetEntriesForDialog(repo, base, logData)
    }

    // The generator must not silently drop the just-added commit: it must bail out (null -> the action falls back to
    // git-native generation) because the data pack does not contain the current HEAD.
    val newHash = justAdded.id.asString()
    val includesNew = entries?.any { it.commit.startsWith(newHash) || newHash.startsWith(it.commit) } == true
    assertThat(entries == null || includesNew)
      .describedAs(
        "Interactive rebase built from a stale log must not silently drop the just-added commit ($newHash); " +
        "expected a fallback (null) or entries including it, but got ${entries?.map { it.commit }}"
      ).isTrue()
  }

  // Same staleness as above, but through the commit-editing entry point used by Drop/Squash. Verified as an A/B:
  // while the data pack is behind the real HEAD the generator must bail out (null, so those operations fall back to
  // their Git-native path); once the data pack catches up, the same call succeeds and includes the new commit.
  @Test
  fun `test commit-editing entries fall back when the data pack is behind the repository head`(): Unit = with(context) {
    val base = file("firstFile.txt").create("").addCommit("0").details()
    build {
      1()
      2()
    }
    logData.refreshAndWait(repo, true)

    val justAdded = file("secondFile.txt").create("content").addCommit("3").details()
    repo.update()
    assertThat(repo.currentRevision)
      .describedAs("Sanity: the real repo HEAD must be the just-added commit")
      .isEqualTo(justAdded.id.asString())

    val provider = repo.project.service<GitInteractiveRebaseEntriesProvider>()
    val newHash = justAdded.id.asString()

    val staleEntries = runBlocking { provider.tryGetEntriesForCommitEditing(repo, base, logData) }
    assertThat(staleEntries == null || staleEntries.any { it.commit.startsWith(newHash) || newHash.startsWith(it.commit) })
      .describedAs("Commit-editing entries built from a stale data pack must not silently drop the current HEAD ($newHash); "
                   + "expected a fallback (null) or entries including it, but got ${staleEntries?.map { it.commit }}"
      ).isTrue()

    // Control: once the data pack catches up, the same call succeeds and includes the just-added commit.
    logData.refreshAndWait(repo, true)
    val freshEntries = runBlocking { provider.tryGetEntriesForCommitEditing(repo, base, logData) }
    assertThat(freshEntries)
      .describedAs("Commit-editing entries must be generated once the data pack contains the HEAD")
      .isNotNull()
    assertThat(freshEntries?.any { it.commit.startsWith(newHash) || newHash.startsWith(it.commit) })
      .describedAs("Entries must include the current HEAD $newHash (got ${freshEntries!!.map { it.commit }})").isTrue()
  }

  private fun GitSingleRepoContext.getRebaseEntriesUsingGit(commit: VcsCommitMetadata): List<GitRebaseEntry> {
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
    runUnderProgress { indicator ->
      GitRebaseUtils.rebase(repo.project, listOf(repo), params, indicator)
    }
    return entriesGeneratedUsingGit
  }

  private fun GitSingleRepoContext.checkEntriesGeneration(commit: VcsCommitMetadata) {
    logData.refreshAndWait(repo, true)
    repo.update() // keep GitRepository's cached HEAD in sync with the refreshed log, as it always is in production
    val entriesGeneratedUsingLog = runBlocking {
      repo.project.service<GitInteractiveRebaseEntriesProvider>()
        .tryGetEntriesForDialog(repo, commit, logData)
    } ?: error("Failed to get entries")
    val entriesGeneratedUsingGit = getRebaseEntriesUsingGit(commit)
    assertThat(entriesGeneratedUsingGit).isNotEmpty()
    assertThat(entriesGeneratedUsingLog).isNotEmpty()
    entriesGeneratedUsingLog.forEachIndexed { i, generatedEntry ->
      val realEntry = entriesGeneratedUsingGit[i]
      assertThat(generatedEntry.equalsWithReal(realEntry))
        .describedAs("Generated entry: $generatedEntry, Real entry: $realEntry")
        .isTrue()
    }
  }

  private fun GitSingleRepoContext.checkEntryGenerationForSingleCommitWithMessage(message: () -> String) {
    val commit = file("firstFile.txt").create("").addCommit(message()).details()
    checkEntriesGeneration(commit)
  }

  private fun GitSingleRepoContext.assertFailureDuringEntriesGeneration(
    commit: VcsCommitMetadata,
    reason: GetEntriesUsingLogResult.FailureReason,
    failMessage: (entries: List<GitRebaseEntry>) -> String,
  ) {
    logData.refreshAndWait(repo, true)
    val result = runBlocking {
      repo.project.service<GitInteractiveRebaseEntriesProvider>().getEntriesForDialog(repo, commit, logData)
    }

    when (result) {
      is GetEntriesUsingLogResult.Failure -> assertThat(result.reason).isEqualTo(reason)
      is GetEntriesUsingLogResult.Success -> fail<Nothing>(failMessage(result.entries))
    }
  }
}
