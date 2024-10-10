// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.codeWithMe.ClientId
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.ide.GeneralSettings
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vcs.ex.RollbackLineStatusAction.rollback
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.concurrency.annotations.RequiresEdt
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.CalledInAny
import java.awt.Point
import javax.swing.JComponent

interface LineStatusTracker<out R : Range> : LineStatusTrackerI<R> {
  override val project: Project
  override val virtualFile: VirtualFile

  /**
   * Whether tracker gutter markers are visible in a given [Editor].
   */
  @RequiresEdt
  fun isAvailableAt(editor: Editor): Boolean {
    return editor.settings.isLineMarkerAreaShown && !DiffUtil.isDiffEditor(editor)
  }

  @RequiresEdt
  fun scrollAndShowHint(range: Range, editor: Editor)

  @RequiresEdt
  fun showHint(range: Range, editor: Editor)
}

/**
 * Trackers tracked by [com.intellij.openapi.vcs.impl.LineStatusTrackerManager].
 *
 * The trackers are frozen by [com.intellij.openapi.vcs.changes.VcsFreezingProcess].
 *
 * There's a lock order:
 * [com.intellij.openapi.application.Application.runReadAction] ->
 * [com.intellij.openapi.vcs.changes.ChangeListManagerImpl.executeUnderDataLock] ->
 * [LineStatusTracker.readLock].
 * Which means implementations CAN NOT access CLM during most operations, including [DocumentTracker.Handler].
 *
 * @see com.intellij.openapi.vcs.impl.LocalLineStatusTrackerProvider
 */
interface LocalLineStatusTracker<R : Range> : LineStatusTracker<R> {
  fun release()

  @CalledInAny
  fun freeze()

  @CalledInAny
  fun unfreeze()

  var mode: Mode

  class Mode(val isVisible: Boolean,
             val showErrorStripeMarkers: Boolean,
             val detectWhitespaceChangedLines: Boolean)

  @RequiresEdt
  override fun isAvailableAt(editor: Editor): Boolean {
    return mode.isVisible && super.isAvailableAt(editor)
  }
}

