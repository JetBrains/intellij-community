// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.XSourcePosition
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import com.intellij.xdebugger.impl.rpc.XEvaluationResult
import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

private val LOG = logger<FrontendXDebuggerEvaluator>()

// TODO[IJPL-160146]: support XDebuggerPsiEvaluator
internal class FrontendXDebuggerEvaluator(private val project: Project, private val scope: CoroutineScope, private val evaluatorId: XDebuggerEvaluatorId) : XDebuggerEvaluator() {
  override fun evaluate(expression: String, callback: XEvaluationCallback, expressionPosition: XSourcePosition?) {
    scope.launch(Dispatchers.EDT) {
      try {
        val evaluation = XDebuggerEvaluatorApi.getInstance().evaluate(evaluatorId, expression).await()
        when (evaluation) {
          is XEvaluationResult.Evaluated -> callback.evaluated(FrontendXValue(project, evaluation.valueId))
          is XEvaluationResult.EvaluationError -> callback.errorOccurred(evaluation.errorMessage)
        }
      }
      catch (e: Exception) {
        callback.errorOccurred(e.message ?: XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.not.evaluator"))
        LOG.error(e)
      }
    }
  }
}