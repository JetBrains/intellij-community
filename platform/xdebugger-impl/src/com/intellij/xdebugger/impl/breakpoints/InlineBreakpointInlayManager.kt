// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.*
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.EditorFactoryEvent
import com.intellij.openapi.editor.event.EditorFactoryListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.IndexNotReadyException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.toMutableSmartList
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xdebugger.XDebuggerUtil
import com.intellij.xdebugger.impl.XDebuggerUtilImpl
import com.intellij.xdebugger.impl.XSourcePositionImpl
import com.intellij.xdebugger.impl.frame.XDebugManagerProxy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.util.concurrent.atomic.AtomicLong

@Service(Service.Level.PROJECT)
internal class InlineBreakpointInlayManager(private val project: Project, parentScope: CoroutineScope) {
  @OptIn(ExperimentalCoroutinesApi::class)
  private val scope = parentScope.childScope("InlineBreakpoints",
                                             if (Registry.`is`(LIMIT_REDRAW_JOBS_COUNT_KEY))
                                                    Dispatchers.Default.limitedParallelism (1)
                                                  else
                                                    Dispatchers.Default)
  private val redrawJobInternalSemaphore = Semaphore(1)

  private val redrawQueue = MergingUpdateQueue.mergingUpdateQueue(
    name = "inline breakpoint inlay redraw queue",
    mergingTimeSpan = 300,
    coroutineScope = parentScope,
  ).setRestartTimerOnAdd(true)

  private fun areInlineBreakpointsEnabled(virtualFile: VirtualFile?) = XDebuggerUtil.areInlineBreakpointsEnabled(virtualFile)

  private fun areInlineBreakpointsEnabled(document: Document) =
    areInlineBreakpointsEnabled(FileDocumentManager.getInstance().getFile(document))

  private fun shouldAlwaysShowAllInlays() = Registry.`is`(Companion.SHOW_EVEN_TRIVIAL_KEY)

  // Breakpoints are modified without borrowing the write lock,
  // so we have to manually track their modifications to prevent races
  // (e.g., between breakpoint removal and inlay drawing, IDEA-341620).
  private val breakpointModificationStamp = AtomicLong(0)

