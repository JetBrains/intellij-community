// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.childScope
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.XSourcePositionEx.NavigationMode
import com.intellij.xdebugger.impl.ui.ExecutionPositionUi
import com.intellij.xdebugger.impl.ui.showExecutionPointUi
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch


private val LOG = logger<XDebuggerExecutionPointManager>()

internal class XDebuggerExecutionPointManager(private val project: Project,
                                              parentScope: CoroutineScope) {
  private val coroutineScope: CoroutineScope = parentScope.childScope(Dispatchers.EDT + CoroutineName(javaClass.simpleName))

  private val updateRequestFlow = MutableSharedFlow<ExecutionPositionUpdateRequest>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

  private val _executionPointState = MutableStateFlow<ExecutionPoint?>(null)
  private val executionPointState: StateFlow<ExecutionPoint?> = _executionPointState.asStateFlow()
  var executionPoint: ExecutionPoint? by _executionPointState::value

  private val _activeSourceKindState = MutableStateFlow(XSourceKind.MAIN)
  private val activeSourceKindState: StateFlow<XSourceKind> = _activeSourceKindState.asStateFlow()
  var activeSourceKind: XSourceKind by _activeSourceKindState::value

  private val _gutterIconRendererState = MutableStateFlow<GutterIconRenderer?>(null)
  private val gutterIconRendererState: StateFlow<GutterIconRenderer?> = _gutterIconRendererState.asStateFlow()
  var gutterIconRenderer: GutterIconRenderer? by _gutterIconRendererState::value

  private val executionPointVmState: StateFlow<ExecutionPointVm?> = executionPointState
    .onCompletion { emit(null) }
    .map { executionPoint ->
      kotlin.runCatching {
        executionPoint?.let {
          ExecutionPointVmImpl(project,
                               coroutineScope, it,
                               activeSourceKindState,
                               gutterIconRendererState,
                               updateRequestFlow)
        }
      }.getOrLogException(LOG)
    }.stateIn(coroutineScope, SharingStarted.Eagerly, initialValue = null)
  private val executionPointVm: ExecutionPointVm? by executionPointVmState::value

  init {
    val uiScope = coroutineScope.childScope(CoroutineName("${javaClass.simpleName}/UI"))
    showExecutionPointUi(project, uiScope, executionPointVmState)

    // navigate when execution point changes
    uiScope.launch {
      executionPointVmState.filterNotNull().collect {
        it.navigateTo(NavigationMode.OPEN)
      }
    }

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      uiScope.launch {
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

  @RequiresEdt
  fun isFullLineHighlighterAt(file: VirtualFile, line: Int, project: Project, isToCheckTopFrameOnly: Boolean): Boolean {
    return isCurrentFile(file) && ExecutionPositionUi.isFullLineHighlighterAt(file, line, project, isToCheckTopFrameOnly)
  }

  private fun isCurrentFile(file: VirtualFile): Boolean {
    val point = executionPoint ?: return false
    return point.getSourcePosition(XSourceKind.MAIN)?.file == file ||
           point.getSourcePosition(XSourceKind.ALTERNATIVE)?.file == file
  }

  fun updateExecutionPosition(file: VirtualFile, toNavigate: Boolean) {
    if (isCurrentFile(file)) {
      val navigationMode = if (toNavigate) NavigationMode.OPEN else NavigationMode.NONE
      val updateRequest = ExecutionPositionUpdateRequest(file, navigationMode)
      updateRequestFlow.tryEmit(updateRequest).also { check(it) }
    }
  }

  fun showExecutionPosition() {
    executionPointVm?.navigateTo(NavigationMode.OPEN)
  }
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

internal data class ExecutionPositionUpdateRequest(val file: VirtualFile, val navigationMode: NavigationMode)
