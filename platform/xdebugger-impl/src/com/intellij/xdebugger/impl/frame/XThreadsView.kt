// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.project.Project
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.impl.actions.XDebuggerActions.THREADS_VIEW_POPUP_GROUP
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import org.jetbrains.annotations.ApiStatus
import javax.swing.JPanel

@ApiStatus.Internal
class XThreadsView(project: Project, session: XDebugSessionProxy) : XDebugView() {

  @ApiStatus.Obsolete
  constructor(project: Project, session: XDebugSession) : this(project, session.asProxy())

  private val treePanel = XDebuggerTreePanel(project, session.editorsProvider, this, null, THREADS_VIEW_POPUP_GROUP, null)

  fun getTree(): XDebuggerTree = treePanel.tree
  fun getPanel(): JPanel = treePanel.mainPanel

  override fun getMainComponent(): JPanel = getPanel()

  fun getDefaultFocusedComponent(): XDebuggerTree = treePanel.tree

  override fun clear() {
    DebuggerUIUtil.invokeLater {
      getTree().setRoot(object : XValueContainerNode<XValueContainer>(getTree(), null, true, object : XValueContainer() {}) {}, false)
    }
  }

  override fun processSessionEvent(event: SessionEvent, session: XDebugSessionProxy) {
    if (event == SessionEvent.BEFORE_RESUME) {
      return
    }
    if (event == SessionEvent.PAUSED) {
      // clear immediately
      cancelClear()
      clear()
    }
    DebuggerUIUtil.invokeLater {
      getTree().setRoot(XThreadsRootNode(getTree(), session), false)
    }
  }

  override fun dispose() {
  }

  class ThreadsContainer(val session: XDebugSessionProxy) : XValueContainer() {
    override fun computeChildren(node: XCompositeNode) {
      session.computeExecutionStacks {
        object : XSuspendContext.XExecutionStackContainer {
          override fun errorOccurred(errorMessage: String) {

          }

          override fun addExecutionStack(executionStacks: List<XExecutionStack>, last: Boolean) {
            val children = XValueChildrenList()
            executionStacks.map { FramesContainer(it) }.forEach { children.add("", it) }
            node.addChildren(children, last)
          }
        }
      }
    }
  }

  class FramesContainer(val executionStack: XExecutionStack) : XValue() {
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
      frame.customizeTextPresentation { text, attrs -> // todo use TextAttributes in node repr
        node.setPresentation(null, XRegularValuePresentation(text, null, ""), false)
      }
    }
  }

  class XThreadsRootNode(tree: XDebuggerTree, session: XDebugSessionProxy) :
    XValueContainerNode<ThreadsContainer>(tree, null, false, ThreadsContainer(session))
}
