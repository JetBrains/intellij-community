// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asProject
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.XFullValueEvaluator
import com.intellij.xdebugger.frame.XValue
import com.intellij.xdebugger.frame.XValueNode
import com.intellij.xdebugger.frame.XValuePlace
import com.intellij.xdebugger.impl.LocalXDebuggerSessionEvaluatorEntity
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorApi
import com.intellij.xdebugger.impl.rpc.XDebuggerEvaluatorId
import com.intellij.xdebugger.impl.rpc.XEvaluationResult
import com.intellij.xdebugger.impl.rpc.XValueId
import com.intellij.xdebugger.impl.rpc.XValuePresentation
import com.intellij.xdebugger.impl.ui.tree.nodes.XValuePresentationUtil.XValuePresentationTextExtractor
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal class BackendXDebuggerEvaluatorApi : XDebuggerEvaluatorApi {
  override suspend fun evaluate(evaluatorId: XDebuggerEvaluatorId, expression: String): Deferred<XEvaluationResult> = withKernel {
    val evaluatorEntity = entity(evaluatorId.eid) as? LocalXDebuggerSessionEvaluatorEntity
                          ?: return@withKernel CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val evaluator = evaluatorEntity.evaluator
    val evaluationResult = CompletableDeferred<XValue>()

    withContext(Dispatchers.EDT) {
      // TODO: pass XSourcePosition
      evaluator.evaluate(expression, object : XEvaluationCallback {
        override fun evaluated(result: XValue) {
          evaluationResult.complete(result)
        }

        override fun errorOccurred(errorMessage: @NlsContexts.DialogMessage String) {
          evaluationResult.completeExceptionally(EvaluationException(errorMessage))
        }
      }, null)
    }
    val project = evaluatorEntity.sessionEntity.projectEntity.asProject()
    val evaluationCoroutineScope = EvaluationCoroutineScopeProvider.getInstance(project).cs

    evaluationCoroutineScope.async(Dispatchers.EDT) {
      val xValue = try {
        evaluationResult.await()
      }
      catch (e: EvaluationException) {
        return@async XEvaluationResult.EvaluationError(e.errorMessage)
      }
      val xValueEntity = withKernel {
        change {
          // TODO: leaked XValue entity, it is never disposed
          LocalHintXValueEntity.new {
            it[LocalHintXValueEntity.Project] = evaluatorEntity.sessionEntity.projectEntity
            it[LocalHintXValueEntity.XValue] = xValue
          }
        }
      }
      XEvaluationResult.Evaluated(XValueId(xValueEntity.eid))
    }
  }

  private class EvaluationException(val errorMessage: @NlsContexts.DialogMessage String) : Exception(errorMessage)

  override suspend fun computePresentation(xValueId: XValueId): Flow<XValuePresentation>? = withKernel {
    val hintEntity = entity(xValueId.eid) as? LocalHintXValueEntity ?: return@withKernel null
    val presentations = MutableSharedFlow<XValuePresentation>(replay = 1)
    val xValue = hintEntity.xValue
    channelFlow {
      var isObsolete = false

      val valueNode = object : XValueNode {
        override fun isObsolete(): Boolean {
          return isObsolete
        }

        override fun setPresentation(icon: Icon?, type: @NonNls String?, value: @NonNls String, hasChildren: Boolean) {
          // TODO: pass icon, type and hasChildren too
          presentations.tryEmit(XValuePresentation(value))
        }

        override fun setPresentation(icon: Icon?, presentation: com.intellij.xdebugger.frame.presentation.XValuePresentation, hasChildren: Boolean) {
          // TODO: handle XValuePresentation fully
          val textExtractor = XValuePresentationTextExtractor()
          presentation.renderValue(textExtractor)
          setPresentation(icon, presentation.type, textExtractor.text, hasChildren)
        }

        override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
          // TODO: implement setFullValueEvaluator
        }
      }
      // TODO: pass XValuePlace
      xValue.computePresentation(valueNode, XValuePlace.TOOLTIP)

      launch {
        try {
          awaitCancellation()
        }
        finally {
          isObsolete = true
        }
      }

      presentations.collectLatest {
        send(it)
      }
    }
  }
}

@Service(Service.Level.PROJECT)
private class EvaluationCoroutineScopeProvider(project: Project, val cs: CoroutineScope) {

  companion object {
    fun getInstance(project: Project): EvaluationCoroutineScopeProvider = project.service()
  }
}