// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.backend

import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.kernel.withKernel
import com.intellij.platform.project.asProject
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.evaluation.XDebuggerEvaluator.XEvaluationCallback
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.impl.rpc.*
import com.intellij.xdebugger.impl.ui.tree.nodes.XValuePresentationUtil.XValuePresentationTextExtractor
import com.jetbrains.rhizomedb.entity
import fleet.kernel.change
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import org.jetbrains.annotations.NonNls
import javax.swing.Icon

internal class BackendXDebuggerEvaluatorApi : XDebuggerEvaluatorApi {
  override suspend fun evaluate(evaluatorId: XDebuggerEvaluatorId, expression: String): Deferred<XEvaluationResult> = withKernel {
    val evaluatorEntity = entity(evaluatorId.eid) as? LocalXDebuggerSessionEvaluatorEntity
                          ?: return@withKernel CompletableDeferred(XEvaluationResult.EvaluationError(XDebuggerBundle.message("xdebugger.evaluate.stack.frame.has.no.evaluator.id")))
    val evaluator = evaluatorEntity.evaluator
    val evaluationResult = CompletableDeferred<XValue>()

    withContext(Dispatchers.EDT) {
      // TODO[IJPL-160146]: pass XSourcePosition
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
          // TODO[IJPL-160146]: leaked XValue entity, it is never disposed
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
    val presentations = Channel<XValuePresentation>(capacity = Int.MAX_VALUE)
    val xValue = hintEntity.xValue
    channelFlow {
      var isObsolete = false

      val valueNode = object : XValueNode {
        override fun isObsolete(): Boolean {
          return isObsolete
        }

        override fun setPresentation(icon: Icon?, type: @NonNls String?, value: @NonNls String, hasChildren: Boolean) {
          // TODO[IJPL-160146]: pass icon, type and hasChildren too
          presentations.trySend(XValuePresentation(value, hasChildren))
        }

        override fun setPresentation(icon: Icon?, presentation: com.intellij.xdebugger.frame.presentation.XValuePresentation, hasChildren: Boolean) {
          // TODO[IJPL-160146]: handle XValuePresentation fully
          val textExtractor = XValuePresentationTextExtractor()
          presentation.renderValue(textExtractor)
          setPresentation(icon, presentation.type, textExtractor.text, hasChildren)
        }

        override fun setFullValueEvaluator(fullValueEvaluator: XFullValueEvaluator) {
          // TODO[IJPL-160146]: implement setFullValueEvaluator
        }
      }
      // TODO[IJPL-160146]: pass XValuePlace
      xValue.computePresentation(valueNode, XValuePlace.TOOLTIP)

      launch {
        try {
          awaitCancellation()
        }
        finally {
          isObsolete = true
        }
      }

      for (presentation in presentations) {
        send(presentation)
      }
    }
  }

  override suspend fun computeChildren(xValueId: XValueId): Flow<XValueComputeChildrenEvent>? = withKernel {
    val hintEntity = entity(xValueId.eid) as? LocalHintXValueEntity ?: return@withKernel null
    val computeEvents = Channel<XValueComputeChildrenEvent>(capacity = Int.MAX_VALUE)
    val xValue = hintEntity.xValue
    channelFlow {
      var isObsolete = false

      val xCompositeNode = object : XCompositeNode {
        override fun isObsolete(): Boolean {
          return isObsolete
        }

        override fun addChildren(children: XValueChildrenList, last: Boolean) {
          val names = (0 until children.size()).map { children.getName(it) }
          val childrenXValues = (0 until children.size()).map { children.getValue(it) }
          launch {
            val xValueEntities = withKernel {
              val projectEntity = hintEntity.projectEntity
              childrenXValues.map { childXValue ->
                change {
                  // TODO: leaked XValue entity, it is never disposed
                  LocalHintXValueEntity.new {
                    it[LocalHintXValueEntity.Project] = projectEntity
                    it[LocalHintXValueEntity.XValue] = childXValue
                  }
                }
              }
            }
            val xValueIds = xValueEntities.map { XValueId(it.eid) }
            computeEvents.trySend(XValueComputeChildrenEvent.AddChildren(names, xValueIds, last))
          }
        }

        override fun tooManyChildren(remaining: Int) {
          // TODO[IJPL-160146]: implement tooManyChildren
        }

        override fun setAlreadySorted(alreadySorted: Boolean) {
          // TODO[IJPL-160146]: implement setAlreadySorted
        }

        override fun setErrorMessage(errorMessage: String) {
          // TODO[IJPL-160146]: implement setErrorMessage
        }

        override fun setErrorMessage(errorMessage: String, link: XDebuggerTreeNodeHyperlink?) {
          // TODO[IJPL-160146]: implement setErrorMessage
        }

        override fun setMessage(message: String, icon: Icon?, attributes: SimpleTextAttributes, link: XDebuggerTreeNodeHyperlink?) {
          // TODO[IJPL-160146]: implement setMessage
        }
      }

      xValue.computeChildren(xCompositeNode)

      launch {
        try {
          awaitCancellation()
        }
        finally {
          isObsolete = true
        }
      }

      for (event in computeEvents) {
        send(event)
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