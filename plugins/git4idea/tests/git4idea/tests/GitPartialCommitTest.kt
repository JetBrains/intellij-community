// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.tests

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.testFramework.junit5.TestApplication
import git4idea.test.GitSingleRepoContext
import git4idea.test.assertChanges
import git4idea.test.assertChangesWithRefresh
import git4idea.test.assertCommitted
import git4idea.test.assertNoChanges
import git4idea.test.commit
import git4idea.test.git
import git4idea.test.gitAsBytes
import git4idea.test.gitSingleRepoContextFixture
import git4idea.test.tac
import git4idea.test.withPartialTracker
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
internal class GitPartialCommitTest {
  private val contextFixture = gitSingleRepoContextFixture()
  private val context: GitSingleRepoContext get() = contextFixture.get()

  @Test
  fun `test partial commit with changelists`(): Unit = with(context) {
    tac("a.java", "A\nB\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { _, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    assertChangesWithRefresh {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList("Test")!!.changes
    commit(changes)

    assertChangesWithRefresh {
      modified("a.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }

    assertCommittedContent("a.java", "A\nB\nZ")
  }

  @Test
  fun `test partial commit with excluded range`(): Unit = with(context) {
    tac("a.java", "A\nB\nC")

    withTrackedDocument("a.java", "X\nB\nZ") { _, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.setExcludedFromCommit(ranges[1], true)
    }

    assertChangesWithRefresh {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList(LocalChangeList.getDefaultName())!!.changes
    commit(changes)

    assertChangesWithRefresh {
      modified("a.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }

    assertCommittedContent("a.java", "X\nB\nC")
  }

  @Test
  fun `test full commit with changelists`(): Unit = with(context) {
    tac("a.java", "A\nB\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { _, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    assertChangesWithRefresh {
      modified("a.java")
    }

    val changes = changeListManager.allChanges
    commit(changes)

    assertNoChanges()
    repo.assertCommitted {
      modified("a.java")
    }

    assertCommittedContent("a.java", "X\nB\nZ")
  }

  @Test
  fun `test partial commit with changelists and don't commit staged change`(): Unit = with(context) {
    tac("a.java", "A\nB\nC")
    tac("b.java", "A\nB\nC")
    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    changeListManager.ensureUpToDate()

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { _, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    withTrackedDocument("b.java", "X1\nB\nZ2") { _, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    assertChangesWithRefresh {
      modified("a.java")
      modified("b.java")
    }

    val changes = changeListManager.findChangeList("Test")!!.changes
      .filter { ChangesUtil.getFilePath(it).name == "a.java" }
    commit(changes)

    assertChangesWithRefresh {
      modified("a.java")
      modified("b.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }

    assertCommittedContent("a.java", "A\nB\nZ")
    assertCommittedContent("b.java", "A\nB\nC")
  }

  @Test
  fun `test partial commit with changelists and enabled autocrlf conversions`(): Unit = with(context) {
    git("config core.autocrlf true")
    tac("a.java", "A\r\nB\r\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { _, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    assertChangesWithRefresh {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList("Test")!!.changes
    commit(changes)

    assertChangesWithRefresh {
      modified("a.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }

    assertCommittedContent("a.java", "A\nB\nZ")
    assertCommittedContent("a.java", "A\r\nB\r\nZ", true)
  }

  @Test
  fun `test partial commit with changelists and disabled autocrlf conversions`(): Unit = with(context) {
    git("config core.autocrlf false")
    tac("a.java", "A\r\nB\r\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { _, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    assertChangesWithRefresh {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList("Test")!!.changes
    commit(changes)

    assertChangesWithRefresh {
      modified("a.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }

    assertCommittedContent("a.java", "A\r\nB\r\nZ")
    assertCommittedContent("a.java", "A\r\nB\r\nZ", true)
  }

  @Test
  fun `test partial commit with multiple changelists 1`(): Unit = with(context) {
    tac("a.java", "A\nB\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { _, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    assertChanges {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList("Test")!!.changes +
                  changeListManager.findChangeList(LocalChangeList.getDefaultName())!!.changes
    commit(changes)

    assertNoChanges()
    repo.assertCommitted {
      modified("a.java")
    }

    assertCommittedContent("a.java", "X\nB\nZ")
  }

  @Test
  fun `test partial commit with multiple changelists 2`(): Unit = with(context) {
    tac("a.java", "A\nB\nC\nD\nE")

    val testChangeList1 = changeListManager.addChangeList("Test 1", null)
    val testChangeList2 = changeListManager.addChangeList("Test 2", null)

    withTrackedDocument("a.java", "X\nB\nZ\nD\nY") { _, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList1)
      tracker.moveToChangelist(ranges[2], testChangeList2)
    }

    assertChanges {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList("Test 1")!!.changes +
                  changeListManager.findChangeList("Test 2")!!.changes
    commit(changes)

    assertChanges {
      modified("a.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }

    assertCommittedContent("a.java", "A\nB\nZ\nD\nY")
  }

  private fun GitSingleRepoContext.assertCommittedContent(
    fileName: String,
    expectedContent: String,
    useFilters: Boolean = false,
  ) {
    val actualContent = repo.gitAsBytes("cat-file" +
                                        (if (useFilters) " --filters" else " -p") +
                                        " :$fileName")
    assertThat(String(actualContent, Charsets.UTF_8)).isEqualTo(expectedContent)
  }

  private fun GitSingleRepoContext.withTrackedDocument(
    fileName: String,
    newContent: String,
    task: (Document, PartialLocalLineStatusTracker) -> Unit,
  ) {
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(child(fileName))!!

    withPartialTracker(file, newContent) { document, tracker ->
      // Assume that initial changes are included into commit
      tracker.setExcludedFromCommit(false)

      task(document, tracker)
      FileDocumentManager.getInstance().saveAllDocuments()
    }
  }
}
