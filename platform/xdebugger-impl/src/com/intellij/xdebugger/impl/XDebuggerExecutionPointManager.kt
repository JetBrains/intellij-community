// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.asSafely
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.ui.ExecutionPositionUi
import com.intellij.xdebugger.impl.ui.showExecutionPointUi
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*


private val LOG = logger<XDebuggerExecutionPointManager>()

internal class XDebuggerExecutionPointManager(private val project: Project,
                                              parentScope: CoroutineScope) {
  private val coroutineScope: CoroutineScope = parentScope.childScope(Dispatchers.EDT + CoroutineName(javaClass.simpleName))

  private val updateRequestFlow = MutableSharedFlow<ExecutionPositionUpdateRequest>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

  private val _alternativeSourceKindFlowState = MutableStateFlow<Flow<Boolean>>(emptyFlow())
  var alternativeSourceKindFlow: Flow<Boolean> by _alternativeSourceKindFlowState::value

  @OptIn(ExperimentalCoroutinesApi::class)
  private val activeSourceKindState: StateFlow<XSourceKind> =
    _alternativeSourceKindFlowState
      .flatMapLatest { isAlternativeFlow ->
        isAlternativeFlow.map { isAlternativeSourceKind ->
          if (isAlternativeSourceKind) XSourceKind.ALTERNATIVE else XSourceKind.MAIN
        }.catch { t -> LOG.error(t) }
      }
      .stateIn(coroutineScope, started = SharingStarted.Eagerly, initialValue = XSourceKind.MAIN)

  private val _gutterIconRendererState = MutableStateFlow<GutterIconRenderer?>(null)
  private val gutterIconRendererState: StateFlow<GutterIconRenderer?> = _gutterIconRendererState.asStateFlow()
  var gutterIconRenderer: GutterIconRenderer? by _gutterIconRendererState::value

  private val executionPointVmState = MutableStateFlow<ExecutionPointVm?>(null)
  private var executionPointVm: ExecutionPointVm?
    get() = executionPointVmState.value
    set(value) {
      executionPointVmState.update { oldValue ->
        oldValue.asSafely<ExecutionPointVmImpl>()?.coroutineScope?.cancel()
        value
      }
    }

  init {
    val uiScope = coroutineScope.childScope(CoroutineName("${javaClass.simpleName}/UI"))
    showExecutionPointUi(project, uiScope, executionPointVmState)

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      uiScope.launch(Dispatchers.EDT) {
        executionPointVmState
          .map { it != null }.distinctUntilChanged()
          .dropWhile { !it }  // ignore initial 'false' value
          .collect { hasHighlight ->
            if (hasHighlight) {
              EditorMouseHoverPopupControl.disablePopups(project)
            }
            else {
              EditorMouseHoverPopupControl.enablePopups(project)
            }
          }
      }
    }
  }

  fun clearExecutionPoint() {
    executionPointVm = null
  }

  fun setExecutionPoint(mainSourcePosition: XSourcePosition?,
                        alternativeSourcePosition: XSourcePosition?,
                        isTopFrame: Boolean,
                        navigationSourceKind: XSourceKind) {
    if (mainSourcePosition == null && alternativeSourcePosition == null) {
      return clearExecutionPoint()
    }

    executionPointVm = ExecutionPointVmImpl.create(project,
                                                   coroutineScope.childScope(CoroutineName(ExecutionPointVm::class.java.simpleName)),
                                                   mainSourcePosition,
                                                   alternativeSourcePosition,
                                                   isTopFrame,
                                                   activeSourceKindState,
                                                   gutterIconRendererState,
                                                   updateRequestFlow).also {
      coroutineScope.launch {
        it.navigateTo(ExecutionPositionNavigationMode.OPEN, navigationSourceKind)
      }
    }
  }

  @RequiresEdt
  fun isFullLineHighlighterAt(file: VirtualFile, line: Int, project: Project, isToCheckTopFrameOnly: Boolean): Boolean {
    return isCurrentFile(file) && ExecutionPositionUi.isFullLineHighlighterAt(file, line, project, isToCheckTopFrameOnly)
  }

  private fun isCurrentFile(file: VirtualFile): Boolean {
    val pointVm = executionPointVm ?: return false
    return pointVm.mainPositionVm?.file == file ||
           pointVm.alternativePositionVm?.file == file
  }

  fun updateExecutionPosition(file: VirtualFile, toNavigate: Boolean) {
    if (isCurrentFile(file)) {
      val updateRequest = ExecutionPositionUpdateRequest(file, toNavigate)
      updateRequestFlow.tryEmit(updateRequest).also { check(it) }
    }
  }

  fun showExecutionPosition() {
    coroutineScope.launch {
      executionPointVm?.navigateTo(ExecutionPositionNavigationMode.OPEN)
    }
  }
}

internal data class ExecutionPositionUpdateRequest(val file: VirtualFile, val isToScrollToPosition: Boolean)
