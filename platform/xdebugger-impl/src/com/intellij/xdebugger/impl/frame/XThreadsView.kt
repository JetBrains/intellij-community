// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.debugger.impl.rpc.XDebugSessionApi
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.ui.DebuggerUIUtilShared
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.actions.XDebuggerActions.THREADS_VIEW_POPUP_GROUP
import com.intellij.xdebugger.impl.proxy.asProxy
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreeState
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.event.HierarchyEvent
import javax.swing.Icon
import javax.swing.JPanel

@OptIn(FlowPreview::class)
@ApiStatus.Internal
class XThreadsView(project: Project, session: XDebugSessionProxy) : XDebugView() {

  private val rebuildRequests = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
  private var wasShowing = false

  @ApiStatus.Obsolete
  constructor(project: Project, session: XDebugSession) : this(project, session.asProxy())

  private val treePanel = XDebuggerTreePanel(project, session.editorsProvider, this, null, THREADS_VIEW_POPUP_GROUP, null)

  init {
    tree.emptyText.text = XDebuggerBundle.message("debugger.threads.not.available")

    object : AutoScrollToSourceHandler() {
      override fun isAutoScrollMode(): Boolean = true

      override fun setAutoScrollMode(state: Boolean) {}

      override fun needToCheckFocus(): Boolean = false

      @RequiresEdt
      override fun scrollToSource(tree: Component) {
        val path = (tree as? XDebuggerTree)?.selectionPath ?: return
        (path.lastPathComponent as? XValueNodeImpl)?.valueContainer?.let { xValueContainer ->
          if (xValueContainer is FrameValue) {
            val xStackFrame = xValueContainer.frame
            session.setCurrentStackFrame(xValueContainer.executionStack, xStackFrame, false);
          }
        }
      }
    }.install(tree)

    panel.addHierarchyListener { e ->
      if ((e.changeFlags and HierarchyEvent.SHOWING_CHANGED.toLong()) > 0) {
        val isCurrentlyShowing = panel.isShowing
        if (!wasShowing && isCurrentlyShowing) {
          requestRebuild()
        }
        wasShowing = isCurrentlyShowing
      }
    }

    subscribeToThreadRefreshEvents(session)

    session.coroutineScope.launch {
      rebuildRequests
        .debounce(200)
        .collectLatest {
          withContext(Dispatchers.EDT) {
            DebuggerUIUtilShared.freezePaintingToReduceFlickering(treePanel.contentComponent)
            if (panel.isShowing) {
              val state = XDebuggerTreeState.saveState(tree)
              tree.setRoot(XThreadsRootNode(tree, session), false)
              state.restoreState(tree)
            }
          }
        }
    }
  }

  private fun subscribeToThreadRefreshEvents(session: XDebugSessionProxy) {
    session.coroutineScope.launch {
      XDebugSessionApi.getInstance().getUiUpdateEventsFlow(session.id)
        .collectLatest {
          requestRebuild()
        }
    }
  }

  val tree: XDebuggerTree get() = treePanel.tree

  val panel: JPanel get() = treePanel.mainPanel

  override fun getMainComponent(): JPanel = panel

  fun getDefaultFocusedComponent(): XDebuggerTree = tree

  private fun requestRebuild() {
    rebuildRequests.tryEmit(Unit)
  }

  override fun clear() {
    DebuggerUIUtil.invokeLater {
      tree.setRoot(object : XValueContainerNode<XValueContainer>(tree, null, true, object : XValueContainer() {}) {}, false)
    }
  }

  override fun processSessionEvent(event: SessionEvent, session: XDebugSessionProxy) {
    if (event == SessionEvent.BEFORE_RESUME) {
      return
    }
    // Do not refresh a tree on a FRAME_CHANGED event
    // so that selecting stack frames does not collapse a thread node.
    if (event == SessionEvent.FRAME_CHANGED) {
      return
    }
    requestRebuild()
  }

