// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXDebuggerEvaluator
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.createFrontendXDebuggerEvaluator
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XDebugSessionDto
import com.intellij.xdebugger.impl.rpc.XDebugSessionState
import com.intellij.xdebugger.impl.rpc.XValueMarkerId
import com.intellij.xdebugger.impl.rpc.sourcePosition
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.supervisorScope

internal class FrontendXDebuggerSession(
  private val project: Project,
  scope: CoroutineScope,
  sessionDto: XDebugSessionDto,
) {
  private val cs = scope.childScope("Session ${sessionDto.id}")
  private val localEditorsProvider = sessionDto.editorsProviderDto.editorsProvider
  val id = sessionDto.id
  val evaluator: StateFlow<FrontendXDebuggerEvaluator?> =
    channelFlow {
      XDebugSessionApi.getInstance().currentEvaluator(id).collectLatest { evaluatorDto ->
        if (evaluatorDto == null) {
          send(null)
          return@collectLatest
        }
        supervisorScope {
          val evaluator = createFrontendXDebuggerEvaluator(project, this, evaluatorDto)
          send(evaluator)
          awaitCancellation()
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  val sourcePosition: StateFlow<XSourcePosition?> =
    channelFlow {
      XDebugSessionApi.getInstance().currentSourcePosition(id).collectLatest { sourcePositionDto ->
        if (sourcePositionDto == null) {
          send(null)
          return@collectLatest
        }
        supervisorScope {
          send(sourcePositionDto.sourcePosition())
          awaitCancellation()
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, null)

  private val sessionState: StateFlow<XDebugSessionState> =
    channelFlow {
      XDebugSessionApi.getInstance().currentSessionState(id).collectLatest { sessionState ->
        send(sessionState)
      }
    }.stateIn(cs, SharingStarted.Eagerly, sessionDto.initialSessionState)

  val isStopped: Boolean
    get() = sessionState.value.isStopped

  val isPaused: Boolean
    get() = sessionState.value.isPaused

  val isReadOnly: Boolean
    get() = sessionState.value.isReadOnly

  val isPauseActionSupported: Boolean
    get() = sessionState.value.isPauseActionSupported

  val editorsProvider: XDebuggerEditorsProvider = localEditorsProvider
                                                  ?: FrontendXDebuggerEditorsProvider(id, sessionDto.editorsProviderDto.fileTypeId)

  val valueMarkers: XValueMarkers<FrontendXValue, XValueMarkerId> = FrontendXValueMarkers(project)

  fun closeScope() {
    cs.cancel()
  }
}