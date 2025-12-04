// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.runners.ExecutionEnvironmentProxy
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.actions.CustomContentLayoutSettings
import com.intellij.ide.DataManager
import com.intellij.ide.ui.customization.CustomActionsListener
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.platform.debugger.impl.shared.proxy.XDebugSessionProxy
import com.intellij.platform.debugger.impl.ui.XDebuggerEntityConverter
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.Content
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.frame.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Dimension
import javax.swing.Icon

@Internal
class XDebugSessionTab3(
  proxy: XDebugSessionProxy,
  icon: Icon?,
  environmentProxy: ExecutionEnvironmentProxy?,
  val defaultFramesViewKey: String?
) : XDebugSessionTabNewUI(proxy, icon, environmentProxy) {

  companion object {
    private const val VIEW_PROPORTION_KEY = "debugger.layout.watches.defaultThreadsProportion"
    //is used by plugins
    const val debuggerContentId: String = "DebuggerView"
  }

  val project: Project = proxy.project

  private val splitter = OnePixelSplitter(VIEW_PROPORTION_KEY, 0.35f).apply {
    addPropertyChangeListener {
      if ("ancestor" == it.propertyName && it.newValue != null) {
        updateSplitterOrientation()
      }
    }
  }

  override fun getWatchesContentId(): String = debuggerContentId
  override fun getFramesContentId(): String = debuggerContentId

  private fun getWatchesViewImpl(sessionProxy: XDebugSessionProxy, watchesIsVariables: Boolean): XWatchesViewImpl {
    val xDebugSession = XDebuggerEntityConverter.getSessionNonSplitOnly(sessionProxy)
    if (xDebugSession is XDebugSessionImpl) {
      if (xDebugSession.debugProcess.useSplitterView()) { // TODO terekhin migrate Immediate window to using new debugger API
        return XSplitterWatchesViewImpl(xDebugSession, watchesIsVariables, true, withToolbar = false)
      }
    }
    return XWatchesViewImpl(sessionProxy, watchesIsVariables, true, false)
  }

  override fun addVariablesAndWatches(proxy: XDebugSessionProxy) {
    val variablesView: XVariablesView?
    if (isWatchesInVariables) {
      variablesView = getWatchesViewImpl(proxy, watchesIsVariables = true)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      myWatchesView = variablesView
    }
    else {
      variablesView = XVariablesView(proxy)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      val watchesView = getWatchesViewImpl(proxy, watchesIsVariables = false)
      val watchesContent = createWatchesContent(proxy, watchesView)
      myUi.addContent(watchesContent, 0, PlaceInGrid.right, false)
    }
    applyVariablesTabLayoutSettings()

    splitter.secondComponent = variablesView.panel

    UIUtil.removeScrollBorder(splitter)
  }

  override fun initDebuggerTab(session: XDebugSessionProxy) {
    val name = debuggerContentId
    val content = myUi.createContent(name, splitter, XDebuggerBundle.message("xdebugger.threads.vars.tab.title"), null, null).apply {
      isCloseable = false
    }

    val optionsCollection = XDebugTabLayoutSettings(content, this)
    content.putUserData(CustomContentLayoutSettings.KEY, optionsCollection)
    val customLayoutOptions = optionsCollection.threadsAndFramesOptions

    val framesView = (customLayoutOptions.getCurrentOption() as? FramesAndThreadsLayoutOptionBase)?.createView(session) ?: XFramesView(session)
    registerThreadsView(content, framesView, true)
    framesView.mainComponent?.isVisible = !customLayoutOptions.isHidden
    addVariablesAndWatches(session)

    myUi.addContent(content, 0, PlaceInGrid.center, false)

    ui.defaults.initContentAttraction(debuggerContentId, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION, LayoutAttractionPolicy.FocusOnce())

    addDebugToolwindowActions(session.project)

    CustomActionsListener.subscribe(this, object : CustomActionsListener {
      override fun schemaChanged() {
        updateToolbars()
      }
    })
  }

  override fun initFocusingVariablesFromFramesView() {
    val xFramesView = threadFramesView as? XFramesView ?: return
    xFramesView.mainComponent?.isFocusCycleRoot = false
    xFramesView.onFrameSelectionKeyPressed {
      val variablesView = getView(DebuggerContentInfo.VARIABLES_CONTENT, XVariablesViewBase::class.java)
      variablesView?.onReady()?.whenComplete { _, _ ->
        with(variablesView.tree) {
          requestFocus()
          if (isSelectionEmpty) {
            setSelectionRow(0)
          }
        }
      }
    }
  }

  // used externally
  @Suppress("MemberVisibilityCanBePrivate")
  val threadFramesView: XDebugView?
    get() = getView(DebuggerContentInfo.FRAME_CONTENT, XDebugView::class.java)

  val sessionProxy: XDebugSessionProxy?
    get() = mySession

  private fun updateSplitterOrientation() {
    val toolWindow = PlatformDataKeys.TOOL_WINDOW.getData(DataManager.getInstance().getDataContext(
      UIUtil.getParentOfType(InternalDecoratorImpl::class.java, splitter)))
    splitter.orientation = toolWindow?.anchor?.let { it == ToolWindowAnchor.LEFT || it == ToolWindowAnchor.RIGHT } == true
  }

  internal fun registerThreadsView(content: Content, view: XDebugView) = registerThreadsView(content, view, false)

  private fun registerThreadsView(content: Content, view: XDebugView, isInitialization: Boolean) {

    unregisterView(DebuggerContentInfo.FRAME_CONTENT)
    registerView(DebuggerContentInfo.FRAME_CONTENT, view)

    splitter.firstComponent = view.mainComponent?.apply {
      minimumSize = Dimension(20, 0)
    }

    content.preferredFocusableComponent = view.mainComponent

    if (!isInitialization) {
      mySession?.let {
        attachViewToSession(it, view)
        view.processSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED, it)
      }
      initFocusingVariablesFromFramesView()
    }
    UIUtil.removeScrollBorder(splitter)
  }

  private fun applyVariablesTabLayoutSettings() {
    val areOptionsVisible = XDebugTabLayoutSettings.isVariablesViewVisible()
    getView(DebuggerContentInfo.VARIABLES_CONTENT, XVariablesView::class.java)?.mainComponent?.isVisible = areOptionsVisible
    getView(DebuggerContentInfo.WATCHES_CONTENT, XVariablesView::class.java)?.mainComponent?.isVisible = areOptionsVisible
  }
}
