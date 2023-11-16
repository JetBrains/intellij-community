
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.icons.AllIcons
import com.intellij.openapi.application.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.toMutableSmartList
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xdebugger.XDebuggerManager
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.breakpoints.XBreakpointProperties
import com.intellij.xdebugger.breakpoints.XLineBreakpoint
import com.intellij.xdebugger.breakpoints.XLineBreakpointType
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.XSourcePositionImpl
import kotlinx.coroutines.*
import kotlin.math.max

@Service(Service.Level.PROJECT)
internal class InlineBreakpointInlayManager(private val project: Project, private val scope: CoroutineScope) {

  private val redrawQueue = MergingUpdateQueue(
    "inline breakpoint inlay redraw queue",
    300, true, null, project, null, false
  ).setRestartTimerOnAdd(true)

  private fun areInlineBreakpointsEnabled() = XDebuggerUtil.areInlineBreakpointsEnabled()

  /**
   * Refresh inlays for the given [document] at given [line].
   *
   * Try to do it as soon as possible.
   */
  fun redrawLine(document: Document, line: Int) {
    if (!areInlineBreakpointsEnabled()) return
    scope.launch {
      redraw(document, line, null)
    }
  }

  /**
   * Schedule refresh inlays for the given [document] at given [line].
   *
   * This request might be merged with subsequent requests for the same location.
   */
  fun redrawLineQueued(document: Document, line: Int) {
    if (!areInlineBreakpointsEnabled()) return
    redrawQueue.queue(Update.create(Pair(document, line)) {
      redrawLine(document, line)
    })
  }

  /**
   * Refresh all inlays in the editor.
   */
  fun initializeInNewEditor(editor: Editor) {
    if (!areInlineBreakpointsEnabled()) return
    scope.launch {
      val document = editor.document
      if (allBreakpointsIn(document).isEmpty()) {
        // No need to clear inlays in new editor, so we can just skip whole redraw.
        // FIXME[inline-bp]: test DaemonRespondToChangesTest.testLocalInspectionMustReceiveCorrectVisibleRangeViaItsHighlightingSession
        //                   fails if you remove this fast-path,
        //                   it's really strange, investigate it
        return@launch
      }
      redraw(document, null, editor)
    }
  }

  /**
   * Refresh inlays in all editors.
   */
  fun reinitializeAll() {
    val enabled = areInlineBreakpointsEnabled()
    for (editor in EditorFactory.getInstance().allEditors) {
      val document = editor.document
      if (enabled) {
        // We might be able to iterate all editors inside redraw,
        // but this procedure is a really cold path and doesn't worse any optimization.
        scope.launch {
          redraw(document, null, editor)
        }
      }
      else {
        scope.launch {
          writeAction {
            for (inlay in editor.inlayModel.getInlineElementsInRange(Int.MIN_VALUE, Int.MAX_VALUE, InlineBreakpointInlayRenderer::class.java)) {
              Disposer.dispose(inlay)
            }
          }
        }
      }
    }
  }

  private suspend fun redraw(document: Document, onlyLine: Int?, onlyEditor: Editor?) {
    val startStamp = document.modificationStamp

    fun retryLater() {
      redrawQueue.queue(Update.create(Pair(document, onlyLine)) {
        scope.launch {
          redraw(document, onlyLine, onlyEditor)
        }
      })
    }

    // We need Document and PSI to be in sync. Also ensure that document was not changed.
    fun shouldRetry() =
      !PsiDocumentManager.getInstance(project).isCommitted(document) || document.modificationStamp != startStamp

    if (shouldRetry()) {
      retryLater()
      return
    }

    val allBreakpoints = allBreakpointsIn(document)

    val inlays = mutableListOf<SingleInlayDatum>()
    if (onlyLine != null) {
      if (!DocumentUtil.isValidLine(onlyLine, document)) return

      val breakpoints = allBreakpoints.filter { it.line == onlyLine }
      if (!breakpoints.isEmpty()) {
        inlays += collectInlays(document, onlyLine, breakpoints)
      }
    }
    else {
      for ((line, breakpoints) in allBreakpoints.groupBy { it.line }) {
        // We could process lines concurrently, but it doesn't seem to be really required.
        inlays += collectInlays(document, line, breakpoints)
      }
    }

    writeAction {
      if (shouldRetry()) {
        retryLater()
        return@writeAction
      }

      insertInlays(document, onlyEditor, onlyLine, inlays)
    }
  }

  private fun allBreakpointsIn(document: Document): Collection<XLineBreakpointImpl<*>> {
    val lineBreakpointManager = (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).lineBreakpointManager
    return lineBreakpointManager.getDocumentBreakpoints(document)
  }

  private data class SingleInlayDatum(
    val breakpoint: XLineBreakpointImpl<*>?,
    val variant: XLineBreakpointType<*>.XLineBreakpointVariant?,
    val offset: Int,
  )

