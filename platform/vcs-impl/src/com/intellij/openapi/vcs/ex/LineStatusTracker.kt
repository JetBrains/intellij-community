/*
 * Copyright 2000-2016 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.DiffUtil
import com.intellij.ide.GeneralSettings
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import org.jetbrains.annotations.CalledInAwt
import java.awt.Graphics
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JPanel

class LineStatusTracker private constructor(project: Project,
                                            document: Document,
                                            override val virtualFile: VirtualFile,
                                            mode: Mode
) : LineStatusTrackerBase(project, document) {
  enum class Mode {
    DEFAULT, SMART, SILENT
  }

  private val fileEditorManager: FileEditorManager = FileEditorManager.getInstance(project)
  private val vcsDirtyScopeManager: VcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(project)

  override val renderer: MyLineStatusMarkerRenderer = MyLineStatusMarkerRenderer(this)

  var mode: Mode = mode
    set(value) {
      if (value == mode) return
      field = value
      reinstallRanges()
    }

  @CalledInAwt
  fun isAvailableAt(editor: Editor): Boolean {
    return mode != Mode.SILENT && editor.settings.isLineMarkerAreaShown && !DiffUtil.isDiffEditor(editor)
  }

  @CalledInAwt
  override fun isDetectWhitespaceChangedLines(): Boolean = mode == Mode.SMART

  @CalledInAwt
  override fun installNotification(text: String) {
    val editors = fileEditorManager.getAllEditors(virtualFile)
    for (editor in editors) {
      val panel = editor.getUserData(PANEL_KEY)
      if (panel == null) {
        val newPanel = EditorNotificationPanel().text(text)
        editor.putUserData(PANEL_KEY, newPanel)
        fileEditorManager.addTopComponent(editor, newPanel)
      }
    }
  }

  @CalledInAwt
  override fun destroyNotification() {
    val editors = fileEditorManager.getEditors(virtualFile)
    for (editor in editors) {
      val panel = editor.getUserData(PANEL_KEY)
      if (panel != null) {
        fileEditorManager.removeTopComponent(editor, panel)
        editor.putUserData(PANEL_KEY, null)
      }
    }
  }

  @CalledInAwt
  override fun fireFileUnchanged() {
    if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
      // later to avoid saving inside document change event processing.
      TransactionGuard.getInstance().submitTransactionLater(project!!, Runnable {
        FileDocumentManager.getInstance().saveDocument(document)
        val ranges = getRanges()
        if (ranges == null || ranges.isEmpty()) {
          // file was modified, and now it's not -> dirty local change
          vcsDirtyScopeManager.fileDirty(virtualFile);
        }
      })
    }
  }

  override fun doRollbackRange(range: Range) {
    super.doRollbackRange(range)
    markLinesUnchanged(range.line1, range.line1 + range.vcsLine2 - range.vcsLine1)
  }

  private fun markLinesUnchanged(startLine: Int, endLine: Int) {
    if (document.textLength == 0) return  // empty document has no lines
    if (startLine == endLine) return
    (document as DocumentImpl).clearLineModificationFlags(startLine, endLine)
  }


  fun scrollAndShowHint(range: Range, editor: Editor) {
    renderer.scrollAndShow(editor, range)
  }

  fun showHint(range: Range, editor: Editor) {
    renderer.showAfterScroll(editor, range)
  }

  class MyLineStatusMarkerRenderer(val tracker: LineStatusTracker) : LineStatusMarkerPopupRenderer(tracker) {
    override fun getEditorFilter(): MarkupEditorFilter? = MarkupEditorFilterFactory.createIsNotDiffFilter()

    override fun canDoAction(range: Range, e: MouseEvent?): Boolean {
      if (tracker.mode == Mode.SILENT) return false
      return super.canDoAction(range, e)
    }

    override fun paint(editor: Editor, range: Range, g: Graphics) {
      if (tracker.mode == Mode.SILENT) return
      super.paint(editor, range, g)
    }

    override fun createToolbarActions(editor: Editor, range: Range, mousePosition: Point?): List<AnAction> {
      val actions = ArrayList<AnAction>()
      actions.add(ShowPrevChangeMarkerAction(editor, range))
      actions.add(ShowNextChangeMarkerAction(editor, range))
      actions.add(RollbackLineStatusRangeAction(tracker, range, editor))
      actions.add(ShowLineStatusRangeDiffAction(range))
      actions.add(CopyLineStatusRangeAction(range))
      actions.add(ToggleByWordDiffAction(editor, range, mousePosition))
      return actions
    }

    override fun getFileType(): FileType {
      return tracker.virtualFile.getFileType()
    }
  }

  companion object {
    private val PANEL_KEY = Key<JPanel>("LineStatusTracker.CanNotCalculateDiffPanel")

    @JvmStatic
    fun createOn(virtualFile: VirtualFile,
                 document: Document,
                 project: Project,
                 mode: Mode): LineStatusTracker {
      return LineStatusTracker(project, document, virtualFile, mode)
    }
  }
}