@ApiStatus.Internal
abstract class LocalLineStatusTrackerImpl<R : Range>(
  final override val project: Project,
  document: Document,
  final override val virtualFile: VirtualFile
) : LineStatusTrackerBase<R>(project, document), LocalLineStatusTracker<R> {
  protected abstract val renderer: LocalLineStatusMarkerRenderer

  private val innerRangesHandler = MyInnerRangesDocumentTrackerHandler()
  private val clientIdsHandler = MyClientIdsDocumentTrackerHandler()

  override var mode: LocalLineStatusTracker.Mode = LocalLineStatusTracker.Mode(true, true, false)
    set(value) {
      if (value == mode) return
      field = value
      innerRangesHandler.resetInnerRanges()
      updateHighlighters()
    }

  init {
    documentTracker.addHandler(LocalDocumentTrackerHandler())
    documentTracker.addHandler(innerRangesHandler)
    if (showClientIdGutterIconRenderer(project)) {
      documentTracker.addHandler(clientIdsHandler)
    }
    listeners.addListener(object : LineStatusTrackerListener {
      override fun onRangesChanged() {
        renderer.scheduleUpdate()
      }
    })
  }

  @RequiresEdt
  override fun isDetectWhitespaceChangedLines(): Boolean = mode.isVisible && mode.detectWhitespaceChangedLines

  override fun isClearLineModificationFlagOnRollback(): Boolean = true

  abstract override val Block.ourData: LocalBlockData

  @RequiresEdt
  abstract fun setBaseRevision(vcsContent: CharSequence)

  override fun setBaseRevisionContent(vcsContent: CharSequence, beforeUnfreeze: (() -> Unit)?) {
    super.setBaseRevisionContent(vcsContent, beforeUnfreeze)

    if (blocks.isEmpty() && isOperational()) {
      saveDocumentWhenUnchanged(project, document)
    }
  }

  override fun scrollAndShowHint(range: Range, editor: Editor) {
    renderer.scrollAndShow(editor, range)
  }

  override fun showHint(range: Range, editor: Editor) {
    renderer.showAfterScroll(editor, range)
  }

  protected open class LocalLineStatusMarkerRenderer(
    protected open val tracker: LocalLineStatusTrackerImpl<*>
  ) : LineStatusTrackerMarkerRenderer(tracker, MarkupEditorFilterFactory.createIsNotDiffFilter()) {

    override fun shouldPaintGutter(): Boolean {
      return tracker.mode.isVisible
    }

    override fun shouldPaintErrorStripeMarkers(): Boolean {
      return tracker.mode.isVisible && tracker.mode.showErrorStripeMarkers
    }

    override fun createToolbarActions(editor: Editor, range: Range, mousePosition: Point?): List<AnAction> {
      val actions = ArrayList<AnAction>()
      actions.add(LineStatusMarkerPopupActions.ShowPrevChangeMarkerAction(editor, tracker, range, this))
      actions.add(LineStatusMarkerPopupActions.ShowNextChangeMarkerAction(editor, tracker, range, this))
      actions.add(RollbackLineStatusRangeAction(editor, range))
      actions.add(LineStatusMarkerPopupActions.ShowLineStatusRangeDiffAction(editor, tracker, range))
      actions.add(LineStatusMarkerPopupActions.CopyLineStatusRangeAction(editor, tracker, range))
      actions.add(LineStatusMarkerPopupActions.ToggleByWordDiffAction(editor, tracker, range, mousePosition, this))
      return actions
    }

    override fun createAdditionalInfoPanel(editor: Editor, range: Range, mousePosition: Point?, disposable: Disposable): JComponent? {
      val clientIds = (range as? LstLocalRange)?.clientIds ?: return null
      return createClientIdGutterPopupPanel(tracker.project, clientIds)
    }

    private inner class RollbackLineStatusRangeAction(editor: Editor, range: Range)
      : LineStatusMarkerPopupActions.RangeMarkerAction(editor, tracker, range, IdeActions.SELECTED_CHANGES_ROLLBACK), LightEditCompatible {
      override fun isEnabled(editor: Editor, range: Range): Boolean = true

      override fun actionPerformed(editor: Editor, range: Range) {
        rollback(tracker, range, editor)
      }
    }

    override fun toString(): String = "LocalLineStatusMarkerRenderer(tracker=$tracker)"
  }

  private inner class LocalDocumentTrackerHandler : DocumentTracker.Handler {
    override fun afterBulkRangeChange(isDirty: Boolean) {
      if (blocks.isEmpty() && isOperational()) {
        saveDocumentWhenUnchanged(project, document)
      }
    }
  }

  private inner class MyInnerRangesDocumentTrackerHandler : InnerRangesDocumentTrackerHandler() {
    override fun isDetectWhitespaceChangedLines(): Boolean = mode.let { it.isVisible && it.detectWhitespaceChangedLines }

    override var Block.innerRanges: List<Range.InnerRange>?
      get() {
        return ourData.innerRanges
      }
      set(value) {
        ourData.innerRanges = value
      }
  }

  private inner class MyClientIdsDocumentTrackerHandler : ClientIdsDocumentTrackerHandler(project) {
    /**
     * Sorted by [ClientId.value]
     */
    override var Block.clientIds: List<ClientId>
      get() {
        return ourData.clientIds
      }
      set(value) {
        ourData.clientIds = value
      }
  }

  @CalledInAny
  override fun freeze() {
    documentTracker.freeze(Side.LEFT)
    documentTracker.freeze(Side.RIGHT)
  }

  @RequiresEdt
  override fun unfreeze() {
    documentTracker.unfreeze(Side.LEFT)
    documentTracker.unfreeze(Side.RIGHT)
  }

  protected interface LocalBlockData : DocumentTracker.BlockData {
    var innerRanges: List<Range.InnerRange>?
    var clientIds: List<ClientId>
  }

  @ApiStatus.Experimental
  interface LstLocalRange {
    val clientIds: List<ClientId>
  }
}

fun saveDocumentWhenUnchanged(project: Project, document: Document) {
  if (GeneralSettings.getInstance().isSaveOnFrameDeactivation) {
    // Use 'invokeLater' to avoid saving inside document change event processing and deadlock with CLM.
    if (ModalityState.current() != ModalityState.nonModal()) {
      println("Saving document from non-modal")
      Throwable().printStackTrace()
    }
    ApplicationManager.getApplication().invokeLater(Runnable {
      FileDocumentManager.getInstance().saveDocument(document)
    }, ModalityState.nonModal(), project.disposed)
  }
}
