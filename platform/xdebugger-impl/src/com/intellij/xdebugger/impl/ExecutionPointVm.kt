// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.editor.markup.GutterIconRenderer
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.util.coroutines.flow.mapStateIn
import com.intellij.util.asSafely
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.impl.ui.ExecutionPointHighlighter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.*
import org.jetbrains.annotations.ApiStatus


@ApiStatus.Internal
interface ExecutionPointVm {
  val isTopFrame: Boolean
  val mainPositionVm: ExecutionPositionVm?
  val alternativePositionVm: ExecutionPositionVm?

  suspend fun navigateTo(navigationMode: ExecutionPositionNavigationMode, sourceKind: XSourceKind? = null)
}

@ApiStatus.Internal
interface ExecutionPositionVm {
  val file: VirtualFile
  val line: Int
  val exactRange: TextRange?
  val isTopFrame: Boolean
  val isActiveSourceKindState: StateFlow<Boolean>
  val gutterVm: ExecutionPositionGutterVm
  val invalidationUpdateFlow: Flow<Unit>

  suspend fun navigateTo(navigationMode: ExecutionPositionNavigationMode, isActiveSourceKind: Boolean)
}

@ApiStatus.Internal
class ExecutionPositionGutterVm(val gutterIconRendererState: StateFlow<GutterIconRenderer?>)


internal class ExecutionPointVmImpl(
  internal val coroutineScope: CoroutineScope,
  override val mainPositionVm: ExecutionPositionVm?,
  override val alternativePositionVm: ExecutionPositionVm?,
  override val isTopFrame: Boolean,
  activeSourceKindState: StateFlow<XSourceKind>,
) : ExecutionPointVm {
  private val activeSourceKind: XSourceKind by activeSourceKindState::value

  override suspend fun navigateTo(navigationMode: ExecutionPositionNavigationMode, sourceKind: XSourceKind?) {
    val effectiveSourceKind = sourceKind ?: activeSourceKind
    mainPositionVm?.navigateTo(navigationMode, isActiveSourceKind = effectiveSourceKind == XSourceKind.MAIN)
    alternativePositionVm?.navigateTo(navigationMode, isActiveSourceKind = effectiveSourceKind == XSourceKind.ALTERNATIVE)
  }

  companion object {
    fun create(
      project: Project,
      coroutineScope: CoroutineScope,
      mainSourcePosition: XSourcePosition?,
      alternativeSourcePosition: XSourcePosition?,
      isTopFrame: Boolean,
      activeSourceKindState: StateFlow<XSourceKind>,
      gutterIconRendererState: StateFlow<GutterIconRenderer?>,
      updateRequestFlow: Flow<ExecutionPositionUpdateRequest>,
    ): ExecutionPointVmImpl {
      val gutterVm = ExecutionPositionGutterVm(gutterIconRendererState)

      fun createPositionVm(sourcePosition: XSourcePosition?, sourceKind: XSourceKind): ExecutionPositionVm? {
        if (sourcePosition == null) return null
        val isActiveSourceKindState = activeSourceKindState.mapStateIn(coroutineScope) { it == sourceKind }
        return ExecutionPositionVmImpl(project, coroutineScope, sourcePosition, isTopFrame, isActiveSourceKindState, gutterVm,
                                       updateRequestFlow)
      }

      val mainPositionVm = createPositionVm(mainSourcePosition, XSourceKind.MAIN)
      val alternativePositionVm = createPositionVm(alternativeSourcePosition, XSourceKind.ALTERNATIVE)

      return ExecutionPointVmImpl(coroutineScope, mainPositionVm, alternativePositionVm, isTopFrame, activeSourceKindState)
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

  override suspend fun navigateTo(navigationMode: ExecutionPositionNavigationMode, isActiveSourceKind: Boolean) {
    navigator.navigateTo(navigationMode, isActiveSourceKind)
  }
}
