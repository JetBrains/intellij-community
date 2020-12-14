// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.Executor.child
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vfs.LocalFileSystem
import git4idea.test.GitSingleRepoTest
import git4idea.test.assertCommitted
import git4idea.test.gitAsBytes
import git4idea.test.tac

class GitPartialCommitTest : GitSingleRepoTest() {
  fun `test partial commit with changelists`() {
    tac("a.java", "A\nB\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { document, tracker ->
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

  fun `test partial commit with excluded range`() {
    tac("a.java", "A\nB\nC")

    withTrackedDocument("a.java", "X\nB\nZ") { document, tracker ->
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

  fun `test full commit with changelists`() {
    tac("a.java", "A\nB\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { document, tracker ->
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

  fun `test partial commit with changelists & don't commit staged change`() {
    tac("a.java", "A\nB\nC")
    tac("b.java", "A\nB\nC")
    VcsDirtyScopeManager.getInstance(project).markEverythingDirty()
    changeListManager.ensureUpToDate()

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { document, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    withTrackedDocument("b.java", "X1\nB\nZ2") { document, tracker ->
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

  fun `test partial commit with changelists & enabled autocrlf conversions`() {
    git("config core.autocrlf true")
    tac("a.java", "A\r\nB\r\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { document, tracker ->
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

  fun `test partial commit with changelists & disabled autocrlf conversions`() {
    git("config core.autocrlf false")
    tac("a.java", "A\r\nB\r\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { document, tracker ->
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


  private fun assertCommittedContent(fileName: String, expectedContent: String, useFilters: Boolean = false) {
    val actualContent = repo.gitAsBytes("cat-file" +
                                        (if (useFilters) " --filters" else " -p") +
                                        " :$fileName")
    assertEquals(expectedContent, String(actualContent, Charsets.UTF_8))
  }

  private fun withTrackedDocument(fileName: String, newContent: String, task: (Document, PartialLocalLineStatusTracker) -> Unit) {
    val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(child(fileName))!!

    withPartialTracker(file, newContent) { document, tracker ->
      // Assume that initial changes are included into commit
      tracker.setExcludedFromCommit(false)

      task(document, tracker)
      FileDocumentManager.getInstance().saveAllDocuments()
    }
  }
}