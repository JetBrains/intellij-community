// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
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
import org.jetbrains.annotations.CalledInAny
import org.jetbrains.annotations.CalledInAwt
import java.awt.Graphics
import java.awt.Point
import java.awt.event.MouseEvent
import java.util.*

interface LineStatusTracker<out R : Range> : LineStatusTrackerI<R> {
  val project: Project
  override val virtualFile: VirtualFile

  fun isAvailableAt(editor: Editor): Boolean

  fun scrollAndShowHint(range: Range, editor: Editor)
  fun showHint(range: Range, editor: Editor)

  fun <T> readLock(task: () -> T): T
}

abstract class LocalLineStatusTracker<R : Range> constructor(override val project: Project,
                                                             document: Document,
                                                             override val virtualFile: VirtualFile,
                                                             mode: Mode
) : LineStatusTrackerBase<R>(project, document), LineStatusTracker<R> {
  enum class Mode {
    DEFAULT, SMART, SILENT
  }

  private val vcsDirtyScopeManager: VcsDirtyScopeManager = VcsDirtyScopeManager.getInstance(project)

  abstract override val renderer: LocalLineStatusMarkerRenderer

  var mode: Mode = mode
    set(value) {
      if (value == mode) return
      field = value
      updateInnerRanges()
    }


  @CalledInAwt
  override fun isAvailableAt(editor: Editor): Boolean {
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


  override fun scrollAndShowHint(range: Range, editor: Editor) {
    renderer.scrollAndShow(editor, range)
  }

  override fun showHint(range: Range, editor: Editor) {
    renderer.showAfterScroll(editor, range)
  }

  protected open class LocalLineStatusMarkerRenderer(open val tracker: LocalLineStatusTracker<*>)
    : LineStatusMarkerPopupRenderer(tracker) {
    override fun getEditorFilter(): MarkupEditorFilter? = MarkupEditorFilterFactory.createIsNotDiffFilter()

    override fun canDoAction(editor: Editor, ranges: List<Range>, e: MouseEvent): Boolean {
      if (tracker.mode == Mode.SILENT) return false
      return super.canDoAction(editor, ranges, e)
    }

    override fun paint(editor: Editor, g: Graphics) {
      if (tracker.mode == Mode.SILENT) return
      super.paint(editor, g)
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

  @CalledInAny
  internal fun freeze() {
    documentTracker.freeze(Side.LEFT)
    documentTracker.freeze(Side.RIGHT)
  }

  @CalledInAwt
  internal fun unfreeze() {
    documentTracker.unfreeze(Side.LEFT)
    documentTracker.unfreeze(Side.RIGHT)
  }

  override fun <T> readLock(task: () -> T): T {
    return documentTracker.readLock(task)
  }
}