  private suspend fun collectInlays(document: Document,
                                    line: Int,
                                    breakpoints: List<XLineBreakpointImpl<*>>): List<SingleInlayDatum> {
    return readAction {
      if (!DocumentUtil.isValidLine(line, document)) return@readAction emptyList()

      val file = FileDocumentManager.getInstance().getFile(document) ?: return@readAction emptyList()
      val linePosition = XSourcePositionImpl.create(file, line)
      val breakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(project, linePosition, null)

      val variants =
        if (breakpointTypes.isNotEmpty()) {
          XDebuggerUtilImpl.getLineBreakpointVariantsSync(project, breakpointTypes, linePosition)
            // No need to show "all" variant in case of the inline breakpoints approach, it's useful only for the popup based one.
            .filter { !isAllVariant(it) }
        }
        else {
          emptyList()
        }

      val codeStartOffset = DocumentUtil.getLineStartIndentedOffset(document, line)

      if (breakpoints.size == 1 &&
          (variants.isEmpty() ||
           variants.size == 1 && areMatching(variants[0], breakpoints[0], codeStartOffset))) {
        // No need to show inline variants when there is only one breakpoint and one matching variant (or no variants at all).
        return@readAction emptyList()
      }

      buildList {
        val remainingBreakpoints = breakpoints.toMutableSmartList()
        for (variant in variants) {
          val breakpointsHere = remainingBreakpoints.filter { areMatching(variant, it, codeStartOffset) }
          if (!breakpointsHere.isEmpty()) {
            for (breakpointHere in breakpointsHere) {
              remainingBreakpoints.remove(breakpointHere)
              add(SingleInlayDatum(breakpointHere, variant,
                                   getBreakpointRangeStartOffset(breakpointHere, codeStartOffset)))
            }
          }
          else {
            add(SingleInlayDatum(null, variant,
                                 getBreakpointVariantRangeStartOffset(variant, codeStartOffset)))
          }
        }
        for (remainingBreakpoint in remainingBreakpoints) {
          add(SingleInlayDatum(remainingBreakpoint, null,
                               getBreakpointRangeStartOffset(remainingBreakpoint, codeStartOffset)))
        }
      }
    }
  }

  private fun areMatching(variant: XLineBreakpointType<*>.XLineBreakpointVariant, breakpoint: XLineBreakpointImpl<*>, codeStartOffset: Int): Boolean {
    return variant.type == breakpoint.type &&
           getBreakpointVariantRangeStartOffset(variant, codeStartOffset) == getBreakpointRangeStartOffset(breakpoint, codeStartOffset)
  }

  private fun getBreakpointVariantRangeStartOffset(variant: XLineBreakpointType<*>.XLineBreakpointVariant, codeStartOffset: Int): Int {
    val variantRange = variant.highlightRange
    return getLineRangeStartNormalized(variantRange, codeStartOffset)
  }

  @Suppress("UNCHECKED_CAST")
  private fun getBreakpointRangeStartOffset(breakpoint: XLineBreakpointImpl<*>, codeStartOffset: Int): Int {
    val type: XLineBreakpointType<XBreakpointProperties<*>> = breakpoint.type as XLineBreakpointType<XBreakpointProperties<*>>
    val breakpointRange = type.getHighlightRange(breakpoint as XLineBreakpoint<XBreakpointProperties<*>>)
    return getLineRangeStartNormalized(breakpointRange, codeStartOffset)
  }

  private fun getLineRangeStartNormalized(range: TextRange?, codeStartOffset: Int): Int {
    // Null range represents the whole line.
    // Any start offset from the line start until the first non-whitespace character (code start) is normalized
    // to the offset of that non-whitespace character for ease of comparison of various ranges coming from variants and breakpoints.
    return range?.let { max(it.startOffset, codeStartOffset) } ?: codeStartOffset
  }

  @RequiresWriteLock
  private fun insertInlays(document: Document,
                           onlyEditor: Editor?,
                           onlyLine: Int?,
                           inlays: List<SingleInlayDatum>) {
    val editors = onlyEditor?.let { arrayOf(it) } ?: EditorFactory.getInstance().getEditors(document, project)
    for (editor in editors) {
      val inlayModel = editor.inlayModel

      // remove previous inlays
      val startOffset = onlyLine?.let { document.getLineStartOffset(it) } ?: Int.MIN_VALUE
      val endOffset = onlyLine?.let { document.getLineEndOffset(it) } ?: Int.MAX_VALUE
      for (oldInlay in inlayModel.getInlineElementsInRange(startOffset, endOffset, InlineBreakpointInlayRenderer::class.java)) {
        Disposer.dispose(oldInlay)
      }

      // draw new ones
      for ((breakpoint, variant, offset) in inlays) {
        val renderer = InlineBreakpointInlayRenderer(breakpoint, variant)
        val inlay = inlayModel.addInlineElement(offset, renderer)
        inlay?.let { renderer.inlay = it }
      }
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): InlineBreakpointInlayManager =
      project.service<InlineBreakpointInlayManager>()

    /**
     * Returns whether this breakpoint variant covers multiple locations (e.g. "line and lambdas" aka "All").
     * Such variants aren't used with new inline breakpoints inlays because user can manually put separate breakpoints at every location.
     */
    @JvmStatic
    fun isAllVariant(variant: XLineBreakpointType<*>.XLineBreakpointVariant): Boolean {
      // Currently, it's the easiest way to check that it's really multi-location variant.
      // Don't try to check whether the variant is an instance of XLineBreakpointAllVariant, they all are.
      // FIXME[inline-bp]: introduce better way for this or completely get rid of multi-location variants
      return variant.icon === AllIcons.Debugger.MultipleBreakpoints
    }
  }
}
