// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.impl.EditorMouseHoverPopupControl
import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.fileEditor.FileDocumentManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.debugger.impl.shared.XDebuggerExecutionPointManager
import com.intellij.platform.util.coroutines.childScope
import com.intellij.util.asSafely
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.ui.ExecutionPositionUi
import com.intellij.xdebugger.impl.ui.showExecutionPointUi
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow.DROP_OLDEST
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.dropWhile
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus


private val LOG = logger<XDebuggerExecutionPointManagerImpl>()

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class XDebuggerExecutionPointManagerImpl(
  private val project: Project,
  parentScope: CoroutineScope,
) : XDebuggerExecutionPointManager {
  private val coroutineScope: CoroutineScope = parentScope.childScope(javaClass.simpleName, Dispatchers.EDT)

  private val updateRequestFlow = MutableSharedFlow<ExecutionPositionUpdateRequest>(extraBufferCapacity = 1, onBufferOverflow = DROP_OLDEST)

  private val _alternativeSourceKindFlowState = MutableStateFlow<Flow<Boolean>>(emptyFlow())
  override var alternativeSourceKindFlow: Flow<Boolean> by _alternativeSourceKindFlowState::value

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
  override var gutterIconRenderer: GutterIconRenderer? by _gutterIconRendererState::value

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
    val uiScope = coroutineScope.childScope("${javaClass.simpleName}/UI")
    showExecutionPointUi(project, uiScope, executionPointVmState)

    if (!ApplicationManager.getApplication().isUnitTestMode) {
      uiScope.launch(Dispatchers.EDT) {
        executionPointVmState
          .map { it != null }.distinctUntilChanged()
          .dropWhile { !it }  // ignore initial 'false' value
          .collect { hasHighlight ->
            if (Registry.`is`( "debugger.valueTooltipAutoShow")) {
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

    val messageBusConnection = project.getMessageBus().connect(parentScope)

    messageBusConnection.subscribe(FileDocumentManagerListener.TOPIC, object : FileDocumentManagerListener {
      override fun fileContentLoaded(file: VirtualFile, document: Document) {
        updateExecutionPosition(file, true)
      }

      override fun fileContentReloaded(file: VirtualFile, document: Document) {
        updateExecutionPosition(file, true)
      }
    })
  }

  override fun clearExecutionPoint() {
    executionPointVm = null
  }

  override fun setExecutionPoint(
    mainSourcePosition: XSourcePosition?,
    alternativeSourcePosition: XSourcePosition?,
    isTopFrame: Boolean,
    navigationSourceKind: XSourceKind,
  ) {
    if (mainSourcePosition == null && alternativeSourcePosition == null) {
      return clearExecutionPoint()
    }

    executionPointVm = ExecutionPointVmImpl.create(project,
                                                   coroutineScope.childScope(ExecutionPointVm::class.java.simpleName),
                                                   mainSourcePosition,
                                                   alternativeSourcePosition,
                                                   isTopFrame,
                                                   activeSourceKindState,
                                                   gutterIconRendererState,
                                                   updateRequestFlow).also {
      coroutineScope.launch(ClientId.coroutineContext()) {
        it.navigateTo(ExecutionPositionNavigationMode.OPEN, navigationSourceKind)
      }
    }
  }

  override fun isFullLineHighlighterAt(file: VirtualFile, line: Int, project: Project, isToCheckTopFrameOnly: Boolean): Boolean {
    return isCurrentFile(file) && ExecutionPositionUi.isFullLineHighlighterAt(file, line, project, isToCheckTopFrameOnly)
  }

  private fun isCurrentFile(file: VirtualFile): Boolean {
    val pointVm = executionPointVm ?: return false
    return pointVm.mainPositionVm?.file == file ||
           pointVm.alternativePositionVm?.file == file
  }

  internal fun updateExecutionPosition(file: VirtualFile, toNavigate: Boolean) {
    if (isCurrentFile(file)) {
      val updateRequest = ExecutionPositionUpdateRequest(file, toNavigate)
      updateRequestFlow.tryEmit(updateRequest).also { check(it) }
    }
  }

  internal fun showExecutionPosition() {
    coroutineScope.launch(ClientId.coroutineContext()) {
      executionPointVm?.navigateTo(ExecutionPositionNavigationMode.OPEN)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): XDebuggerExecutionPointManagerImpl = project.getService(XDebuggerExecutionPointManagerImpl::class.java)
  }
}

internal data class ExecutionPositionUpdateRequest(val file: VirtualFile, val isToScrollToPosition: Boolean)
