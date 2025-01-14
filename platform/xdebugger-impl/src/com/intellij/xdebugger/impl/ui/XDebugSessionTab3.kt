// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.actions.CustomContentLayoutSettings
import com.intellij.ide.DataManager
import com.intellij.ide.ui.customization.CustomActionsListener
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
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
  session: XDebugSessionImpl,
  icon: Icon?,
  environment: ExecutionEnvironment?
) : XDebugSessionTabNewUI(session, icon, environment) {

  companion object {
    private const val VIEW_PROPORTION_KEY = "debugger.layout.watches.defaultThreadsProportion"
    //is used by plugins
    const val debuggerContentId: String = "DebuggerView"
  }

  val project: Project = session.project

  private val splitter = OnePixelSplitter(VIEW_PROPORTION_KEY, 0.35f).apply {
    addPropertyChangeListener {
      if ("ancestor" == it.propertyName && it.newValue != null) {
        updateSplitterOrientation()
      }
    }
  }

  override fun getWatchesContentId(): String = debuggerContentId
  override fun getFramesContentId(): String = debuggerContentId

  private fun getWatchesViewImpl(session: XDebugSessionImpl, watchesIsVariables: Boolean): XWatchesViewImpl {
    val useSplitterView = session.debugProcess.getBottomLocalsComponentProvider() != null
    return if (useSplitterView)
      XSplitterWatchesViewImpl(session, watchesIsVariables, true, withToolbar = false)
    else
      XWatchesViewImpl(session, watchesIsVariables, true, false)
  }

  override fun addVariablesAndWatches(session: XDebugSessionImpl) {
    val variablesView: XVariablesView?
    if (isWatchesInVariables) {
      variablesView = getWatchesViewImpl(session, watchesIsVariables = true)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      myWatchesView = variablesView
    } else {
      variablesView = XVariablesView(session)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      val watchesView = getWatchesViewImpl(session, watchesIsVariables = false)
      myUi.addContent(createWatchesContent(session, watchesView), 0, PlaceInGrid.right, false)
    }
    applyVariablesTabLayoutSettings()

    splitter.secondComponent = variablesView.panel

    UIUtil.removeScrollBorder(splitter)
  }

  override fun initDebuggerTab(session: XDebugSessionImpl) {
    val name = debuggerContentId
    val content = myUi.createContent(name, splitter, XDebuggerBundle.message("xdebugger.threads.vars.tab.title"), null, null).apply {
      isCloseable = false
    }

    val customLayoutOptions = if (session.debugProcess.allowFramesViewCustomization()) {
      val optionsCollection = XDebugTabLayoutSettings(content, this)
      content.putUserData(CustomContentLayoutSettings.KEY, optionsCollection)
      optionsCollection.threadsAndFramesOptions
    }
    else
      null

    val framesView = (customLayoutOptions?.getCurrentOption() as? FramesAndThreadsLayoutOptionBase)?.createView(session) ?: XFramesView(session)
    registerThreadsView(content, framesView, true)
    framesView.mainComponent?.isVisible = customLayoutOptions?.isHidden?.not() ?: true
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

  private fun updateSplitterOrientation() {
    val toolWindow = PlatformDataKeys.TOOL_WINDOW.getData(DataManager.getInstance().getDataContext(
      UIUtil.getParentOfType(InternalDecoratorImpl::class.java, splitter)))
    splitter.orientation = toolWindow?.anchor?.let { it == ToolWindowAnchor.LEFT || it == ToolWindowAnchor.RIGHT } == true
  }

  internal val session: XDebugSessionImpl?
    get() = mySession

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
