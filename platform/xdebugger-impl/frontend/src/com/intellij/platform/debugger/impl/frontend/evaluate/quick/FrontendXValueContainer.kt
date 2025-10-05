// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.platform.debugger.impl.rpc.XValueGroupDto
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueContainer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
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
      childrenComputation().collect { computeChildrenEvent ->
        when (computeChildrenEvent) {
          is XValueComputeChildrenEvent.AddChildren -> {
            val childrenXValues = coroutineScope {
              computeChildrenEvent.children.map {
                FrontendXValue.create(project, cs, it, hasParentValue)
              }
            }
            val childrenList = XValueChildrenList()
            for (i in computeChildrenEvent.children.indices) {
              childrenList.add(computeChildrenEvent.names[i], childrenXValues[i])
            }

            fun List<XValueGroupDto>.toFrontendXValueGroups() = map {
              FrontendXValueGroup(project, cs, it, hasParentValue)
            }

            for (group in computeChildrenEvent.topGroups.toFrontendXValueGroups()) {
              childrenList.addTopGroup(group)
            }

            for (group in computeChildrenEvent.bottomGroups.toFrontendXValueGroups()) {
              childrenList.addBottomGroup(group)
            }

            for (topValue in computeChildrenEvent.topValues) {
              childrenList.addTopValue(FrontendXValue.create(project, cs, topValue, hasParentValue) as XNamedValue)
            }

            node.addChildren(childrenList, computeChildrenEvent.isLast)
          }
          is XValueComputeChildrenEvent.SetAlreadySorted -> {
            node.setAlreadySorted(computeChildrenEvent.value)
          }
          is XValueComputeChildrenEvent.SetErrorMessage -> {
            node.setErrorMessage(computeChildrenEvent.message, computeChildrenEvent.link)
          }
          is XValueComputeChildrenEvent.SetMessage -> {
            // TODO[IJPL-160146]: support SimpleTextAttributes serialization -- don't pass SimpleTextAttributes.REGULAR_ATTRIBUTES
            node.setMessage(
              computeChildrenEvent.message,
              computeChildrenEvent.icon?.icon(),
              computeChildrenEvent.attributes ?: SimpleTextAttributes.REGULAR_ATTRIBUTES,
              computeChildrenEvent.link
            )
          }
          is XValueComputeChildrenEvent.TooManyChildren -> {
            val addNextChildren = computeChildrenEvent.addNextChildren
            if (addNextChildren != null) {
              node.tooManyChildren(computeChildrenEvent.remaining, Runnable { addNextChildren.trySend(Unit) })
            }
            else {
              @Suppress("DEPRECATION")
              node.tooManyChildren(computeChildrenEvent.remaining)
            }
          }
        }
      }
    }
  }
}
