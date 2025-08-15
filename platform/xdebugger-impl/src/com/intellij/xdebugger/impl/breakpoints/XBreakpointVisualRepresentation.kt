// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.LazyRangeMarkerFactory
import com.intellij.openapi.editor.RangeMarker
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.DocumentUtil
import com.intellij.util.ThreeState
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import com.intellij.xdebugger.ui.DebuggerColors
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragSource
import java.lang.Runnable

private data class UpdateUICallback(val callOnUpdate: Runnable)

@ApiStatus.Internal
class XBreakpointVisualRepresentation(
  cs: CoroutineScope,
  private val myBreakpoint: XLightLineBreakpointProxy,
  isEnabled: Boolean,
  private val myBreakpointManager: XBreakpointManagerProxy,
) {
  private val myProject: Project = myBreakpoint.project
  private val channel = Channel<UpdateUICallback>(Channel.UNLIMITED)

  init {
    if (isEnabled && !ApplicationManager.getApplication().isUnitTestMode()) {
      cs.launch(start = CoroutineStart.ATOMIC) {
        try {
          for (event in channel) {
            try {
              internalUpdateUI(event.callOnUpdate)
            }
            catch (e: Throwable) {
              if (e is CancellationException) throw e
              fileLogger().error(e)
            }
          }
        }
        finally {
          // Guarantee that the highlighter is removed when the scope is canceled
          removeHighlighter()
        }
      }
    }
    else {
      channel.close()
    }
  }

  var rangeMarker: RangeMarker? = null
    private set

  val highlighter: RangeHighlighter?
    get() = rangeMarker as? RangeHighlighter


  fun updateUI() {
    myBreakpointManager.getLineBreakpointManager().queueBreakpointUpdateCallback(myBreakpoint) {
      doUpdateUI {}
    }
  }

  fun doUpdateUI(callOnUpdate: Runnable) {
    channel.trySend(UpdateUICallback(callOnUpdate))
  }

  private suspend fun internalUpdateUI(callOnUpdate: Runnable) {
    val file = myBreakpoint.getFile() ?: return

    val document = readAction { findDocument(file, mayDecompile = false) }
    if (document == null) {
      // currently LazyRangeMarkerFactory creates document for non binary files
      if (readAction { file.fileType.isBinary() }) {
        withContext(Dispatchers.EDT) {
          if (rangeMarker == null) {
            rangeMarker = LazyRangeMarkerFactory.getInstance(myProject).createRangeMarker(file, myBreakpoint.getLine(), 0, true)
            callOnUpdate.run()
          }
        }
      }
      return
    }
    withContext(Dispatchers.EDT) {
      val highlightRange = myBreakpoint.getHighlightRangeSuspend()
      if (highlightRange !is XLineBreakpointHighlighterRange.Available) return@withContext
      val range = highlightRange.range
      if (rangeMarker != null && rangeMarker !is RangeHighlighter) {
        removeHighlighter()
        assert(highlighter == null)
      }

      val attributes = getBreakpointAttributes()
      val highlighter = getHighlighterIfValid(range, document, attributes)

      myBreakpoint.updateIcon()

      if (highlighter == null) {
        createHighlighter(document, range, attributes)
      }
      else {
        val markupModel = DocumentMarkupModel.forDocument(document, myProject, false) as MarkupModelEx?
        if (markupModel != null) {
          // renderersChanged false - we don't change gutter size
          val filter = highlighter.getEditorFilter()
          highlighter.setEditorFilter(MarkupEditorFilter.EMPTY)
          highlighter.setEditorFilter(filter) // to fireChanged
        }
      }
      callOnUpdate.run()
    }
  }

  private fun getHighlighterIfValid(
    range: TextRange?,
    document: Document,
    attributes: TextAttributes?,
  ): RangeHighlighter? {
    val highlighter = this.highlighter ?: return null
    if (!highlighter.isValid()
        //breakpoint range marker is out-of-sync with actual breakpoint text range
        || range != null && highlighter.textRange != range
        || !DocumentUtil.isValidOffset(highlighter.getStartOffset(), document)
        || !Comparing.equal(highlighter.getTextAttributes(null), attributes)
    ) {
      removeHighlighter()
      redrawInlineInlays()
      return null
    }
    return highlighter
  }

  private fun getBreakpointAttributes(): TextAttributes? {
    var attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES)

    if (!myBreakpoint.isEnabled()) {
      attributes = attributes.clone()
      attributes.backgroundColor = null
    }
    return attributes
  }

  private fun createHighlighter(document: Document, range: TextRange?, attributes: TextAttributes?) {
    var highlighter: RangeHighlighter? = null
    val line = myBreakpoint.getLine()
    if (!DocumentUtil.isValidLine(line, document)) return
    val markupModel = DocumentMarkupModel.forDocument(document, myProject, true) as MarkupModelEx
    if (range != null && !range.isEmpty) {
      val lineRange = DocumentUtil.getLineTextRange(document, line)
      if (range.intersectsStrict(lineRange)) {
        highlighter = markupModel.addRangeHighlighter(range.startOffset, range.endOffset,
                                                      DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER, attributes,
                                                      HighlighterTargetArea.EXACT_RANGE)
      }
    }
    if (highlighter == null) {
      highlighter = markupModel.addPersistentLineHighlighter(line, DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER, attributes)
    }
    if (highlighter == null) return
    highlighter.setGutterIconRenderer(myBreakpoint.createGutterIconRenderer())
    highlighter.putUserData(DebuggerColors.BREAKPOINT_HIGHLIGHTER_KEY, true)
    highlighter.setEditorFilter(MarkupEditorFilter { editor -> isHighlighterAvailableIn(editor) })
    this.rangeMarker = highlighter

    redrawInlineInlays()
  }

  private fun findDocument(file: VirtualFile, mayDecompile: Boolean): Document? {
    var document = FileDocumentManager.getInstance().getCachedDocument(file)
    if (document == null) {
      if (!mayDecompile && file.fileType.isBinary()) {
        return null
      }
      document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    }

    // TODO IJPL-185322 support XBreakpointTypeWithDocumentDelegation
    if (myBreakpoint.type is XBreakpointTypeWithDocumentDelegation) {
      document = (myBreakpoint.type as XBreakpointTypeWithDocumentDelegation).getDocumentForHighlighting(document)
    }
    return document
  }

  fun removeHighlighter() {
    try {
      rangeMarker?.dispose()
    }
    catch (e: Exception) {
      LOG.error(e)
    }
    rangeMarker = null
  }

  private fun redrawInlineInlays() {
    redrawInlineInlays(myBreakpoint.getFile(), myBreakpoint.getLine())
  }

  fun redrawInlineInlays(file: VirtualFile?, line: Int) {
    if (file == null) return
    if (!XDebuggerUtil.areInlineBreakpointsEnabled(file)) return

    val service = RedrawInlaysService.getInstance(myProject)
    service.launch {
      val document = readAction { findDocument(file, mayDecompile = true) } ?: return@launch
      InlineBreakpointInlayManager.getInstance(myProject).redrawLine(document, line)
    }
  }

  fun createBreakpointDraggableObject(): GutterDraggableObject {
    return object : GutterDraggableObject {
      override fun copy(line: Int, file: VirtualFile?, actionId: Int): Boolean {
        if (canMoveTo(line, file)) {
          // TODO IJPL-185322 implement DnD for light breakpoints?
          if (myBreakpoint !is XLineBreakpointProxy) {
            return false
          }
          if (isCopyAction(actionId)) {
            val breakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(myProject)
            breakpointManager.copyLineBreakpoint(myBreakpoint, file!!, line)
          }
          else {
            val debuggerManager = XDebuggerManager.getInstance(myProject) as XDebuggerManagerImpl
            myBreakpoint.setFileUrl(file!!.url)
            myBreakpoint.setLine(line)
            val session = debuggerManager.currentSession
            if (session != null && myBreakpoint is XLineBreakpointProxy.Monolith) {
              // TODO IJPL-185322 support active breakpoint update on DnD
              session.checkActiveNonLineBreakpointOnRemoval(myBreakpoint.breakpoint)
            }
            return true
          }
        }
        return false
      }

      override fun remove() {
        // TODO IJPL-185322 implement DnD remove for light breakpoints?
        if (myBreakpoint is XLineBreakpointProxy) {
          XDebuggerUtilImpl.removeBreakpointWithConfirmation(myBreakpoint)
        }
      }

      override fun getCursor(line: Int, file: VirtualFile?, actionId: Int): Cursor? {
        if (canMoveTo(line, file)) {
          return if (isCopyAction(actionId)) DragSource.DefaultCopyDrop else DragSource.DefaultMoveDrop
        }

        return DragSource.DefaultMoveNoDrop
      }

      fun isCopyAction(actionId: Int): Boolean {
        return (actionId and DnDConstants.ACTION_COPY) == DnDConstants.ACTION_COPY
      }
    }
  }

  private fun canMoveTo(line: Int, file: VirtualFile?): Boolean {
    if (file != null && myBreakpoint.type.canPutAtFast(file, line, myProject) == ThreeState.YES) {
      val existing = myBreakpointManager.findBreakpointAtLine(myBreakpoint.type, file, line)
      return existing == null || existing == myBreakpoint
    }
    return false
  }

  companion object {
    private val LOG = Logger.getInstance(XBreakpointVisualRepresentation::class.java)

    private fun isHighlighterAvailableIn(editor: Editor): Boolean {
      if (editor is EditorImpl && editor.isStickyLinePainting) {
        // suppress breakpoints on sticky lines panel
        return false
      }
      return !DiffUtil.isDiffEditor(editor)
    }
  }
}

@Service(Service.Level.PROJECT)
private class RedrawInlaysService(private val cs: CoroutineScope) {
  private val limitedDispatcher = Dispatchers.Default.limitedParallelism(1)

  fun launch(block: suspend CoroutineScope.() -> Unit) {
    cs.launch(limitedDispatcher, block = block)
  }

  companion object {
    fun getInstance(project: Project): RedrawInlaysService = project.service()
  }
}