  override fun dispose() {
  }

  class ThreadsContainer(private val session: XDebugSessionProxy) : XValueContainer() {
    override fun computeChildren(node: XCompositeNode) {
      val container = object : XSuspendContext.XExecutionStackGroupContainer {
        override fun errorOccurred(errorMessage: String) {
          node.setMessage(errorMessage)
        }

        override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
          val children = XValueChildrenList()
          executionStacks.map { FramesContainer(it, session) }.forEach { children.add(it.executionStack.displayName, it) }
          node.addChildren(children, last)
        }

        override fun addExecutionStackGroups(executionStackGroups: List<XExecutionStackGroup>, last: Boolean) {
          val children = XValueChildrenList()
          executionStackGroups.map { ThreadGroupContainer(it, session) }.forEach { children.add(it.group.name, it) }
          node.addChildren(children, last)
        }
      }
      session.computeRunningExecutionStacks(container)
    }
  }

  class ThreadGroupContainer(val group: XExecutionStackGroup, private val session: XDebugSessionProxy) : XValue() {
    override fun computeChildren(node: XCompositeNode) {
      val children = XValueChildrenList()
      group.groups.map { ThreadGroupContainer(it, session) }.forEach { children.add(it.group.name, it) }
      group.stacks.forEach { children.add(it.displayName, FramesContainer(it, session)) }
      node.addChildren(children, true)
    }

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      node.setEmptyValuePresentation(group.icon)
    }
  }

  class FramesContainer(val executionStack: XExecutionStack, private val session: XDebugSessionProxy) : XValue() {
    override fun computeChildren(node: XCompositeNode) {
      if (!session.hasSuspendContext()) {
        node.setMessage(XDebuggerBundle.message("debugger.frames.not.available"))
        return
      }

      executionStack.computeStackFrames(0, object : XExecutionStack.XStackFrameContainer {
        override fun errorOccurred(errorMessage: String) {
          node.setMessage(errorMessage)
        }

        override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
          val children = XValueChildrenList()
          stackFrames.forEach { children.add("", FrameValue(executionStack,it, session)) }
          node.addChildren(children, last)
        }
      })
    }

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      node.setEmptyValuePresentation(executionStack.icon)
    }
  }

  class FrameValue(val executionStack: XExecutionStack, val frame : XStackFrame, private val session: XDebugSessionProxy) : XValue() {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      session.coroutineScope.launch {
        frame.customizePresentation().collectLatest { presentation ->
          node.setPresentation(presentation.icon, object : XValuePresentation() {
            override fun getSeparator(): @NlsSafe String = ""
            override fun renderValue(renderer: XValueTextRenderer) {
              val fragments = presentation.fragments
              fragments.forEach { (text, attr) ->
                when(attr) {
                  SimpleTextAttributes.GRAYED_ATTRIBUTES,
                  SimpleTextAttributes.GRAY_ATTRIBUTES,
                  SimpleTextAttributes.GRAY_ITALIC_ATTRIBUTES,
                    -> renderer.renderComment(text)
                  SimpleTextAttributes.ERROR_ATTRIBUTES -> renderer.renderError(text)
                  else -> renderer.renderValue(text)
                }
              }
            }
          }, false)
        }
      }
    }
  }

  class XThreadsRootNode(tree: XDebuggerTree, session: XDebugSessionProxy) :
    XValueContainerNode<ThreadsContainer>(tree, null, false, ThreadsContainer(session))
}

private fun XCompositeNode.setMessage(text: String) {
  // remove temporary loading message nodes
  addChildren(XValueChildrenList.EMPTY, true)
  setMessage(text, null, SimpleTextAttributes.GRAYED_ATTRIBUTES, null)
}

private fun XValueNode.setEmptyValuePresentation(icon: Icon?) {
  setPresentation(icon, XRegularValuePresentation("", null, ""), true)
}