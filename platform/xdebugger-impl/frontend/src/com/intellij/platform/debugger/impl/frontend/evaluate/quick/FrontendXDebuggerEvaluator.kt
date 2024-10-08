// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.openapi.application.EDT
import com.intellij.platform.kernel.withKernel
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// TODO: support XDebuggerPsiEvaluator
internal class FrontendXDebuggerEvaluator(private val scope: CoroutineScope, private val evaluatorId: XDebuggerEvaluatorId) : XDebuggerEvaluator() {
  override fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
    scope.launch(Dispatchers.EDT) {
      withKernel {
        val xValue = try {
          // TODO: write proper error message, don't throw error here
          val xValueId = XDebuggerEvaluatorApi.getInstance().evaluate(evaluatorId, expression) ?: error("Cannot evaluate")
          // TODO: what scope to provide for the XValue?
          FrontendXValue(scope, xValueId.await())
        }
        catch (e: Exception) {
          // TODO: write proper error message
          callback.errorOccurred(e.message ?: "Error occurred during evaluation")
          return@withKernel
        }
        callback.evaluated(xValue)
      }
    }
  }
}