  init {
    val busConnection = project.messageBus.connect()
    if (!project.isDefault) {
      EditorFactory.getInstance().addEditorFactoryListener(object : EditorFactoryListener {
        override fun editorCreated(event: EditorFactoryEvent) {
          initializeInNewEditor(event.editor)
        }
      }, project)

      for (key in listOf(XDebuggerUtil.INLINE_BREAKPOINTS_KEY, Companion.SHOW_EVEN_TRIVIAL_KEY)) {
        Registry.get(key).addListener(object : RegistryValueListener {
          override fun afterValueChanged(value: RegistryValue) {
            reinitializeAll()
          }
        }, project)
      }

      XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project).subscribeOnBreakpointsChanges(busConnection) {
        breakpointModificationStamp.incrementAndGet()
      }
    }

  }

  /**
   * Refresh inlays for the given [document] at given [line].
   *
   * Try to do it as soon as possible.
   */
  fun redrawLine(document: Document, line: Int) {
    if (!areInlineBreakpointsEnabled(document)) return
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
    if (!areInlineBreakpointsEnabled(document)) return
    redrawQueue.queue(Update.create(Pair(document, line)) {
      redrawLine(document, line)
    })
  }

  fun redrawDocument(e: DocumentEvent) {
    val document = e.document
    val file = FileDocumentManager.getInstance().getFile(document)
    if (file == null) return
    if (!XDebuggerUtil.areInlineBreakpointsEnabled(file)) return
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
    if (!areInlineBreakpointsEnabled(editor.virtualFile)) return
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
    for (editor in EditorFactory.getInstance().allEditors) {
      if (!isSuitableEditor(editor)) continue

      val document = editor.document
      val enabled = areInlineBreakpointsEnabled(editor.virtualFile)
      if (enabled) {
        // We might be able to iterate all editors inside redraw,
        // but this procedure is a really cold path and doesn't worse any optimization.
        scope.launch {
          redraw(document, null, editor)
        }
      }
      else {
        scope.launch {
          edtWriteAction {
            getAllExistingInlays(editor.inlayModel).forEach { Disposer.dispose(it) }
          }
        }
      }
    }
  }

  private suspend fun redraw(document: Document, onlyLine: Int?, onlyEditor: Editor?) {
    val documenetStamp = document.modificationStamp
    val breakpointsStamp = breakpointModificationStamp.get()

    fun postponeOnChanged(): Boolean {
      val documentAndPsiAreOutOfSync = !PsiDocumentManager.getInstance(project).isCommitted(document)
      val documentIsOutdated = document.modificationStamp != documenetStamp
      val breakpointsAreOutdated = breakpointModificationStamp.get() != breakpointsStamp
      val isOutdated = documentAndPsiAreOutOfSync || documentIsOutdated || breakpointsAreOutdated

      if (isOutdated) {
        redrawQueue.queue(Update.create(Pair(document, onlyLine)) {
          scope.launch {
            redraw(document, onlyLine, onlyEditor)
          }
        })
      }

      return isOutdated
    }

    suspend fun withSemaphorePermit(action: suspend () -> Unit) {
      if (!Registry.`is`(LIMIT_REDRAW_JOBS_COUNT_KEY)) {
        action()
        return
      }
      redrawJobInternalSemaphore.withPermit {
        action()
      }
    }

    if (postponeOnChanged()) return
    // Double-checked now.

    withSemaphorePermit {
      readAndEdtWriteAction {
        if (postponeOnChanged()) return@readAndEdtWriteAction value(Unit)

        val allBreakpoints = allBreakpointsIn(document)

        val inlays = mutableListOf<SingleInlayDatum>()
        if (onlyLine != null) {
          if (!DocumentUtil.isValidLine(onlyLine, document)) return@readAndEdtWriteAction value(Unit)

          val breakpoints = allBreakpoints.filter { it.getLine() == onlyLine }
          if (!breakpoints.isEmpty()) {
            inlays += collectInlayData(document, onlyLine, breakpoints)
          }
        }
        else {
          for ((line, breakpoints) in allBreakpoints.groupBy { it.getLine() }) {
            // We could process lines concurrently, but it doesn't seem to be really required.
            inlays += collectInlayData(document, line, breakpoints)
          }
        }

        if (postponeOnChanged()) return@readAndEdtWriteAction value(Unit)

        if (onlyLine != null && inlays.isEmpty() &&
            allEditorsFor(document).all { getExistingInlays(it.inlayModel, document, onlyLine).isEmpty() }
        ) {
          // It's a fast path: no need to fire write action to remove inlays if there are already no inlays.
          // It's required to prevent performance degradations due to IDEA-339224,
          // otherwise fast insertion of twenty new lines could lead to 10 seconds of inlay recalculations.
          return@readAndEdtWriteAction value(Unit)
        }

        writeAction {
          if (postponeOnChanged()) return@writeAction

          insertInlays(document, onlyEditor, onlyLine, inlays)

          if (postponeOnChanged()) return@writeAction
        }
      }
    }
  }

  private fun isSuitableEditor(editor: Editor) =
    !DiffUtil.isDiffEditor(editor)

  private fun allBreakpointsIn(document: Document): Collection<XLineBreakpointProxy> {
    val lineBreakpointManager = XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project).getLineBreakpointManager()
    return lineBreakpointManager.getDocumentBreakpointProxies(document)
  }

  private data class SingleInlayDatum(
    val breakpoint: XLineBreakpointProxy?,
    val variant: XLineBreakpointInlineVariantProxy?,
    val offset: Int,
  )

  @RequiresReadLock
  private fun collectInlayData(
    document: Document,
    line: Int,
    breakpoints: List<XLineBreakpointProxy>,
  ): List<SingleInlayDatum> {
    if (!DocumentUtil.isValidLine(line, document)) return emptyList()

    val file = FileDocumentManager.getInstance().getFile(document) ?: return emptyList()
    val linePosition = XSourcePositionImpl.create(file, line)

    val variants = try {
      val breakpointTypes = XBreakpointUtil.getAvailableLineBreakpointTypes(project, linePosition, null)
      if (breakpointTypes.isNotEmpty()) {
        // TODO move it to XDebugManagerProxy
        XDebuggerUtilImpl.getLineBreakpointVariantsSync(project, breakpointTypes, linePosition)
          .filter { it.shouldUseAsInlineVariant() }
          .map { it.asProxy() }
      } else {
        emptyList()
      }
    } catch (_: IndexNotReadyException) {
      emptyList()
    }

    // Any breakpoint offset from the interval between line start until the first non-whitespace character (code start) is normalized
    // to the offset of that non-whitespace character.
    // Any breakpoint offset from the lines below the current line is normalized to the end of this line to prevent inlay migration (like IDEA-348719).
    val lineRange = DocumentUtil.getLineStartIndentedOffset(document, line)..document.getLineEndOffset(line)
    assert(!lineRange.isEmpty())

    if (!shouldAlwaysShowAllInlays() &&
        breakpoints.size == 1 &&
        (variants.isEmpty() ||
         variants.size == 1 && variants.first().isMatching(breakpoints.first()))) {
      // No need to show inline variants when there is only one breakpoint and one matching variant (or no variants at all).
      return emptyList()
    }

    return buildList {
      val remainingBreakpoints = breakpoints.toMutableSmartList()
      for (variant in variants) {
        val matchingBreakpoints = breakpoints.filter { variant.isMatching(it) }
        if (matchingBreakpoints.isEmpty()) {
          // Easy case: just draw this inlay as a variant.
          val offset = getBreakpointVariantRangeStartOffset(variant, lineRange)
          add(SingleInlayDatum(null, variant, offset))
        }
        else {
          // We might have multiple breakpoints for a single variant, bad luck. Still draw them.
          // Otherwise, we have a variant and single corresponding breakpoint.
          for (breakpoint in matchingBreakpoints) {
            val notYetMatched = remainingBreakpoints.remove(breakpoint)
            if (notYetMatched) {
              // If breakpoint was not matched earlier, just draw this inlay as a breakpoint.
              val offset = getBreakpointRangeStartOffset(breakpoint, lineRange)

              // However, if this breakpoint is the only breakpoint, and all variant highlighters are inside its range, don't draw it.
              val singleLineBreakpoint = breakpoints.size == 1 && breakpointHasTheBiggestRange(breakpoint, variants)

              if (!singleLineBreakpoint || shouldAlwaysShowAllInlays()) {
                add(SingleInlayDatum(breakpoint, variant, offset))
              }
            }
            else {
              // We have multiple variants matching a single breakpoint, bad luck.
              // Don't draw anything new.
            }
          }
        }
      }

      for (breakpoint in remainingBreakpoints) {
        // We have some breakpoints without matched variants.
        // Draw them.
        val offset = getBreakpointRangeStartOffset(breakpoint, lineRange)
        add(SingleInlayDatum(breakpoint, null, offset))
      }
    }
  }

  private fun breakpointHasTheBiggestRange(breakpoint: XLineBreakpointProxy, variants: List<XLineBreakpointInlineVariantProxy>): Boolean {
    val rangeAvailability = breakpoint.getHighlightRange() as? XLineBreakpointHighlighterRange.Available ?: return false
    val range = rangeAvailability.range
    if (range == null) {
      return true
    }

    return variants.all {
      val variantRange = it.highlightRange ?: return@all false
      return@all range.contains(variantRange)
    }
  }

  private fun getBreakpointVariantRangeStartOffset(variant: XLineBreakpointInlineVariantProxy, lineRange: IntRange): Int {
    val range = variant.highlightRange
    return getBreakpointRangeStartNormalized(range, lineRange)
  }

  private fun getBreakpointRangeStartOffset(breakpoint: XLineBreakpointProxy, lineRange: IntRange): Int {
    val range = breakpoint.getHighlightRange()
    if (range !is XLineBreakpointHighlighterRange.Available) return lineRange.first
    return getBreakpointRangeStartNormalized(range.range, lineRange)
  }

  private fun getBreakpointRangeStartNormalized(breakpointRange: TextRange?, lineRange: IntRange): Int {
    // Null range represents the whole line.
    return breakpointRange?.startOffset?.coerceIn(lineRange) ?: lineRange.first
  }

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
    @JvmStatic
    fun getInstance(project: Project): InlineBreakpointInlayManager =
      project.service<InlineBreakpointInlayManager>()

    private const val LIMIT_REDRAW_JOBS_COUNT_KEY = "debugger.limit.inline.breakpoints.jobs.count"
    private const val SHOW_EVEN_TRIVIAL_KEY = "debugger.show.breakpoints.inline.even.trivial"
  }
}
