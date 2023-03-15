// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.asSafely
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.XSourcePositionEx.NavigationMode
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*


interface ExecutionPointVm {
  val isTopFrame: Boolean
  val mainPositionVm: ExecutionPositionVm?
  val alternativePositionVm: ExecutionPositionVm?
}

interface ExecutionPositionVm {
  val file: VirtualFile
  val line: Int
  val exactRange: TextRange?
  val isTopFrame: Boolean
  val isActiveSourceKindState: StateFlow<Boolean>
  val gutterVm: ExecutionPositionGutterVm
  val updatesFlow: SharedFlow<NavigationMode>
}

class ExecutionPositionGutterVm(val gutterIconRendererState: StateFlow<GutterIconRenderer?>)


internal class ExecutionPointVmImpl(
  coroutineScope: CoroutineScope,
  executionPoint: ExecutionPoint,
  activeSourceKindState: StateFlow<XSourceKind>,
  gutterIconRendererState: StateFlow<GutterIconRenderer?>,
  updateRequestFlow: Flow<ExecutionPositionUpdateRequest>,
) : ExecutionPointVm {
  override val isTopFrame: Boolean by executionPoint::isTopFrame

  override val mainPositionVm: ExecutionPositionVm?
  override val alternativePositionVm: ExecutionPositionVm?

  init {
    val gutterVm = ExecutionPositionGutterVm(gutterIconRendererState)

    fun createPositionVm(sourceKind: XSourceKind): ExecutionPositionVmImpl? {
      val sourcePosition = executionPoint.getSourcePosition(sourceKind) ?: return null
      val isActiveSourceKindState = activeSourceKindState.mapStateIn(coroutineScope) { it == sourceKind }
      return ExecutionPositionVmImpl(coroutineScope, sourcePosition, isTopFrame, isActiveSourceKindState, gutterVm, updateRequestFlow)
    }

    mainPositionVm = createPositionVm(XSourceKind.MAIN)
    alternativePositionVm = createPositionVm(XSourceKind.ALTERNATIVE)
  }
}

internal class ExecutionPositionVmImpl(
  coroutineScope: CoroutineScope,
  sourcePosition: XSourcePosition,
  override val isTopFrame: Boolean,
  override val isActiveSourceKindState: StateFlow<Boolean>,
  override val gutterVm: ExecutionPositionGutterVm,
  updateRequestFlow: Flow<ExecutionPositionUpdateRequest>,
) : ExecutionPositionVm {
  override val file: VirtualFile by sourcePosition::file
  override val line: Int by sourcePosition::line

  private val highlighterProvider = sourcePosition.asSafely<ExecutionPointHighlighter.HighlighterProvider>()
  override val exactRange: TextRange? get() = highlighterProvider?.highlightRange

  override val updatesFlow: SharedFlow<NavigationMode>

  init {
    val externalUpdateFlow = updateRequestFlow.filter { it.file == file }.map { it.navigationMode }
    val positionUpdateFlow = sourcePosition.asSafely<XSourcePositionEx>()?.positionUpdateFlow ?: emptyFlow()

    updatesFlow = merge(externalUpdateFlow, positionUpdateFlow)
      .shareIn(coroutineScope, SharingStarted.Eagerly)
  }
}


internal fun <T, M> StateFlow<T>.mapStateIn(
  coroutineScope: CoroutineScope,
  started: SharingStarted = SharingStarted.Eagerly,
  transform: (value: T) -> M
): StateFlow<M> {
  return map(transform).stateIn(coroutineScope, started = started, initialValue = transform(value))
}
