// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.frame

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.AutoScrollToSourceHandler
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.xdebugger.XDebugSession
import com.intellij.xdebugger.frame.*
import com.intellij.xdebugger.frame.presentation.XRegularValuePresentation
import com.intellij.xdebugger.frame.presentation.XValuePresentation
import com.intellij.xdebugger.impl.actions.XDebuggerActions.THREADS_VIEW_POPUP_GROUP
import com.intellij.xdebugger.impl.ui.DebuggerUIUtil
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTree
import com.intellij.xdebugger.impl.ui.tree.XDebuggerTreePanel
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueContainerNode
import com.intellij.xdebugger.impl.ui.tree.nodes.XValueNodeImpl
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JPanel

@ApiStatus.Internal
class XThreadsView(project: Project, session: XDebugSessionProxy) : XDebugView() {

  @ApiStatus.Obsolete
  constructor(project: Project, session: XDebugSession) : this(project, session.asProxy())

  private val treePanel = XDebuggerTreePanel(project, session.editorsProvider, this, null, THREADS_VIEW_POPUP_GROUP, null)

  init {
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
  }

  val tree: XDebuggerTree get() = treePanel.tree

  val panel: JPanel get() = treePanel.mainPanel

  override fun getMainComponent(): JPanel = panel

  fun getDefaultFocusedComponent(): XDebuggerTree = tree

  override fun clear() {
    DebuggerUIUtil.invokeLater {
      tree.setRoot(object : XValueContainerNode<XValueContainer>(tree, null, true, object : XValueContainer() {}) {}, false)
    }
  }

  override fun processSessionEvent(event: SessionEvent, session: XDebugSessionProxy) {
    if (event == SessionEvent.BEFORE_RESUME) {
      return
    }
    if (!session.hasSuspendContext()) {
      requestClear()
      return
    }
    // Do not refresh a tree on a FRAME_CHANGED event
    // so that selecting stack frames does not collapse a thread node.
    if (event == SessionEvent.FRAME_CHANGED) {
      return
    }
    if (event == SessionEvent.PAUSED) {
      // clear immediately
      cancelClear()
      clear()
    }
    DebuggerUIUtil.invokeLater {
      tree.setRoot(XThreadsRootNode(tree, session), false)
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
          stackFrames.forEach { children.add("", FrameValue(executionStack,it)) }
          node.addChildren(children, last)
        }
      })
    }

    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      node.setPresentation(executionStack.icon, XRegularValuePresentation(executionStack.displayName, null, ""), true)
    }
  }

  class FrameValue(val executionStack: XExecutionStack, val frame : XStackFrame) : XValue() {
    override fun computePresentation(node: XValueNode, place: XValuePlace) {
      val component = SimpleColoredComponent()
      frame.customizeTextPresentation(component)
      node.setPresentation(component.icon, object : XValuePresentation() {
        override fun getSeparator(): @NlsSafe String = ""
        override fun renderValue(renderer: XValueTextRenderer) {
          val i = component.iterator()
          while (i.hasNext()) {
            i.next()
            val text = i.fragment
            when (i.textAttributes) {
              SimpleTextAttributes.GRAYED_ATTRIBUTES -> renderer.renderComment(text)
              SimpleTextAttributes.ERROR_ATTRIBUTES -> renderer.renderError(text)
              SimpleTextAttributes.REGULAR_ATTRIBUTES -> renderer.renderValue(text)
            }
          }
        }
      }, false)
    }
  }

  class XThreadsRootNode(tree: XDebuggerTree, session: XDebugSessionProxy) :
    XValueContainerNode<ThreadsContainer>(tree, null, false, ThreadsContainer(session))
}
