// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend

import com.intellij.platform.debugger.impl.frontend.evaluate.quick.FrontendXDebuggerEvaluator
import com.intellij.platform.util.coroutines.childScope
import com.intellij.xdebugger.impl.XDebuggerActiveSessionEntity
import fleet.kernel.rete.asQuery
import fleet.kernel.rete.get
import fleet.kernel.rete.tokensFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

internal class FrontendXDebuggerSession(
  private val cs: CoroutineScope,
  sessionEntity: XDebuggerActiveSessionEntity,
) {
  private var evaluatorCoroutineScope: CoroutineScope? = null

  val evaluator: StateFlow<FrontendXDebuggerEvaluator?> =
    sessionEntity.asQuery()[XDebuggerActiveSessionEntity.EvaluatorId].tokensFlow()
      .map { token -> token.value.takeIf { token.added } }
      .map { evaluatorId ->
        evaluatorCoroutineScope?.cancel()
        if (evaluatorId != null) {
          val newEvaluatorCoroutineScope = cs.childScope("FrontendXDebuggerEvaluator")
          evaluatorCoroutineScope = newEvaluatorCoroutineScope
          FrontendXDebuggerEvaluator(newEvaluatorCoroutineScope, evaluatorId)
        }
        else {
          evaluatorCoroutineScope = null
          null
        }
      }
      .stateIn(cs, SharingStarted.Eagerly, null)
}