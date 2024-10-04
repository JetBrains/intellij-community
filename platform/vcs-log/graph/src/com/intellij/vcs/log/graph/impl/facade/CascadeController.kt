// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.graph.impl.facade

import com.intellij.vcs.log.graph.api.elements.GraphElement
import com.intellij.vcs.log.graph.api.permanent.PermanentGraphInfo
import com.intellij.vcs.log.graph.api.printer.GraphPrintElement
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class CascadeController protected constructor(protected val delegateController: LinearGraphController,
                                                       val permanentGraphInfo: PermanentGraphInfo<*>) : LinearGraphController {
  override fun performLinearGraphAction(action: LinearGraphController.LinearGraphAction): LinearGraphController.LinearGraphAnswer {
    val answer = performAction(action)
    if (answer != null) return answer
    val delegateAction = VisibleGraphImpl.LinearGraphActionImpl(convertToDelegate(action.affectedElement), action.type)
    return delegateGraphChanged(delegateController.performLinearGraphAction(delegateAction))
  }

  private fun convertToDelegate(element: GraphPrintElement?): GraphPrintElement? {
    if (element == null) return null
    val convertedGraphElement = convertToDelegate(element.graphElement) ?: return null
    return convertPrintElement(element, convertedGraphElement)
  }

  protected open fun convertToDelegate(graphElement: GraphElement): GraphElement? = graphElement

  protected abstract fun delegateGraphChanged(delegateAnswer: LinearGraphController.LinearGraphAnswer): LinearGraphController.LinearGraphAnswer

  // null mean that this action must be performed by delegateGraphController
  protected abstract fun performAction(action: LinearGraphController.LinearGraphAction): LinearGraphController.LinearGraphAnswer?

  companion object {
    internal fun LinearGraphController.performActionRecursively(action: (LinearGraphController) -> GraphChanges<Int>?): GraphChanges<Int>? {
      val graphChanges = action(this)
      if (graphChanges != null) return graphChanges

      if (this !is CascadeController) return null

      val result = delegateController.performActionRecursively(action) ?: return null
      return delegateGraphChanged(LinearGraphController.LinearGraphAnswer(result)).graphChanges
    }

    private fun convertPrintElement(element: GraphPrintElement, convertedGraphElement: GraphElement): GraphPrintElement {
      return object : GraphPrintElement by element {
        override val graphElement: GraphElement get() = convertedGraphElement
      }
    }
  }
}
