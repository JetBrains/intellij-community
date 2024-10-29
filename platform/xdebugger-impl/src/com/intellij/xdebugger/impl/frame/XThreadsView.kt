// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleColoredComponent
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

@ApiStatus.Internal
class XThreadsView(project: Project, session: XDebugSessionImpl) : XDebugView() {
  private val treePanel = XDebuggerTreePanel(project, session.debugProcess.editorsProvider, this, null, "", null)

  fun getTree() = treePanel.tree
  fun getPanel(): JPanel = treePanel.mainPanel

  override fun getMainComponent() = getPanel()

  fun getDefaultFocusedComponent(): XDebuggerTree =  treePanel.tree

  override fun clear() {
    DebuggerUIUtil.invokeLater {
      getTree().setRoot(object : XValueContainerNode<XValueContainer>(getTree(), null, true, object : XValueContainer() {}) {}, false)
    }
  }

  override fun processSessionEvent(event: SessionEvent, session: XDebugSession) {
    if (event == SessionEvent.BEFORE_RESUME) {
      return
    }
    val suspendContext = session.suspendContext
    if (suspendContext == null) {
      requestClear()
      return
    }
    if (event == SessionEvent.PAUSED) {
      // clear immediately
      cancelClear()
      clear()
    }
    DebuggerUIUtil.invokeLater {
      getTree().setRoot(XThreadsRootNode(getTree(), suspendContext), false)
    }
  }

  override fun dispose() {
  }

  class ThreadsContainer(val suspendContext: XSuspendContext) : XValueContainer() {
    override fun computeChildren(node: XCompositeNode) {
      suspendContext.computeExecutionStacks(object : XSuspendContext.XExecutionStackContainer {
        override fun errorOccurred(errorMessage: String) {

        }

        override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
          val children = XValueChildrenList()
          executionStacks.map { FramesContainer(it) }.forEach { children.add("", it) }
          node.addChildren(children, last)
        }
      })
    }
  }

  class FramesContainer(private val executionStack: XExecutionStack) : XValue() {
    override fun computeChildren(node: XCompositeNode) {
      executionStack.computeStackFrames(0, object : XExecutionStack.XStackFrameContainer {
        override fun errorOccurred(errorMessage: String) {
        }

        override fun addStackFrames(stackFrames: List<XStackFrame>, last: Boolean) {
          val children = XValueChildrenList()
          stackFrames.forEach { children.add("", FrameValue(it)) }
          node.addChildren(children, last)
        }
      })
    }

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      node.setPresentation(executionStack.icon, XRegularValuePresentation(executionStack.displayName, null, ""), true)
    }
  }

  class FrameValue(val frame : XStackFrame) : XValue() {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      val component = SimpleColoredComponent()
      frame.customizePresentation(component)
      node.setPresentation(component.icon, XRegularValuePresentation(component.getCharSequence(false).toString(), null, ""), false)
    }
  }

  class XThreadsRootNode(tree: XDebuggerTree, suspendContext: XSuspendContext) :
    XValueContainerNode<ThreadsContainer>(tree, null, false, ThreadsContainer(suspendContext))
}
