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
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.asSafely
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.XSourcePositionEx.NavigationMode
import com.intellij.xdebugger.impl.settings.XDebuggerSettingManagerImpl
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter
import com.intellij.xdebugger.ui.DebuggerColors
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*
import kotlin.time.Duration.Companion.milliseconds


private val LOG = logger<XDebuggerExecutionPointManager>()

internal class XDebuggerExecutionPointManager(private val project: Project,
                                              parentScope: CoroutineScope) {
  private val coroutineScope: CoroutineScope = parentScope.childScope(Dispatchers.EDT + CoroutineName(javaClass.simpleName))

  private val updateRequestFlow = MutableSharedFlow<PositionUpdateRequest>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)
  private val navigationRequestFlow = MutableSharedFlow<Unit>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

  private val _executionPointState = MutableStateFlow<ExecutionPoint?>(null)
  private val executionPointState: StateFlow<ExecutionPoint?> = _executionPointState.asStateFlow()
  var executionPoint: ExecutionPoint? by _executionPointState::value

  private val _activeSourceKindState = MutableStateFlow(XSourceKind.MAIN)
  private val activeSourceKindState: StateFlow<XSourceKind> = _activeSourceKindState.asStateFlow()
  var activeSourceKind: XSourceKind by _activeSourceKindState::value

  private val _gutterIconRendererState = MutableStateFlow<GutterIconRenderer?>(null)
  private val gutterIconRendererState: StateFlow<GutterIconRenderer?> = _gutterIconRendererState.asStateFlow()
  var gutterIconRenderer: GutterIconRenderer? by _gutterIconRendererState::value

  init {
    presentationFlowFor(XSourceKind.MAIN).launchIn(coroutineScope)
    presentationFlowFor(XSourceKind.ALTERNATIVE).launchIn(coroutineScope)

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

  private fun presentationFlowFor(sourceKind: XSourceKind): Flow<PositionPresentation?> {
    return executionPointState
      .onCompletion { emit(null) }
      .mapLatest {
        if (it == null) {
          delay(25.milliseconds)
        }
        it
      }
      .transformLatest { executionPoint ->
        kotlin.runCatching {
          val sourcePosition = executionPoint?.getSourcePosition(sourceKind) ?: return@transformLatest emit(null)
          val positionUpdateFlow = sourcePosition.asSafely<XSourcePositionEx>()?.positionUpdateFlow ?: emptyFlow()
          val externalUpdateFlow = updateRequestFlow
            .filter { it.file == sourcePosition.file }
            .map { it.navigationMode }

          merge(positionUpdateFlow, externalUpdateFlow)
            .onStart { emit(NavigationMode.OPEN) } // initial open
            .collectLatest { navigationMode ->
              show(sourcePosition, sourceKind, executionPoint.isTopFrame, navigationMode)
            }
        }.getOrLogException(LOG)
      }
  }

  private suspend fun FlowCollector<PositionPresentation>.show(sourcePosition: XSourcePosition,
                                                               sourceKind: XSourceKind,
                                                               isTopFrame: Boolean,
                                                               initialNavigationMode: NavigationMode): Nothing = coroutineScope {
    PositionPresentation(project, this, sourceKind, sourcePosition, isTopFrame,
                         activeSourceKindState, gutterIconRendererState).use { presentation ->
      emit(presentation)

      navigationRequestFlow
        .map { NavigationMode.OPEN }
        .onStart { emit(initialNavigationMode) }
        .debounce(10.milliseconds)
        .onEach { navigationMode ->
          presentation.navigator.navigateTo(navigationMode)
        }.launchIn(this)

      awaitCancellation()
    }
  }

  @RequiresEdt
  fun isFullLineHighlighterAt(file: VirtualFile, line: Int, project: Project, isToCheckTopFrameOnly: Boolean): Boolean {
    return isCurrentFile(file) && PositionHighlight.isFullLineHighlighterAt(file, line, project, isToCheckTopFrameOnly)
  }

  private fun isCurrentFile(file: VirtualFile): Boolean {
    val point = executionPoint ?: return false
    return point.getSourcePosition(XSourceKind.MAIN)?.file == file ||
           point.getSourcePosition(XSourceKind.ALTERNATIVE)?.file == file
  }

  fun updateExecutionPosition(file: VirtualFile, toNavigate: Boolean) {
    if (isCurrentFile(file)) {
      val navigationMode = if (toNavigate) NavigationMode.OPEN else NavigationMode.NONE
      val updateRequest = PositionUpdateRequest(file, navigationMode)
      updateRequestFlow.tryEmit(updateRequest).also { check(it) }
    }
  }

  fun showExecutionPosition() {
    navigationRequestFlow.tryEmit(Unit).also { check(it) }
  }

  private data class PositionUpdateRequest(val file: VirtualFile, val navigationMode: NavigationMode)
}

