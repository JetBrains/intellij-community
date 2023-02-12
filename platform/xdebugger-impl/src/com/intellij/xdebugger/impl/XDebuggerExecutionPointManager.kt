// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)

package com.intellij.xdebugger.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.enumMapOf
import com.intellij.util.io.MultiCloseable
import com.intellij.util.resettableLazy
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter
import com.intellij.xdebugger.ui.DebuggerColors
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.*
import kotlin.properties.Delegates
import kotlin.time.Duration.Companion.milliseconds


private val LOG = logger<XDebuggerExecutionPointManager>()

internal class XDebuggerExecutionPointManager(private val project: Project,
                                              parentScope: CoroutineScope) {
  private val coroutineScope: CoroutineScope = parentScope.childScope(Dispatchers.EDT + CoroutineName(javaClass.simpleName))

  private val navigationRequestFlow = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  private val _executionPointState = MutableStateFlow<ExecutionPoint?>(null)
  private val executionPointState: StateFlow<ExecutionPoint?> = _executionPointState.asStateFlow()
  var executionPoint: ExecutionPoint? by _executionPointState::value

  private val _activeSourceKindState = MutableStateFlow(XSourceKind.MAIN)
  private val activeSourceKindState: StateFlow<XSourceKind> = _activeSourceKindState.asStateFlow()
  var activeSourceKind: XSourceKind by _activeSourceKindState::value

  private val _gutterIconRendererState = MutableStateFlow<GutterIconRenderer?>(null)
  private val gutterIconRendererState: StateFlow<GutterIconRenderer?> = _gutterIconRendererState.asStateFlow()
  var gutterIconRenderer: GutterIconRenderer? by _gutterIconRendererState::value

  private val presentationState: StateFlow<ExecutionPointPresentation?> = presentationFlow()
    .stateIn(coroutineScope, SharingStarted.Eagerly, initialValue = null)

  private val presentation by presentationState::value

  init {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      executionPointState
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
  }

  private fun presentationFlow(): Flow<ExecutionPointPresentation?> {
    return executionPointState
      .onCompletion { emit(null) }
      .transformLatest { executionPoint ->
        @Suppress("RemoveExplicitTypeArguments") // complaints about Nothing
        kotlin.runCatching {
          if (executionPoint == null) return@transformLatest emit(null)
          show(executionPoint)
        }.getOrLogException<Nothing>(LOG)
      }
  }

  private suspend fun FlowCollector<ExecutionPointPresentation>.show(executionPoint: ExecutionPoint): Nothing = coroutineScope {
    ExecutionPointPresentation(project, executionPoint).use { presentation ->
      emit(presentation)

      activeSourceKindState.onEach {
        presentation.activeSourceKind = it
      }.launchIn(this)

      gutterIconRendererState.onEach {
        presentation.gutterIconRenderer = it
      }.launchIn(this)

      navigationRequestFlow
        .debounce(10.milliseconds)
        .onEach {
          presentation.navigateTo()
        }.launchIn(this)

      awaitCancellation()
    }
  }

  @RequiresEdt
  fun isFullLineHighlighterAt(position: XSourcePosition): Boolean {
    return isFullLineHighlighterAt(position.file, position.line)
  }

  @RequiresEdt
  fun isFullLineHighlighterAt(file: VirtualFile, line: Int): Boolean {
    return presentation?.isFullLineHighlighterAt(file, line) == true
  }

  fun showExecutionPosition() {
    navigationRequestFlow.tryEmit(Unit).also { check(it) }
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

internal class ExecutionPointPresentation(project: Project, executionPoint: ExecutionPoint) : MultiCloseable() {
  private val presentationMap = enumMapOf<XSourceKind, PositionPresentation?>().apply {
    enumValues<XSourceKind>().associateWithTo(this) { sourceKind ->
      val sourcePosition = executionPoint.getSourcePosition(sourceKind) ?: return@associateWithTo null
      PositionPresentation(project, sourceKind, sourcePosition, executionPoint.isTopFrame).also(::registerCloseable)
    }
  }
  private val presentations: Collection<PositionPresentation?> = presentationMap.values

  private val mainPositionHighlight: PositionHighlight? get() = presentationMap[XSourceKind.MAIN]?.highlight

  var activeSourceKind: XSourceKind by Delegates.observable(XSourceKind.MAIN) { _, _, newValue ->
    presentations.forEach { it?.highlight?.isActiveSourceKind = (newValue == it?.sourceKind) }
  }

  var gutterIconRenderer: GutterIconRenderer? by Delegates.observable(null) { _, _, newValue ->
    presentations.forEach { it?.highlight?.gutterIconRenderer = newValue }
  }

  fun isFullLineHighlighterAt(file: VirtualFile, line: Int): Boolean {
    return mainPositionHighlight?.isFullLineHighlighterAt(file, line) == true
  }

  fun navigateTo() {
    presentationMap[activeSourceKind]?.navigator?.navigateTo()
  }
}

private class PositionPresentation(
  project: Project,
  val sourceKind: XSourceKind,
  sourcePosition: XSourcePosition,
  isTopFrame: Boolean,
) : AutoCloseable {

  val highlight = PositionHighlight.create(project, sourcePosition, isTopFrame)
  val navigator = PositionNavigator.create(project, sourcePosition, isTopFrame)

  override fun close() {
    highlight?.hideAndDispose()
    navigator.disposeDescriptor()
  }
}

private class PositionHighlight private constructor(
  private val isTopFrame: Boolean,
  private val rangeHighlighter: RangeHighlighter,
) {
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

  fun isFullLineHighlighterAt(file: VirtualFile, line: Int): Boolean {
    return rangeHighlighter.isValid &&
           rangeHighlighter.targetArea == HighlighterTargetArea.LINES_IN_RANGE &&
           FileDocumentManager.getInstance().getFile(rangeHighlighter.document) == file &&
           rangeHighlighter.document.getLineNumber(rangeHighlighter.startOffset) == line
  }

  fun hideAndDispose() {
    rangeHighlighter.dispose()
  }

  companion object {
    @RequiresEdt
    fun create(project: Project, sourcePosition: XSourcePosition, isTopFrame: Boolean): PositionHighlight? {
      val rangeHighlighter = createRangeHighlighter(project, sourcePosition) ?: return null
      rangeHighlighter.editorFilter = MarkupEditorFilter { it.editorKind == EditorKind.MAIN_EDITOR }

      return PositionHighlight(isTopFrame, rangeHighlighter)
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

private class PositionNavigator(
  private val openFileDescriptor: OpenFileDescriptor,
) {
  private val navigateToEditorLazy = resettableLazy {
    XDebuggerUtilImpl.createEditor(openFileDescriptor)
  }

  fun navigateTo(): Editor? {
    val editor = navigateToEditorLazy.value?.takeUnless { it.isDisposed }
    if (editor != null) return editor
    navigateToEditorLazy.reset()
    return navigateToEditorLazy.value
  }

  fun disposeDescriptor() {
    openFileDescriptor.dispose()
  }

  companion object {
    fun create(project: Project, sourcePosition: XSourcePosition, isTopFrame: Boolean): PositionNavigator {
      val openFileDescriptor = createOpenFileDescriptor(project, sourcePosition).apply {
        isUseCurrentWindow = false //see IDEA-125645 and IDEA-63459
        isUsePreviewTab = true
        setScrollType(scrollType(isTopFrame))
      }
      return PositionNavigator(openFileDescriptor)
    }

    private fun createOpenFileDescriptor(project: Project, position: XSourcePosition): OpenFileDescriptor {
      val navigatable = position.createNavigatable(project)
      return if (navigatable is OpenFileDescriptor) {
        navigatable
      }
      else {
        XDebuggerUtilImpl.createOpenFileDescriptor(project, position)
      }
    }

    private fun scrollType(isTopFrame: Boolean): ScrollType {
      if (XDebuggerSettingManagerImpl.getInstanceImpl().generalSettings.isScrollToCenter) return ScrollType.CENTER
      return if (isTopFrame) ScrollType.MAKE_VISIBLE else ScrollType.CENTER
    }
  }
}
