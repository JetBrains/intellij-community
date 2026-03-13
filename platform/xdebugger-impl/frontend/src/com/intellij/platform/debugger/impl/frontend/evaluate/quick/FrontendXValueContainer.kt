// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.debugger.impl.frontend.evaluate.quick

import com.intellij.ide.ui.colors.attributes
import com.intellij.ide.ui.icons.icon
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.debugger.impl.frontend.frame.VariablesPreloadManager
import com.intellij.platform.debugger.impl.rpc.XContainerId
import com.intellij.platform.debugger.impl.rpc.XDebuggerTreeNodeHyperlinkDto
import com.intellij.platform.debugger.impl.rpc.XStackFrameId
import com.intellij.platform.debugger.impl.rpc.XValueApi
import com.intellij.platform.debugger.impl.rpc.XValueComputeChildrenEvent
import com.intellij.platform.debugger.impl.rpc.XValueGroupDto
import com.intellij.platform.debugger.impl.shared.XValuesPresentationBuilder
import com.intellij.ui.SimpleTextAttributes
import com.intellij.xdebugger.frame.XCompositeNode
import com.intellij.xdebugger.frame.XDebuggerTreeNodeHyperlink
import com.intellij.xdebugger.frame.XNamedValue
import com.intellij.xdebugger.frame.XValueChildrenList
import com.intellij.xdebugger.frame.XValueContainer
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import java.awt.event.MouseEvent
import java.util.function.Supplier
import javax.swing.Icon
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

private class XValueChildrenManager private constructor(
  @Suppress("TestOnlyProblems")
  private val preloadManager: VariablesPreloadManager?,
) : AbstractCoroutineContextElement(Key) {
  companion object Key : CoroutineContext.Key<XValueChildrenManager>

  constructor() : this(null)
  constructor(cs: CoroutineScope, frameId: XStackFrameId, stateToRecover: XDebuggerTreeState?)
    : this(VariablesPreloadManager.creteIfNeeded(cs, stateToRecover, frameId))

  suspend fun getChildrenEventsFlow(entityId: XContainerId): Flow<XValueComputeChildrenEvent> {
    return preloadManager?.getChildrenEventsFlow(entityId) ?: XValueApi.getInstance().computeChildren(entityId)
  }
}

internal class FrontendXValueContainer(
  private val project: Project,
  private val cs: CoroutineScope,
  private val hasParentValue: Boolean,
  private val id: XContainerId,
) : XValueContainer() {
  private fun getOrCreteChildrenManager(node: XCompositeNode): XValueChildrenManager {
    val existing = cs.coroutineContext[XValueChildrenManager]
    if (existing != null) return existing

    return if (id is XStackFrameId) {
      val stateToRecover = (node as? XValueContainerNode.Root<*>)?.stateToRecover
      XValueChildrenManager(cs, id, stateToRecover)
    }
    else {
      XValueChildrenManager()
    }
  }

  override fun computeChildren(node: XCompositeNode) {
    val childrenManager = getOrCreteChildrenManager(node)
    // Children of this container should be tied to the container scope,
    // not the node scope. The XValue may be reused in other nodes, e.g. inline debugger.
    val containerScope = cs
    containerScope.launch(Dispatchers.EDT) {
      val flow = childrenManager.getChildrenEventsFlow(id)
      val builder = XValuesPresentationBuilder()
      flow.collect { event ->
        when (event) {
          is XValueComputeChildrenEvent.AddChildren -> {
            val childrenList = XValueChildrenList()
            for ((name, xValue) in event.names zip event.children) {
              val flows = builder.createFlows(xValue.id)
              val value = FrontendXValue.create(project, containerScope, xValue, flows, hasParentValue)
              childrenList.add(name, value)
            }

            fun List<XValueGroupDto>.toFrontendXValueGroups() = map {
              FrontendXValueGroup(project, containerScope, it, hasParentValue)
            }

            for (group in event.topGroups.toFrontendXValueGroups()) {
              childrenList.addTopGroup(group)
            }

            for (group in event.bottomGroups.toFrontendXValueGroups()) {
              childrenList.addBottomGroup(group)
            }

            for (topValue in event.topValues) {
              val flows = builder.createFlows(topValue.id)
              val xValue = FrontendXValue.create(project, containerScope, topValue, flows, hasParentValue)
              childrenList.addTopValue(xValue as XNamedValue)
            }

            // Important: event when a node is obsolete,
            // we should continue to call createFlows,
            // so that the presentation of the xValue continues to update.
            if (!node.isObsolete) {
              node.addChildren(childrenList, event.isLast)
            }
          }
          is XValueComputeChildrenEvent.SetAlreadySorted -> {
            if (node.isObsolete) return@collect
            node.setAlreadySorted(event.value)
          }
          is XValueComputeChildrenEvent.SetErrorMessage -> {
            if (node.isObsolete) return@collect
            node.setErrorMessage(event.message, event.link?.hyperlink(containerScope))
          }
          is XValueComputeChildrenEvent.SetMessage -> {
            if (node.isObsolete) return@collect
            node.setMessage(
              event.message,
              event.icon?.icon(),
              event.attributes.attributes(),
              event.link?.hyperlink(containerScope)
            )
          }
          is XValueComputeChildrenEvent.TooManyChildren -> {
            if (node.isObsolete) return@collect
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
          is XValueComputeChildrenEvent.XValueAdditionalLinkEvent -> {
            builder.consume(event)
          }
        }
      }
    }
  }
}

internal fun XDebuggerTreeNodeHyperlinkDto.hyperlink(cs: CoroutineScope): XDebuggerTreeNodeHyperlink {
  // prefer local for better onClick handling
  val localLink = local
  if (localLink != null) return localLink

  val dto = this
  return object : XDebuggerTreeNodeHyperlink(text) {
    override fun alwaysOnScreen(): Boolean {
      return dto.alwaysOnScreen
    }

    override fun getLinkIcon(): Icon? {
      return dto.icon?.icon()
    }

    override fun getLinkTooltip(): @Nls String? {
      return dto.tooltip
    }

    override fun getShortcutSupplier(): Supplier<String?>? {
      return dto.shortcut?.let { Supplier { it } }
    }

    override fun getTextAttributes(): SimpleTextAttributes {
      return dto.attributes ?: TEXT_ATTRIBUTES
    }

    override fun onClick(event: MouseEvent?) {
      cs.launch {
        XValueApi.getInstance().nodeLinkClicked(dto.id)
      }
      event?.consume()
    }
  }
}
