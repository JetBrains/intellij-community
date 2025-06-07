// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.DocumentUtil
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.impl.XDebuggerManagerImpl
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.ui.DebuggerColors
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.Cursor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DragSource

@ApiStatus.Internal
class XBreakpointVisualRepresentation(
  private val myBreakpoint: XLightLineBreakpointProxy,
  private val myIsEnabled: Boolean,
  private val myBreakpointManager: XBreakpointManagerProxy,
) {
  private val myProject: Project = myBreakpoint.project

  var rangeMarker: RangeMarker? = null
    private set

  val highlighter: RangeHighlighter?
    get() = rangeMarker as? RangeHighlighter


  fun updateUI() {
    myBreakpointManager.getLineBreakpointManager().queueBreakpointUpdateCallback(myBreakpoint) {
      doUpdateUI {}
    }
  }


  @RequiresBackgroundThread
  fun doUpdateUI(callOnUpdate: Runnable) {
    if (myBreakpoint.isDisposed() || ApplicationManager.getApplication().isUnitTestMode()) {
      return
    }
    if (!myIsEnabled) {
      return
    }

    val file = myBreakpoint.getFile() ?: return

    ReadAction.nonBlocking {
      if (myBreakpoint.isDisposed()) return@nonBlocking
      // try not to decompile files
      var document = FileDocumentManager.getInstance().getCachedDocument(file)
      if (document == null) {
        // currently LazyRangeMarkerFactory creates document for non binary files
        if (file.fileType.isBinary()) {
          ApplicationManager.getApplication().invokeLater(
            {
              if (this.rangeMarker == null) {
                this.rangeMarker = LazyRangeMarkerFactory.getInstance(myProject).createRangeMarker(file, myBreakpoint.getLine(), 0, true)
                callOnUpdate.run()
              }
            }, myProject.getDisposed())
          return@nonBlocking
        }
        document = FileDocumentManager.getInstance().getDocument(file)
        if (document == null) {
          return@nonBlocking
        }
      }

      // TODO IJPL-185322 support XBreakpointTypeWithDocumentDelegation
      if (myBreakpoint.type is XBreakpointTypeWithDocumentDelegation) {
        document = (myBreakpoint.type as XBreakpointTypeWithDocumentDelegation).getDocumentForHighlighting(document)
      }

      val range = myBreakpoint.getHighlightRange()

      val finalDocument: Document = document
      ApplicationManager.getApplication().invokeLater(
        {
          if (myBreakpoint.isDisposed()) return@invokeLater
          if (this.rangeMarker != null && this.rangeMarker !is RangeHighlighter) {
            removeHighlighter()
            assert(this.highlighter == null)
          }

          var attributes = EditorColorsManager.getInstance().getGlobalScheme().getAttributes(DebuggerColors.BREAKPOINT_ATTRIBUTES)

          if (!myBreakpoint.isEnabled()) {
            attributes = attributes.clone()
            attributes.backgroundColor = null
          }

          var highlighter = this.highlighter
          if (highlighter != null &&
              (!highlighter.isValid() || range != null && highlighter.textRange != range //breakpoint range marker is out-of-sync with actual breakpoint text range
               || !DocumentUtil.isValidOffset(highlighter.getStartOffset(), finalDocument) || !Comparing.equal<TextAttributes?>(
                highlighter.getTextAttributes(null),
                attributes) // it seems that this check is not needed - we always update line number from the highlighter
                // and highlighter is removed on line and file change anyway
                /*|| document.getLineNumber(highlighter.getStartOffset()) != getLine()*/
              )
          ) {
            removeHighlighter()
            redrawInlineInlays()
            highlighter = null
          }

          myBreakpoint.updateIcon()

          if (highlighter == null) {
            val line = myBreakpoint.getLine()
            if (line >= finalDocument.getLineCount()) {
              callOnUpdate.run()
              return@invokeLater
            }
            val markupModel = DocumentMarkupModel.forDocument(finalDocument, myProject, true) as MarkupModelEx
            if (range != null && !range.isEmpty) {
              val lineRange = DocumentUtil.getLineTextRange(finalDocument, line)
              if (range.intersectsStrict(lineRange)) {
                highlighter = markupModel.addRangeHighlighter(range.startOffset, range.endOffset,
                                                              DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER, attributes,
                                                              HighlighterTargetArea.EXACT_RANGE)
              }
            }
            if (highlighter == null) {
              highlighter = markupModel.addPersistentLineHighlighter(line, DebuggerColors.BREAKPOINT_HIGHLIGHTER_LAYER, attributes)
            }
            if (highlighter == null) {
              callOnUpdate.run()
              return@invokeLater
            }

            highlighter.setGutterIconRenderer(myBreakpoint.createGutterIconRenderer())
            highlighter.putUserData(DebuggerColors.BREAKPOINT_HIGHLIGHTER_KEY, true)
            highlighter.setEditorFilter(MarkupEditorFilter { editor -> isHighlighterAvailableIn(editor) })
            this.rangeMarker = highlighter

            redrawInlineInlays()
          }
          else {
            val markupModel = DocumentMarkupModel.forDocument(finalDocument, myProject, false) as MarkupModelEx?
            if (markupModel != null) {
              // renderersChanged false - we don't change gutter size
              val filter = highlighter.getEditorFilter()
              highlighter.setEditorFilter(MarkupEditorFilter.EMPTY)
              highlighter.setEditorFilter(filter) // to fireChanged
            }
          }
          callOnUpdate.run()
        }, myProject.getDisposed())
    }.executeSynchronously()
  }

  fun removeHighlighter() {
    if (this.highlighter != null) {
      try {
        this.highlighter!!.dispose()
      }
      catch (e: Exception) {
        LOG.error(e)
      }
      this.rangeMarker = null
    }
  }

  private fun redrawInlineInlays() {
    redrawInlineInlays(myBreakpoint.getFile(), myBreakpoint.getLine())
  }

  fun redrawInlineInlays(file: VirtualFile?, line: Int) {
    if (file == null) return
    if (!XDebuggerUtil.areInlineBreakpointsEnabled(file)) return

    val service = RedrawInlaysService.getInstance(myProject)
    service.launch {
      val document = readAction {
        val document = FileDocumentManager.getInstance().getDocument(file) ?: return@readAction null

        val type = myBreakpoint.type
        if (type !is XBreakpointTypeWithDocumentDelegation) return@readAction document
        type.getDocumentForHighlighting(document)
      } ?: return@launch
      InlineBreakpointInlayManager.getInstance(myProject).redrawLine(document, line)
    }
  }

  fun createBreakpointDraggableObject(): GutterDraggableObject {
    return object : GutterDraggableObject {
      override fun copy(line: Int, file: VirtualFile?, actionId: Int): Boolean {
        if (canMoveTo(line, file)) {
          val debuggerManager = XDebuggerManager.getInstance(myProject) as XDebuggerManagerImpl
          val breakpointManager = debuggerManager.breakpointManager
          // TODO IJPL-185322 implement DnD for light breakpoints?
          if (myBreakpoint !is XLineBreakpointProxy) {
            return false
          }
          if (isCopyAction(actionId) && myBreakpoint is XLineBreakpointProxy.Monolith) {
            // TODO IJPL-185322 support copy through gutter DnD
            breakpointManager.copyLineBreakpoint(myBreakpoint.breakpoint, file!!.url, line)
          }
          else {
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
      if (myBreakpoint is XLineBreakpointProxy.Monolith) {
        val monolithBreakpoint = myBreakpoint.breakpoint
        val existing = monolithBreakpoint.breakpointManager.findBreakpointAtLine(monolithBreakpoint.getType(), file, line)
        return existing == null || existing === monolithBreakpoint
      }
      else {
        // TODO IJPL-185322 support findBreakpointAtLine check for split
        return true
      }
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
