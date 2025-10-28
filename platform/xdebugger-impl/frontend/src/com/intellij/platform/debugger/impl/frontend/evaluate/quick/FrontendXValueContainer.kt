// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.platform.debugger.impl.rpc.XValueGroupDto
import com.intellij.platform.debugger.impl.shared.XValuesPresentationBuilder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

internal class FrontendXValueContainer(
  private val project: Project,
  private val cs: CoroutineScope,
  private val hasParentValue: Boolean,
  private val childrenComputation: suspend () -> Flow<XValueComputeChildrenEvent>,
) : XValueContainer() {
  override fun computeChildren(node: XCompositeNode) {
    node.childCoroutineScope(parentScope = cs, "FrontendXValueContainer#computeChildren").launch(Dispatchers.EDT) {
      val builder = XValuesPresentationBuilder()

      childrenComputation().collect { event ->
        when (event) {
          is XValueComputeChildrenEvent.AddChildren -> {
            val childrenList = XValueChildrenList()
            for ((name, xValue) in event.names zip event.children) {
              val (presentationFlow, fullValueEvaluatorFlow) = builder.createFlows(xValue.id)
              val value = FrontendXValue.create(project, cs, xValue, presentationFlow, fullValueEvaluatorFlow, hasParentValue)
              childrenList.add(name, value)
            }

            fun List<XValueGroupDto>.toFrontendXValueGroups() = map {
              FrontendXValueGroup(project, cs, it, hasParentValue)
            }

            for (group in event.topGroups.toFrontendXValueGroups()) {
              childrenList.addTopGroup(group)
            }

            for (group in event.bottomGroups.toFrontendXValueGroups()) {
              childrenList.addBottomGroup(group)
            }

            for (topValue in event.topValues) {
              val (presentationFlow, fullValueEvaluatorFlow) = builder.createFlows(topValue.id)
              val xValue = FrontendXValue.create(project, cs, topValue, presentationFlow, fullValueEvaluatorFlow, hasParentValue)
              childrenList.addTopValue(xValue as XNamedValue)
            }

            node.addChildren(childrenList, event.isLast)
          }
          is XValueComputeChildrenEvent.SetAlreadySorted -> {
            node.setAlreadySorted(event.value)
          }
          is XValueComputeChildrenEvent.SetErrorMessage -> {
            node.setErrorMessage(event.message, event.link)
          }
          is XValueComputeChildrenEvent.SetMessage -> {
            // TODO[IJPL-160146]: support SimpleTextAttributes serialization -- don't pass SimpleTextAttributes.REGULAR_ATTRIBUTES
            node.setMessage(
              event.message,
              event.icon?.icon(),
              event.attributes ?: SimpleTextAttributes.REGULAR_ATTRIBUTES,
              event.link
            )
          }
          is XValueComputeChildrenEvent.TooManyChildren -> {
            val addNextChildren = event.addNextChildren
            if (addNextChildren != null) {
              node.tooManyChildren(event.remaining) { addNextChildren.trySend(Unit) }
            }
            else {
              @Suppress("DEPRECATION")
              node.tooManyChildren(event.remaining)
            }
          }
          is XValueComputeChildrenEvent.XValueFullValueEvaluatorEvent -> {
            builder.consume(event)
          }
          is XValueComputeChildrenEvent.XValuePresentationEvent -> {
            builder.consume(event)
          }
        }
      }
    }
  }
}
