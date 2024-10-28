// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXDebuggerEvaluator
import com.intellij.xdebugger.impl.rpc.XDebugSessionApi
import com.intellij.xdebugger.impl.rpc.XDebugSessionId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*

internal class FrontendXDebuggerSession(
  private val project: Project,
  private val cs: CoroutineScope,
  sessionId: XDebugSessionId,
) {
  val evaluator: StateFlow<FrontendXDebuggerEvaluator?> =
    channelFlow {
      XDebugSessionApi.getInstance().currentEvaluator(sessionId).collectLatest { evaluatorId ->
        if (evaluatorId == null) {
          send(null)
          return@collectLatest
        }
        coroutineScope {
          val evaluator = FrontendXDebuggerEvaluator(project, this, evaluatorId)
          send(evaluator)
          awaitCancellation()
        }
      }
    }.stateIn(cs, SharingStarted.Eagerly, null)
}