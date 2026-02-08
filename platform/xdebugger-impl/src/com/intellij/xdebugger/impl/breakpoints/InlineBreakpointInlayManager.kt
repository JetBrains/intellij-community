// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.breakpoints

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.edtWriteAction
import com.intellij.openapi.application.readAndEdtWriteAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diff.impl.DiffUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.Inlay
import com.intellij.openapi.editor.InlayModel
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
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.shared.proxy.InlineLightBreakpoint
import com.intellij.platform.debugger.impl.shared.proxy.InlineVariantWithMatchingBreakpointProxy
import com.intellij.platform.debugger.impl.shared.proxy.XDebugManagerProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointHighlighterRange
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointInlineVariantProxy
import com.intellij.platform.debugger.impl.shared.proxy.XLineBreakpointProxy
import com.intellij.platform.util.coroutines.childScope
import com.intellij.psi.PsiDocumentManager
import com.intellij.util.DocumentUtil
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.isEmpty
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xdebugger.XDebuggerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicLong
import java.util.stream.Stream

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class InlineBreakpointInlayManager(private val project: Project, parentScope: CoroutineScope) {
  private val scope = parentScope.childScope("InlineBreakpoints")

  private val redrawQueue = MergingUpdateQueue.mergingUpdateQueue(
    name = "inline breakpoint inlay redraw queue",
    mergingTimeSpan = 300,
    coroutineScope = scope,
  ).setRestartTimerOnAdd(true)

  private fun areInlineBreakpointsEnabled(virtualFile: VirtualFile?) = XDebuggerUtil.areInlineBreakpointsEnabled(virtualFile)

  private fun areInlineBreakpointsEnabled(document: Document) =
    areInlineBreakpointsEnabled(FileDocumentManager.getInstance().getFile(document))

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

        override fun editorReleased(event: EditorFactoryEvent) {
          XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project).inlineBreakpointsCache.editorReleased(event.editor)
        }
      }, project)

      for (key in listOf(XDebuggerUtil.INLINE_BREAKPOINTS_KEY, InlineBreakpointsVariantsManager.SHOW_EVEN_TRIVIAL_KEY)) {
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
      if (allBreakpoints(document).isEmpty()) {
        // No need to clear inlays in new editor, so we can just skip whole redraw.
        // FIXME[inline-bp]: test DaemonRespondToChangesTest.testLocalInspectionMustReceiveCorrectVisibleRangeViaItsHighlightingSession
        //                   fails if you remove this fast-path,
        //                   it's really strange, investigate it
        return@launch
      }
      redraw(document, null, editor)
    }
  }

  private fun allBreakpoints(document: Document): Collection<XLineBreakpointProxy> =
    XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project).getLineBreakpointManager()
      .getDocumentBreakpointProxies(document)

  private fun getBreakpointLines(document: Document, onlyLine: Int?): Set<Int> {
    var lines: Set<Int> = allBreakpoints(document).map { it.getLine() }.toHashSet()
    if (onlyLine != null) {
      lines = if (onlyLine in lines) setOf(onlyLine) else emptySet()
    }
    return lines
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

  private class RedrawPostponeException : Exception()

  private inner class RedrawSnapshot(
    val document: Document,
    val onlyLine: Int?,
    val onlyEditor: Editor?,
  ) {
    val documentStamp = document.modificationStamp
    val breakpointsStamp = breakpointModificationStamp.get()

    fun postpone(): Nothing {
      doPostpone()
      throw RedrawPostponeException()
    }

    fun checkPostponed() {
      if (shouldBePostponed()) {
        postpone()
      }
    }

    fun postponeOnChanged(): Boolean {
      val isOutdated = shouldBePostponed()

      if (isOutdated) {
        doPostpone()
      }

      return isOutdated
    }

    private fun doPostpone() {
      redrawQueue.queue(Update.create(Pair(document, onlyLine)) {
        scope.launch {
          redraw(document, onlyLine, onlyEditor)
        }
      })
    }

    private fun shouldBePostponed(): Boolean {
      val documentAndPsiAreOutOfSync = !PsiDocumentManager.getInstance(project).isCommitted(document)
      val documentIsOutdated = document.modificationStamp != documentStamp
      val breakpointsAreOutdated = breakpointModificationStamp.get() != breakpointsStamp
      return documentAndPsiAreOutOfSync || documentIsOutdated || breakpointsAreOutdated
    }
  }

  private suspend fun redraw(document: Document, onlyLine: Int?, onlyEditor: Editor?) {
    val noSuitableEditors = withContext(Dispatchers.EDT) { suitableEditorsFor(document).isEmpty() }
    if (noSuitableEditors) return
    val snapshot = RedrawSnapshot(document, onlyLine, onlyEditor)

    val lines = getBreakpointLines(document, onlyLine)

    if (snapshot.postponeOnChanged()) return
    // Double-checked now.

    XDebugManagerProxy.getInstance().getBreakpointManagerProxy(project).inlineBreakpointsCache.performWithVariants(document, lines) {
      redrawVariants(snapshot, it)
    }
  }

  private suspend fun redrawVariants(
    snapshot: RedrawSnapshot,
    variantsByLine: Map<Int, List<InlineVariantWithMatchingBreakpointProxy>>,
  ): Boolean {
    val document = snapshot.document
    val onlyLine = snapshot.onlyLine
    val onlyEditor = snapshot.onlyEditor
    try {
      with(snapshot) {
        checkPostponed()

        return readAndEdtWriteAction {
          if (onlyLine != null && !DocumentUtil.isValidLine(onlyLine, document)) return@readAndEdtWriteAction value(false)
          checkPostponed()
          if (variantsByLine.keys.any { !DocumentUtil.isValidLine(it, document) }) {
            postpone()
          }
          val inlays = variantsByLine.flatMap { (line, variants) ->
            collectInlayData(document, line, variants)
          }
          checkPostponed()

          if (onlyLine != null && inlays.isEmpty() &&
              allEditorsFor(document).allMatch { getExistingInlays(it.inlayModel, document, onlyLine).isEmpty() }
          ) {
            // It's a fast path: no need to fire write action to remove inlays if there are already no inlays.
            // It's required to prevent performance degradations due to IDEA-339224,
            // otherwise fast insertion of twenty new lines could lead to 10 seconds of inlay recalculations.
            return@readAndEdtWriteAction value(false)
          }

          writeAction {
            checkPostponed()

            insertInlays(document, onlyEditor, onlyLine, inlays)

            checkPostponed()
            true
          }
        }
      }
    }
    catch (_: RedrawPostponeException) {
      return false
    }
  }

  private fun isSuitableEditor(editor: Editor) =
    !DiffUtil.isDiffEditor(editor)

  private data class SingleInlayDatum(
    val breakpoint: InlineLightBreakpoint?,
    val variant: XLineBreakpointInlineVariantProxy?,
    val offset: Int,
    val document: Document,
    val line: Int,
  )

  @RequiresReadLock
  context(_: RedrawSnapshot)
  private fun collectInlayData(
    document: Document,
    line: Int,
    variants: List<InlineVariantWithMatchingBreakpointProxy>,
  ): List<SingleInlayDatum> {
    // Any breakpoint offset from the interval between line start until the first non-whitespace character (code start) is normalized
    // to the offset of that non-whitespace character.
    // Any breakpoint offset from the lines below the current line is normalized to the end of this line to prevent inlay migration (like IDEA-348719).
    val lineRange = DocumentUtil.getLineStartIndentedOffset(document, line)..document.getLineEndOffset(line)
    assert(!lineRange.isEmpty())

    return variants.map { (variant, breakpoint) ->
      val offset = if (breakpoint != null) {
        getBreakpointRangeStartOffset(breakpoint, lineRange)
      }
      else {
        getBreakpointVariantRangeStartOffset(variant!!, lineRange)
      }
      SingleInlayDatum(breakpoint, variant, offset, document, line)
    }
  }

  private fun getBreakpointVariantRangeStartOffset(variant: XLineBreakpointInlineVariantProxy, lineRange: IntRange): Int {
    val range = variant.highlightRange
    return getBreakpointRangeStartNormalized(range, lineRange)
  }

  context(snapshot: RedrawSnapshot)
  private fun getBreakpointRangeStartOffset(breakpoint: InlineLightBreakpoint, lineRange: IntRange): Int {
    val range = breakpoint.highlightRange
    if (range !is XLineBreakpointHighlighterRange.Available) {
      snapshot.postpone()
    }
    return getBreakpointRangeStartNormalized(range.range, lineRange)
  }

  private fun getBreakpointRangeStartNormalized(breakpointRange: TextRange?, lineRange: IntRange): Int {
    // Null range represents the whole line.
    return breakpointRange?.startOffset?.coerceIn(lineRange) ?: lineRange.first
  }

  @RequiresWriteLock
  private fun insertInlays(
    document: Document,
    onlyEditor: Editor?,
    onlyLine: Int?,
    inlays: List<SingleInlayDatum>,
  ) {
    if (onlyEditor != null) {
      insertInlays(document, onlyEditor.inlayModel, onlyLine, inlays)
    }
    else {
      for (editor in suitableEditorsFor(document)) {
        insertInlays(document, editor.inlayModel, onlyLine, inlays)
      }
    }
  }

  @RequiresWriteLock
  private fun insertInlays(
    document: Document,
    inlayModel: InlayModel,
    onlyLine: Int?,
    inlays: List<SingleInlayDatum>,
  ) {
    // remove previous inlays
    getExistingInlays(inlayModel, document, onlyLine).forEach { Disposer.dispose(it) }

    // draw new ones
    for ((breakpoint, variant, offset, document, line) in inlays) {
      val renderer = InlineBreakpointInlayRenderer(breakpoint, variant, document, line)
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

  private fun allEditorsFor(document: Document): Stream<Editor> =
    EditorFactory.getInstance().editors(document, project)

  private fun suitableEditorsFor(document: Document): Stream<Editor> =
    allEditorsFor(document).filter { isSuitableEditor(it) }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): InlineBreakpointInlayManager =
      project.service<InlineBreakpointInlayManager>()
  }
}
