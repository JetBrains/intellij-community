// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl

import com.intellij.openapi.util.Ref
import com.intellij.xdebugger.frame.XStackFrame
import com.intellij.xdebugger.impl.rhizome.XDebugSessionEntity
import com.intellij.xdebugger.impl.rhizome.XDebuggerEvaluatorEntity
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import fleet.kernel.change
import fleet.kernel.withEntities
import fleet.util.UID
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update

/**
 * This allows setting current session's [XStackFrame] and updates DB state, when [XStackFrame] is changed.
 */
internal class XDebugSessionCurrentStackFrameManager(
  sessionScope: CoroutineScope,
  private val sessionEntityDeferred: Deferred<XDebugSessionEntity>,
) {
  // Ref is used to prevent StateFlow's equals checks
  private val currentStackFrame = MutableStateFlow<Ref<XStackFrame?>>(Ref.create(null))

  init {
    // update sessionEntity's evaluator when stackframe is changed
    // NB!: we assume that the current evaluator depends only on the current StackFrame
    sessionScope.launch {
      val sessionEntity = sessionEntityDeferred.await()
      currentStackFrame.collectLatest { stackFrame ->
        val currentEvaluator = stackFrame.get()?.evaluator
        withEntities(sessionEntity) {
          change {
            sessionEntity.evaluator?.delete()
            if (currentEvaluator != null) {
              val evaluatorEntity = XDebuggerEvaluatorEntity.new {
                it[XDebuggerEvaluatorEntity.EvaluatorId] = XDebuggerEvaluatorId(UID.random())
                it[XDebuggerEvaluatorEntity.Evaluator] = currentEvaluator
                it[XDebuggerEvaluatorEntity.Session] = sessionEntity
              }
              sessionEntity.update {
                it[XDebugSessionEntity.Evaluator] = evaluatorEntity
              }
            }
          }
        }
      }
    }
  }

  fun setCurrentStackFrame(stackFrame: XStackFrame?) {
    currentStackFrame.update {
      Ref.create(stackFrame)
    }
  }

  fun getCurrentStackFrame(): XStackFrame? {
    return currentStackFrame.value.get()
  }
}