internal class ExecutionPoint private constructor(
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

  companion object {
    @JvmStatic
    fun create(mainSourcePosition: XSourcePosition?, alternativeSourcePosition: XSourcePosition?, isTopFrame: Boolean): ExecutionPoint? {
      if (mainSourcePosition == null && alternativeSourcePosition == null) return null
      return ExecutionPoint(mainSourcePosition, alternativeSourcePosition, isTopFrame)
    }
  }
}


private class PositionPresentation(
  project: Project,
  coroutineScope: CoroutineScope,
  val sourceKind: XSourceKind,
  sourcePosition: XSourcePosition,
  isTopFrame: Boolean,
  activeSourceKindState: StateFlow<XSourceKind>,
  gutterIconRendererState: StateFlow<GutterIconRenderer?>,
) : AutoCloseable {

  val highlight = PositionHighlight.create(project, sourcePosition, isTopFrame)?.apply {
    gutterIconRenderer = gutterIconRendererState.value
    gutterIconRendererState.onEach(::gutterIconRenderer::set).launchIn(coroutineScope)
  }
  val navigator = PositionNavigator.create(project, sourcePosition, isTopFrame)

  var activeSourceKind: XSourceKind = XSourceKind.MAIN
    @RequiresEdt
    set(value) {
      val isActiveSourceKind = (sourceKind == value)
      highlight?.isActiveSourceKind = isActiveSourceKind
      navigator.isActiveSourceKind = isActiveSourceKind
      field = value
    }

  init {
    activeSourceKind = activeSourceKindState.value
    activeSourceKindState.onEach(::activeSourceKind::set).launchIn(coroutineScope)
  }

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

    @RequiresEdt
    fun isFullLineHighlighterAt(file: VirtualFile, line: Int, project: Project, isToCheckTopFrameOnly: Boolean = false): Boolean {
      val rangeHighlighter = findPositionHighlighterAt(file, line, project) ?: return false
      return isFullLineHighlighter(rangeHighlighter, isToCheckTopFrameOnly)
    }

    private fun findPositionHighlighterAt(file: VirtualFile, line: Int, project: Project): RangeHighlighter? {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
      if (line < 0 || line >= document.lineCount) return null
      val lineStartOffset = document.getLineStartOffset(line)
      val lineEndOffset = document.getLineEndOffset(line)
      val markupModel = DocumentMarkupModel.forDocument(document, project, false) as? MarkupModelEx ?: return null
      var result: RangeHighlighter? = null
      markupModel.processRangeHighlightersOverlappingWith(lineStartOffset, lineEndOffset) { rangeHighlighter ->
        val foundIt = rangeHighlighter.getUserData(ExecutionPointHighlighter.EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY) != null
        if (foundIt) {
          result = rangeHighlighter
        }
        !foundIt
      }
      return result
    }

    private fun isFullLineHighlighter(rangeHighlighter: RangeHighlighter, isToCheckTopFrameOnly: Boolean): Boolean {
      val isTopFrame = rangeHighlighter.getUserData(ExecutionPointHighlighter.EXECUTION_POINT_HIGHLIGHTER_TOP_FRAME_KEY) ?: return false
      return rangeHighlighter.isValid &&
             rangeHighlighter.targetArea == HighlighterTargetArea.LINES_IN_RANGE &&
             (isTopFrame || !isToCheckTopFrameOnly)
    }
  }
}

private class PositionNavigator(
  private val openFileDescriptor: OpenFileDescriptor,
) {
  var isActiveSourceKind: Boolean = false

  private var openedEditor: Editor? = null

  fun navigateTo(navigationMode: NavigationMode) {
    if (navigationMode == NavigationMode.NONE) return

    if (navigationMode == NavigationMode.OPEN && isActiveSourceKind) {
      openedEditor = XDebuggerUtilImpl.createEditor(openFileDescriptor)
    }
    else {
      val fileEditorManager = FileEditorManager.getInstance(openFileDescriptor.project)
      val editor = openedEditor?.takeUnless { it.isDisposed }
                   ?: fileEditorManager.getSelectedEditor(openFileDescriptor.file).asSafely<TextEditor>()?.editor
                   ?: return
      openFileDescriptor.navigateIn(editor)
    }
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
