// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.tests

import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.vcs.Executor.child
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.CharsetToolkit
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.util.ui.UIUtil
import git4idea.test.*

class GitPartialCommitTest : GitSingleRepoTest() {
  fun `test partial commit with changelists`() {
    tac("a.java", "A\nB\nC")

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { document, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    assertChanges {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList("Test")!!.changes
    commit(changes)

    assertChanges {
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

    assertChanges {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList(LocalChangeList.DEFAULT_NAME)!!.changes
    commit(changes)

    assertChanges {
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

    assertChanges {
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

    val testChangeList = changeListManager.addChangeList("Test", null)

    withTrackedDocument("a.java", "X\nB\nZ") { document, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    withTrackedDocument("b.java", "X1\nB\nZ2") { document, tracker ->
      val ranges = tracker.getRanges()!!
      tracker.moveToChangelist(ranges[1], testChangeList)
    }

    assertChanges {
      modified("a.java")
      modified("b.java")
    }

    val changes = changeListManager.findChangeList("Test")!!.changes
      .filter { ChangesUtil.getFilePath(it).name == "a.java" }
    commit(changes)

    assertChanges {
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

    assertChanges {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList("Test")!!.changes
    commit(changes)

    assertChanges {
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

    assertChanges {
      modified("a.java")
    }

    val changes = changeListManager.findChangeList("Test")!!.changes
    commit(changes)

    assertChanges {
      modified("a.java")
    }
    repo.assertCommitted {
      modified("a.java")
    }

    assertCommittedContent("a.java", "A\r\nB\r\nZ")
    assertCommittedContent("a.java", "A\r\nB\r\nZ", true)
  }


  private fun assertNoChanges() {
    changeListManager.assertNoChanges()
  }

  private fun assertChanges(changes: ChangesBuilder.() -> Unit): List<Change> {
    return changeListManager.assertChanges(changes)
  }

  private fun assertCommittedContent(fileName: String, expectedContent: String, useFilters: Boolean = false) {
    val actualContent = repo.gitAsBytes("cat-file" +
                                        (if (useFilters) " --filters" else " -p") +
                                        " :$fileName")
    assertEquals(expectedContent, String(actualContent, CharsetToolkit.UTF8_CHARSET))
  }

  private fun withTrackedDocument(fileName: String, newContent: String, task: (Document, PartialLocalLineStatusTracker) -> Unit) {
    invokeAndWaitIfNeeded {
      val lstm = LineStatusTrackerManager.getInstance(project) as LineStatusTrackerManager

      val file = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(child(fileName))!!
      val document = runReadAction { FileDocumentManager.getInstance().getDocument(file)!! }

      runWriteAction {
        document.setText(newContent)
      }
      changeListManager.waitUntilRefreshed()
      UIUtil.dispatchAllInvocationEvents() // ensure `fileStatusesChanged` events are fired

      lstm.requestTrackerFor(document, this)
      try {
        val tracker = lstm.getLineStatusTracker(file)
        lstm.waitUntilBaseContentsLoaded()

        task(document, tracker as PartialLocalLineStatusTracker)

        FileDocumentManager.getInstance().saveAllDocuments()
      }
      finally {
        lstm.releaseTrackerFor(document, this)
      }
    }
  }
}