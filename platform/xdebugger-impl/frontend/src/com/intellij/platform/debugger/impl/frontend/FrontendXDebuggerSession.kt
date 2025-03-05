// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXDebuggerEvaluator
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXValue
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.createFrontendXDebuggerEvaluator
import com.intellij.xdebugger.evaluation.XDebuggerEditorsProvider
import com.intellij.xdebugger.impl.frame.XValueMarkers
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XDebugSessionDto
import com.intellij.xdebugger.impl.rpc.XValueMarkerId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.supervisorScope

internal class FrontendXDebuggerSession(
  private val project: Project,
  private val cs: CoroutineScope,
  sessionDto: XDebugSessionDto,
) {
  private val localEditorsProvider = sessionDto.editorsProvider
  private val sessionId = sessionDto.id

  val evaluator: StateFlow<FrontendXDebuggerEvaluator?> =
    channelFlow {
      XDebugSessionApi.getInstance().currentEvaluator(sessionId).collectLatest { evaluatorDto ->
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

  val editorsProvider: XDebuggerEditorsProvider = localEditorsProvider ?: FrontendXDebuggerEditorsProvider()

  val valueMarkers: XValueMarkers<FrontendXValue, XValueMarkerId> = FrontendXValueMarkers(project)
}