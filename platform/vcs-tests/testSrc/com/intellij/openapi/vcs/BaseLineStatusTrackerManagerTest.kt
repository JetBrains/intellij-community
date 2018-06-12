// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.DocumentReferenceManager
import com.intellij.openapi.command.undo.DocumentReferenceProvider
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.vcs.BaseLineStatusTrackerTestCase.Companion.parseInput
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.RunAll
import com.intellij.util.ThrowableRunnable
import org.mockito.Mockito

abstract class BaseLineStatusTrackerManagerTest : BaseChangeListsTest() {
  protected lateinit var lstm: LineStatusTrackerManager
  protected lateinit var undoManager: UndoManagerImpl

  override fun setUp() {
    super.setUp()

    lstm = LineStatusTrackerManager.getInstanceImpl(getProject())
    undoManager = UndoManager.getInstance(getProject()) as UndoManagerImpl
  }

  override fun tearDown() {
    RunAll()
      .append(ThrowableRunnable { lstm.releaseAllTrackers() })
      .append(ThrowableRunnable { super.tearDown() })
      .run()
  }

  override fun resetSettings() {
    super.resetSettings()
    VcsApplicationSettings.getInstance().ENABLE_PARTIAL_CHANGELISTS = true
    VcsApplicationSettings.getInstance().SHOW_LST_GUTTER_MARKERS = true
    VcsApplicationSettings.getInstance().SHOW_WHITESPACES_IN_LST = true
    arePartialChangelistsSupported = true
  }


  protected fun releaseUnneededTrackers() {
    runWriteAction { } // LineStatusTrackerManager.MyApplicationListener.afterWriteActionFinished
  }


  protected val VirtualFile.tracker: LineStatusTracker<*>? get() = lstm.getLineStatusTracker(this)
  protected fun VirtualFile.withOpenedEditor(task: () -> Unit) {
    lstm.requestTrackerFor(document, this)
    try {
      task()
    }
    finally {
      lstm.releaseTrackerFor(document, this)
    }
  }


  protected open fun runCommand(groupId: String? = null, task: () -> Unit) {
    CommandProcessor.getInstance().executeCommand(getProject(), {
      ApplicationManager.getApplication().runWriteAction(task)
    }, "", groupId)
  }

  protected fun undo(document: Document) {
    val editor = createMockFileEditor(document)
    undoManager.undo(editor)
  }

  protected fun redo(document: Document) {
    val editor = createMockFileEditor(document)
    undoManager.redo(editor)
  }

  private fun createMockFileEditor(document: Document): FileEditor {
    val editor = Mockito.mock(FileEditor::class.java, Mockito.withSettings().extraInterfaces(DocumentReferenceProvider::class.java))
    val references = listOf(DocumentReferenceManager.getInstance().create(document))
    Mockito.`when`((editor as DocumentReferenceProvider).documentReferences).thenReturn(references);
    return editor
  }

  protected fun PartialLocalLineStatusTracker.assertAffectedChangeLists(vararg expectedNames: String) {
    assertSameElements(this.affectedChangeListsIds.asListIdsToNames(), *expectedNames)
  }

  protected fun LineStatusTracker<*>.assertTextContentIs(expected: String) {
    assertEquals(parseInput(expected), document.text)
  }

  protected fun LineStatusTracker<*>.assertBaseTextContentIs(expected: String) {
    assertEquals(parseInput(expected), vcsDocument.text)
  }

  protected fun Range.assertChangeList(listName: String) {
    val localRange = this as PartialLocalLineStatusTracker.LocalRange
    assertEquals(listName, localRange.changelistId.asListIdToName())
  }
}