// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs

import com.intellij.diff.comparison.iterables.DiffIterableUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.impl.UndoManagerImpl
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.vcs.changes.shelf.ShelveChangesManager
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.common.runAll
import com.intellij.util.ui.UIUtil

abstract class BaseLineStatusTrackerManagerTest : BaseChangeListsTest() {
  protected lateinit var shelveManager: ShelveChangesManager
  protected lateinit var lstm: LineStatusTrackerManager
  protected lateinit var undoManager: UndoManagerImpl

  override fun setUp() {
    super.setUp()

    DiffIterableUtil.setVerifyEnabled(true)
    lstm = LineStatusTrackerManager.getInstanceImpl(project)
    undoManager = UndoManager.getInstance(project) as UndoManagerImpl
    shelveManager = ShelveChangesManager.getInstance(project)
  }

  override fun tearDown() {
    runAll(
      { clm.waitUntilRefreshed() },
      { UIUtil.dispatchAllInvocationEvents() },
      { lstm.resetExcludedFromCommitMarkers() },
      { lstm.releaseAllTrackers() },
      { DiffIterableUtil.setVerifyEnabled(false) },
      { super.tearDown() }
    )
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
    CommandProcessor.getInstance().executeCommand(project, {
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

  protected fun PartialLocalLineStatusTracker.assertAffectedChangeLists(vararg expectedNames: String) {
    assertSameElements(this.getAffectedChangeListsIds().asListIdsToNames(), *expectedNames)
  }

  protected fun Range.assertChangeList(listName: String) {
    val localRange = this as LocalRange
    assertEquals(listName, localRange.changelistId.asListIdToName())
  }

  protected fun VirtualFile.assertNullTracker() {
    val tracker = this.tracker
    if (tracker != null) {
      var message = "$tracker" +
                    ": operational - ${tracker.isOperational()}" +
                    ", valid - ${tracker.isValid()}, " +
                    ", file - ${tracker.virtualFile}"
      if (tracker is PartialLocalLineStatusTracker) {
        message += ", hasPartialChanges - ${tracker.hasPartialChangesToCommit()}" +
          ", lists - ${tracker.getAffectedChangeListsIds().asListIdsToNames()}"
      }
      assertNull(message, tracker)
    }
  }

  protected fun PartialLocalLineStatusTracker.assertExcludedState(expected: ExclusionState, listName: String) {
    assertEquals(expected, getExcludedFromCommitState(listName.asListNameToId()))
  }
}
