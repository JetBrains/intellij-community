package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.application.readAndWriteAction
import com.intellij.openapi.application.writeAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Attachment
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

@Service(Service.Level.PROJECT)
internal class InlineBreakpointInlayManager(private val project: Project, private val scope: CoroutineScope) {

  private val redrawQueue = MergingUpdateQueue(
    "inline breakpoint inlay redraw queue",
    300, true, null, project, null, false
  ).setRestartTimerOnAdd(true)

  private fun areInlineBreakpointsEnabled() = XDebuggerUtil.areInlineBreakpointsEnabled()

  private val SHOW_EVEN_TRIVIAL_KEY = "debugger.show.breakpoints.inline.even.trivial"

  private fun shouldAlwaysShowAllInlays() = Registry.`is`(SHOW_EVEN_TRIVIAL_KEY)

  init {
    EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
      override fun editorCreated(event: EditorFactoryEvent) {
        initializeInNewEditor(event.editor)
      }
    }, project)

    for (key in listOf(XDebuggerUtil.INLINE_BREAKPOINTS_KEY, SHOW_EVEN_TRIVIAL_KEY)) {
      Registry.get(key).addListener(object : RegistryValueListener {
        override fun afterValueChanged(value: RegistryValue) {
          reinitializeAll()
        }
      }, project)
    }
  }

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
  private fun redrawLineQueued(document: Document, line: Int) {
    if (!areInlineBreakpointsEnabled()) return
    redrawQueue.queue(Update.create(Pair(document, line)) {
      redrawLine(document, line)
    })
  }

  fun redrawDocument(e: DocumentEvent) {
    if (!XDebuggerUtil.areInlineBreakpointsEnabled()) return
    val document = e.document
    val file = FileDocumentManager.getInstance().getFile(document)
    if (file == null) return
    val firstLine: Int = document.getLineNumber(e.offset)
    val lastLine: Int = document.getLineNumber(e.offset + e.newLength)
    redrawLineQueued(document, firstLine)
    if (lastLine != firstLine) {
      redrawLineQueued(document, lastLine)
    }
  }

  /**
   * Refresh all inlays in the editor.
   */
  private fun initializeInNewEditor(editor: Editor) {
    if (!areInlineBreakpointsEnabled()) return
    if (!isSuitableEditor(editor)) return
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
  private fun reinitializeAll() {
    val enabled = areInlineBreakpointsEnabled()
    for (editor in EditorFactory.getInstance().allEditors) {
      if (!isSuitableEditor(editor)) continue

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
            getAllExistingInlays(editor.inlayModel).forEach { Disposer.dispose(it) }
          }
        }
      }
    }
  }

  private suspend fun redraw(document: Document, onlyLine: Int?, onlyEditor: Editor?) {
    val startStamp = document.modificationStamp

    fun postponeOnChanged(): Boolean {
      val documentAndPsiAreOutOfSync = !PsiDocumentManager.getInstance(project).isCommitted(document)
      val documentIsOutdated = document.modificationStamp != startStamp
      return if (documentAndPsiAreOutOfSync || documentIsOutdated) {
        redrawQueue.queue(Update.create(Pair(document, onlyLine)) {
          scope.launch {
            redraw(document, onlyLine, onlyEditor)
          }
        })
        true
      }
      else {
        false
      }
    }

    if (postponeOnChanged()) return
    // Double-checked now.

    readAndWriteAction {
      if (postponeOnChanged()) return@readAndWriteAction value(Unit)

      val allBreakpoints = allBreakpointsIn(document)

      val inlays = mutableListOf<SingleInlayDatum>()
      if (onlyLine != null) {
        if (!DocumentUtil.isValidLine(onlyLine, document)) return@readAndWriteAction value(Unit)

        val breakpoints = allBreakpoints.filter { it.line == onlyLine }
        if (!breakpoints.isEmpty()) {
          inlays += collectInlayData(document, onlyLine, breakpoints)
        }
      }
      else {
        for ((line, breakpoints) in allBreakpoints.groupBy { it.line }) {
          // We could process lines concurrently, but it doesn't seem to be really required.
          inlays += collectInlayData(document, line, breakpoints)
        }
      }

      if (postponeOnChanged()) return@readAndWriteAction value(Unit)

      if (onlyLine != null && inlays.isEmpty() &&
          allEditorsFor(document).all { getExistingInlays(it.inlayModel, document, onlyLine).isEmpty() }
      ) {
        // It's a fast path: no need to fire write action to remove inlays if there are already no inlays.
        // It's required to prevent performance degradations due to IDEA-339224,
        // otherwise fast insertion of twenty new lines could lead to 10 seconds of inlay recalculations.
        return@readAndWriteAction value(Unit)
      }

      writeAction {
        if (postponeOnChanged()) return@writeAction

        insertInlays(document, onlyEditor, onlyLine, inlays)
      }
    }
  }

  private fun isSuitableEditor(editor: Editor) =
    !DiffUtil.isDiffEditor(editor)

  private fun allBreakpointsIn(document: Document): Collection<XLineBreakpointImpl<*>> {
    val lineBreakpointManager = (XDebuggerManager.getInstance(project).breakpointManager as XBreakpointManagerImpl).lineBreakpointManager
    return lineBreakpointManager.getDocumentBreakpoints(document)
  }

  private data class SingleInlayDatum(
    val breakpoint: XLineBreakpointImpl<*>?,
    val variant: XLineBreakpointType<*>.XLineBreakpointVariant?,
    val offset: Int,
  )

  @RequiresReadLock
  private fun collectInlayData(document: Document,
                               line: Int,
                               breakpoints: List<XLineBreakpointImpl<*>>): List<SingleInlayDatum> {
    if (!DocumentUtil.isValidLine(line, document)) return emptyList()

    val file = FileDocumentManager.getInstance().getFile(document) ?: return emptyList()
    val linePosition = XSourcePositionImpl.create(file, line)
    val breakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(project, linePosition, null)

    val variants =
      if (breakpointTypes.isNotEmpty()) {
        XDebuggerUtilImpl.getLineBreakpointVariantsSync(project, breakpointTypes, linePosition)
          // No need to show "all" variant in case of the inline breakpoints approach, it's useful only for the popup based one.
          .filter { !it.isMultiVariant }
      }
      else {
        emptyList()
      }

    val codeStartOffset = DocumentUtil.getLineStartIndentedOffset(document, line)

    if (!shouldAlwaysShowAllInlays() &&
        breakpoints.size == 1 &&
        (variants.isEmpty() ||
         variants.size == 1 && areMatching(variants[0], breakpoints[0]))) {
      // No need to show inline variants when there is only one breakpoint and one matching variant (or no variants at all).
      return emptyList()
    }

    return buildList {
      val remainingBreakpoints = breakpoints.toMutableSmartList()
      for (variant in variants) {
        val matchingBreakpoints = breakpoints.filter { areMatching(variant, it) }
        if (matchingBreakpoints.isEmpty()) {
          // Easy case: just draw this inlay as a variant.
          val offset = getBreakpointVariantRangeStartOffset(variant, codeStartOffset)
          add(SingleInlayDatum(null, variant, offset))
        }
        else {
          if (matchingBreakpoints.size >= 2) {
            // We have multiple breakpoints for a single variant, bad luck.
            // Draw them, but report an error.
            reportSingleVariantMultipleBreakpoints(document, line, variant, matchingBreakpoints)
          }
          // Else we have a variant and single corresponding breakpoint.

          for (breakpoint in matchingBreakpoints) {
            val notYetMatched = remainingBreakpoints.remove(breakpoint)
            if (notYetMatched) {
              // If breakpoint was not matched earlier, just draw this inlay as a breakpoint.
              val offset = getBreakpointRangeStartOffset(breakpoint, codeStartOffset)

              // However, if this breakpoint is the only breakpoint, and it covers a whole line, don't draw it.
              val singleLineBreakpoint = breakpoints.size == 1 && getBreakpointHighlightRange(breakpoint) == null

              if (!singleLineBreakpoint || shouldAlwaysShowAllInlays()) {
                add(SingleInlayDatum(breakpoint, variant, offset))
              }
            }
            else {
              // We have multiple variants matching a single breakpoint, bad luck.
              // Don't draw anything new and report an error.
              reportSingleBreakpointMultipleVariants(document, line, breakpoint, variants.filter { areMatching(it, breakpoint) })
            }
          }
        }
      }

      for (breakpoint in remainingBreakpoints) {
        // We have some breakpoints without matched variants.
        // Draw them and report an error.
        reportBreakpointWithoutVariants(document, line, breakpoint)
        val offset = getBreakpointRangeStartOffset(breakpoint, codeStartOffset)
        add(SingleInlayDatum(breakpoint, null, offset))
      }
    }
  }

  @Suppress("UNCHECKED_CAST") // Casts are required for gods of Kotlin-Java type inference.
  private fun areMatching(variant: XLineBreakpointType<*>.XLineBreakpointVariant,
                          breakpoint: XLineBreakpointImpl<*>): Boolean {
    val type = breakpoint.type as XLineBreakpointType<XBreakpointProperties<*>>
    val b = breakpoint as XLineBreakpointImpl<XBreakpointProperties<*>>
    val v = variant as XLineBreakpointType<XBreakpointProperties<*>>.XLineBreakpointVariant

    return type == variant.type && type.variantAndBreakpointMatch(b, v)
  }

  private fun getBreakpointVariantRangeStartOffset(variant: XLineBreakpointType<*>.XLineBreakpointVariant, codeStartOffset: Int): Int {
    val range = variant.highlightRange
    return getLineRangeStartNormalized(range, codeStartOffset)
  }

  private fun getBreakpointRangeStartOffset(breakpoint: XLineBreakpointImpl<*>, codeStartOffset: Int): Int {
    val range = getBreakpointHighlightRange(breakpoint)
    return getLineRangeStartNormalized(range, codeStartOffset)
  }

  @Suppress("UNCHECKED_CAST") // Casts are required for gods of Kotlin-Java type inference.
  private fun getBreakpointHighlightRange(breakpoint: XLineBreakpointImpl<*>): TextRange? {
    val type: XLineBreakpointType<XBreakpointProperties<*>> = breakpoint.type as XLineBreakpointType<XBreakpointProperties<*>>
    val b = breakpoint as XLineBreakpoint<XBreakpointProperties<*>>

    return type.getHighlightRange(b)
  }

  private fun getLineRangeStartNormalized(range: TextRange?, codeStartOffset: Int): Int {
    // Null range represents the whole line.
    // Any start offset from the line start until the first non-whitespace character (code start) is normalized
    // to the offset of that non-whitespace character for ease of comparison of various ranges coming from variants and breakpoints.
    return range?.let { max(it.startOffset, codeStartOffset) } ?: codeStartOffset
  }

  private fun reportSingleVariantMultipleBreakpoints(document: Document, line: Int,
                                                     variant: XLineBreakpointType<*>.XLineBreakpointVariant,
                                                     breakpoints: List<XLineBreakpointImpl<*>>) {
    logger.error("single variant (${formatTextRangeForErrors(variant.highlightRange)}) " +
                 "matches ${breakpoints.size} breakpoints " +
                 "${breakpoints.map { formatTextRangeForErrors(getBreakpointHighlightRange(it)) }} " +
                 "at line ${line + 1}",
                 sourceCodeAttachment(document))
  }

  private fun reportSingleBreakpointMultipleVariants(document: Document, line: Int,
                                                     breakpoint: XLineBreakpointImpl<*>,
                                                     variants: List<XLineBreakpointType<*>.XLineBreakpointVariant>) {
    logger.error("single breakpoint (${formatTextRangeForErrors(getBreakpointHighlightRange(breakpoint))}) " +
                 "matches ${variants.size} variants " +
                 "${variants.map { formatTextRangeForErrors(it.highlightRange) }} " +
                 "at line ${line + 1}",
                 sourceCodeAttachment(document))
  }

  private fun reportBreakpointWithoutVariants(document: Document, line: Int,
                                              breakpoint: XLineBreakpointImpl<*>) {
    logger.error("breakpoint (${formatTextRangeForErrors(getBreakpointHighlightRange(breakpoint))}) " +
                 "matches no variants " +
                 "at line ${line + 1}",
                 sourceCodeAttachment(document))
  }

  private fun formatTextRangeForErrors(range: TextRange?): String =
    range?.toString() ?: "(whole-line)"

  private fun sourceCodeAttachment(document: Document): Attachment =
    Attachment("source.txt", document.text)

  @RequiresWriteLock
  private fun insertInlays(document: Document,
                           onlyEditor: Editor?,
                           onlyLine: Int?,
                           inlays: List<SingleInlayDatum>) {
    if (onlyEditor != null) {
      insertInlays(document, onlyEditor.inlayModel, onlyLine, inlays)
    }
    else {
      for (editor in allEditorsFor(document)) {
        if (!isSuitableEditor(editor)) continue
        insertInlays(document, editor.inlayModel, onlyLine, inlays)
      }
    }
  }

  @RequiresWriteLock
  private fun insertInlays(document: Document,
                           inlayModel: InlayModel,
                           onlyLine: Int?,
                           inlays: List<SingleInlayDatum>) {
    // remove previous inlays
    getExistingInlays(inlayModel, document, onlyLine).forEach { Disposer.dispose(it) }

    // draw new ones
    for ((breakpoint, variant, offset) in inlays) {
      val renderer = InlineBreakpointInlayRenderer(breakpoint, variant)
      val inlay = inlayModel.addInlineElement(offset, renderer)
      inlay?.let { renderer.inlay = it }
    }
  }

  private fun getAllExistingInlays(inlayModel: InlayModel): List<Inlay<out InlineBreakpointInlayRenderer>> {
    return getExistingInlays(inlayModel, Int.MIN_VALUE, Int.MAX_VALUE)
  }

  private fun getExistingInlays(inlayModel: InlayModel, document: Document, onlyLine: Int?): List<Inlay<out InlineBreakpointInlayRenderer>> {
    if (onlyLine == null) return getAllExistingInlays(inlayModel)

    return getExistingInlays(inlayModel,
                             document.getLineStartOffset(onlyLine),
                             document.getLineEndOffset(onlyLine))
  }

  private fun getExistingInlays(inlayModel: InlayModel, startOffset: Int, endOffset: Int): List<Inlay<out InlineBreakpointInlayRenderer>> {
    return inlayModel.getInlineElementsInRange(startOffset, endOffset, InlineBreakpointInlayRenderer::class.java)
  }

  private fun allEditorsFor(document: Document): Array<out Editor> =
    EditorFactory.getInstance().getEditors(document, project)

  companion object {
    private val logger = logger<InlineBreakpointInlayManager>()

    @JvmStatic
    fun getInstance(project: Project): InlineBreakpointInlayManager =
      project.service<InlineBreakpointInlayManager>()
  }
}
