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
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.TransactionGuard
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.DocumentImpl
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.VcsDirtyScopeManager
import com.intellij.openapi.vfs.VirtualFile
import org.jetbrains.annotations.CalledInAwt
import java.awt.Graphics
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.*

abstract class LineStatusTracker<R : Range> constructor(override val project: Project,
                                                        document: Document,
                                                        override val virtualFile: VirtualFile,
                                                        mode: Mode
) : LineStatusTrackerBase<R>(project, document) {
  enum class Mode {
    DEFAULT, SMART, SILENT
  }

  private val vcsDirtyScopeManager: VcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(project)

  override abstract val renderer: LocalLineStatusMarkerRenderer

  var mode: Mode = mode
    set(value) {
      if (value == mode) return
      field = value
      updateInnerRanges()
    }


  @CalledInAwt
  fun isAvailableAt(editor: Editor): Boolean {
    return mode != Mode.SILENT && editor.settings.isLineMarkerAreaShown && !DiffUtil.isDiffEditor(editor)
  }

  @CalledInAwt
  override fun isDetectWhitespaceChangedLines(): Boolean = mode == Mode.SMART

  @CalledInAwt
  override fun fireFileUnchanged() {
    if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
      // later to avoid saving inside document change event processing and deadlock with CLM.
      TransactionGuard.getInstance().submitTransactionLater(project, Runnable {
        FileDocumentManager.getInstance().saveDocument(document)
        val isEmpty = documentTracker.readLock { blocks.isEmpty() }
        if (isEmpty) {
          // file was modified, and now it's not -> dirty local change
          vcsDirtyScopeManager.fileDirty(virtualFile)
        }
      })
    }
  }

  override fun fireLinesUnchanged(startLine: Int, endLine: Int) {
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

  protected open class LocalLineStatusMarkerRenderer(open val tracker: LineStatusTracker<*>)
    : LineStatusMarkerPopupRenderer(tracker) {
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
      actions.add(RollbackLineStatusRangeAction(editor, range))
      actions.add(ShowLineStatusRangeDiffAction(editor, range))
      actions.add(CopyLineStatusRangeAction(editor, range))
      actions.add(ToggleByWordDiffAction(editor, range, mousePosition))
      return actions
    }

    override fun getFileType(): FileType = tracker.virtualFile.fileType

    private inner class RollbackLineStatusRangeAction(editor: Editor, range: Range)
      : RangeMarkerAction(editor, range, IdeActions.SELECTED_CHANGES_ROLLBACK) {
      override fun isEnabled(editor: Editor, range: Range): Boolean = true

      override fun actionPerformed(editor: Editor, range: Range) {
        RollbackLineStatusAction.rollback(tracker, range, editor)
      }
    }
  }
}
