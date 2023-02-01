// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class)

package com.intellij.xdebugger.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.project.Project
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.flow.zipWithNext
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter
import com.intellij.xdebugger.ui.DebuggerColors
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.milliseconds


internal class XDebuggerExecutionPointManager(project: Project,
                                              parentScope: CoroutineScope) {
  private val coroutineScope: CoroutineScope = parentScope.childScope(Dispatchers.EDT + CoroutineName(javaClass.simpleName))

  private val activeSourceKindState = MutableStateFlow(XSourceKind.MAIN)
  private val executionPointState = MutableStateFlow<ExecutionPoint?>(null)
  private val gutterIconRendererState = MutableStateFlow<GutterIconRenderer?>(null)

  var activeSourceKind: XSourceKind by activeSourceKindState::value
  var executionPoint: ExecutionPoint? by executionPointState::value
  var gutterIconRenderer: GutterIconRenderer? by gutterIconRendererState::value

  private val highlightState: StateFlow<ExecutionPointHighlight?>

  init {
    val highlightFlow: SharedFlow<ExecutionPointHighlight?> = executionPointState
      .debounce(10.milliseconds)
      .map { executionPoint ->
        executionPoint?.let {
          ExecutionPointHighlight(project, it)
        }
      }.shareIn(coroutineScope, SharingStarted.Eagerly)

    highlightFlow.onCompletion { emit(null) }
      .zipWithNext { oldValue, _ ->
        oldValue?.hideAndDispose()
      }.launchIn(coroutineScope)

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      highlightFlow.onCompletion { emit(null) }
        .map { it != null }.distinctUntilChanged()
        .dropWhile { !it }  // ignore initial 'false' value
        .onEach { hasHighlight ->
          if (hasHighlight) {
            EditorMouseHoverPopupControl.disablePopups(project)
          }
          else {
            EditorMouseHoverPopupControl.enablePopups(project)
          }
        }.launchIn(coroutineScope)
    }

    highlightState = highlightFlow.stateIn(coroutineScope, SharingStarted.Eagerly, initialValue = null)

    with(highlightState.filterNotNull()) {
      combine(activeSourceKindState, ExecutionPointHighlight::activeSourceKind::set).launchIn(coroutineScope)
      combine(gutterIconRendererState, ExecutionPointHighlight::gutterIconRenderer::set).launchIn(coroutineScope)
    }
  }

  @RequiresEdt
  fun isFullLineHighlighter(): Boolean {
    return highlightState.value?.isFullLineHighlighter == true
  }
}

internal class ExecutionPoint(
  private val mainSourcePosition: XSourcePosition?,
  private val alternativeSourcePosition: XSourcePosition?,
  val isTopFrame: Boolean,
) {
  fun getSourcePosition(sourceKind: XSourceKind): XSourcePosition? {
    return when (sourceKind) {
      XSourceKind.MAIN -> mainSourcePosition
      XSourceKind.ALTERNATIVE -> alternativeSourcePosition
    }
  }
}

internal class ExecutionPointHighlight(project: Project, executionPoint: ExecutionPoint) {
  private val highlights: List<PositionHighlight?> = enumValues<XSourceKind>().map { sourceKind ->
    PositionHighlight.create(project, executionPoint, sourceKind)
  }

  private val mainPositionHighlight: PositionHighlight? get() = highlights[XSourceKind.MAIN.ordinal]

  val isFullLineHighlighter: Boolean get() = mainPositionHighlight?.isFullLineHighlighter == true

  var activeSourceKind: XSourceKind by Delegates.observable(XSourceKind.MAIN) { _, _, newValue ->
    highlights.forEach { it?.isActiveSourceKind = (newValue == it?.sourceKind) }
  }

  var gutterIconRenderer: GutterIconRenderer? by Delegates.observable(null) { _, _, newValue ->
    highlights.forEach { it?.gutterIconRenderer = newValue }
  }

  fun hideAndDispose() {
    highlights.forEach { it?.hideAndDispose() }
  }
}


private class PositionHighlight private constructor(
  val sourceKind: XSourceKind,
  private val isTopFrame: Boolean,
  private val rangeHighlighter: RangeHighlighter,
) {
  val isFullLineHighlighter: Boolean
    @RequiresEdt
    get() = (rangeHighlighter.targetArea == HighlighterTargetArea.LINES_IN_RANGE)

  var gutterIconRenderer: GutterIconRenderer? by rangeHighlighter::gutterIconRenderer
  var isActiveSourceKind: Boolean = true
    @RequiresEdt
    set(value) {
      val useTopFrameAttributes = isTopFrame && value
      val attributesKey = if (useTopFrameAttributes) DebuggerColors.EXECUTIONPOINT_ATTRIBUTES else DebuggerColors.NOT_TOP_FRAME_ATTRIBUTES
      rangeHighlighter.setTextAttributesKey(attributesKey)
      rangeHighlighter.putUserData(ExecutionPointHighlighter.EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY, useTopFrameAttributes)
      field = value
    }

  fun hideAndDispose() {
    rangeHighlighter.dispose()
  }

  companion object {
    @RequiresEdt
    fun create(project: Project, executionPoint: ExecutionPoint, sourceKind: XSourceKind): PositionHighlight? {
      val sourcePosition = executionPoint.getSourcePosition(sourceKind) ?: return null
      val rangeHighlighter = createRangeHighlighter(project, sourcePosition) ?: return null
      rangeHighlighter.editorFilter = MarkupEditorFilter { it.editorKind == EditorKind.MAIN_EDITOR }

      return PositionHighlight(sourceKind, executionPoint.isTopFrame, rangeHighlighter)
    }

    private fun createRangeHighlighter(project: Project, sourcePosition: XSourcePosition): RangeHighlighter? {
      val document = FileDocumentManager.getInstance().getDocument(sourcePosition.file) ?: return null

      val line = sourcePosition.line
      if (line < 0 || line >= document.lineCount) return null

      val markupModel = DocumentMarkupModel.forDocument(document, project, true)
      if (sourcePosition is ExecutionPointHighlighter.HighlighterProvider) {
        val range = sourcePosition.highlightRange
        if (range != null) {
          return markupModel.addRangeHighlighter(null, range.startOffset, range.endOffset,
                                                 DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER,
                                                 HighlighterTargetArea.EXACT_RANGE)
        }
      }
      return markupModel.addLineHighlighter(null, line, DebuggerColors.EXECUTION_LINE_HIGHLIGHTERLAYER)
    }
  }
}
