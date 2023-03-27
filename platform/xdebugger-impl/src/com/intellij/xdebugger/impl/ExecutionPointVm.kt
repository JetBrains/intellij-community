// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.asSafely
import com.intellij.util.flow.mapStateIn
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*


interface ExecutionPointVm {
  val isTopFrame: Boolean
  val mainPositionVm: ExecutionPositionVm?
  val alternativePositionVm: ExecutionPositionVm?

  fun navigateTo(navigationMode: ExecutionPositionNavigationMode, sourceKind: XSourceKind? = null)
}

interface ExecutionPositionVm {
  val file: VirtualFile
  val line: Int
  val exactRange: TextRange?
  val isTopFrame: Boolean
  val isActiveSourceKindState: StateFlow<Boolean>
  val gutterVm: ExecutionPositionGutterVm
  val invalidationUpdateFlow: Flow<Unit>

  fun navigateTo(navigationMode: ExecutionPositionNavigationMode)
}

class ExecutionPositionGutterVm(val gutterIconRendererState: StateFlow<GutterIconRenderer?>)


internal class ExecutionPointVmImpl(
  project: Project,
  coroutineScope: CoroutineScope,
  executionPoint: ExecutionPoint,
  activeSourceKindState: StateFlow<XSourceKind>,
  gutterIconRendererState: StateFlow<GutterIconRenderer?>,
  updateRequestFlow: Flow<ExecutionPositionUpdateRequest>,
) : ExecutionPointVm {
  override val isTopFrame: Boolean by executionPoint::isTopFrame

  override val mainPositionVm: ExecutionPositionVm?
  override val alternativePositionVm: ExecutionPositionVm?

  private val activeSourceKind: XSourceKind by activeSourceKindState::value

  init {
    val gutterVm = ExecutionPositionGutterVm(gutterIconRendererState)

    fun createPositionVm(sourceKind: XSourceKind): ExecutionPositionVmImpl? {
      val sourcePosition = executionPoint.getSourcePosition(sourceKind) ?: return null
      val isActiveSourceKindState = activeSourceKindState.mapStateIn(coroutineScope) { it == sourceKind }
      return ExecutionPositionVmImpl(project, coroutineScope, sourcePosition, isTopFrame, isActiveSourceKindState, gutterVm, updateRequestFlow)
    }

    mainPositionVm = createPositionVm(XSourceKind.MAIN)
    alternativePositionVm = createPositionVm(XSourceKind.ALTERNATIVE)
  }

  override fun navigateTo(navigationMode: ExecutionPositionNavigationMode, sourceKind: XSourceKind?) {
    val openSourceKind = sourceKind ?: activeSourceKind
    val syncSourceKind = if (openSourceKind == XSourceKind.MAIN) XSourceKind.ALTERNATIVE else XSourceKind.MAIN
    val openPositionVm = positionVmFor(openSourceKind)
    val syncPositionVm = positionVmFor(syncSourceKind)
    syncPositionVm?.navigateTo(navigationMode.coerceAtMost(ExecutionPositionNavigationMode.SCROLL))
    openPositionVm?.navigateTo(navigationMode)
  }

  private fun positionVmFor(sourceKind: XSourceKind): ExecutionPositionVm? {
    return when (sourceKind) {
      XSourceKind.MAIN -> mainPositionVm
      XSourceKind.ALTERNATIVE -> alternativePositionVm
    }
  }
}

internal class ExecutionPositionVmImpl(
  project: Project,
  private val coroutineScope: CoroutineScope,
  private val sourcePosition: XSourcePosition,
  override val isTopFrame: Boolean,
  override val isActiveSourceKindState: StateFlow<Boolean>,
  override val gutterVm: ExecutionPositionGutterVm,
  updateRequestFlow: Flow<ExecutionPositionUpdateRequest>,
) : ExecutionPositionVm {
  override val file: VirtualFile by sourcePosition::file
  override val line: Int by sourcePosition::line

  override val exactRange: TextRange? get() = sourcePosition.asSafely<ExecutionPointHighlighter.HighlighterProvider>()?.highlightRange

  private val navigationAwareUpdateFlow: Flow<Boolean> = run {
    val externalUpdateFlow = updateRequestFlow.filter { it.file == file }.map { it.isToScrollToPosition }
    val positionUpdateFlow = sourcePosition.asSafely<XSourcePositionEx>()?.positionUpdateFlow ?: emptyFlow()

    merge(externalUpdateFlow, positionUpdateFlow)
      .shareIn(coroutineScope, SharingStarted.Eagerly, replay = 1)
  }
  private val navigator = ExecutionPositionNavigator(project, coroutineScope, sourcePosition, isTopFrame, navigationAwareUpdateFlow)

  override val invalidationUpdateFlow: Flow<Unit> = navigationAwareUpdateFlow.map { }

  override fun navigateTo(navigationMode: ExecutionPositionNavigationMode) {
    navigator.navigateTo(navigationMode)
  }
}
