// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.xdebugger.impl.ui

import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.PreferredPlace
import com.intellij.execution.runners.RunTab
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.InternalDecoratorImpl
import com.intellij.openapi.wm.impl.content.SingleContentSupplier
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.Content
import com.intellij.ui.content.custom.options.CustomContentLayoutOptions
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.frame.*
import java.awt.Dimension
import javax.swing.Icon

class XDebugSessionTab3(
  session: XDebugSessionImpl,
  icon: Icon?,
  environment: ExecutionEnvironment?
) : XDebugSessionTab(session, icon, environment, false) {

  companion object {
    private const val viewProportionKey = "debugger.layout.watches.defaultThreadsProportion"
    //is used by plugins
    const val debuggerContentId = "DebuggerView"
  }

  private val splitter = OnePixelSplitter(viewProportionKey, 0.35f).apply {
    addPropertyChangeListener {
      if ("ancestor" == it.propertyName && it.newValue != null) {
        updateSplitterOrientation()
      }
    }
  }

  private var mySingleContentSupplier: SingleContentSupplier? = null

  override fun getWatchesContentId() = debuggerContentId
  override fun getFramesContentId() = debuggerContentId

  private fun getWatchesViewImpl(session: XDebugSessionImpl, watchesIsVariables: Boolean): XWatchesViewImpl {
    val useSplitterView = (session.debugProcess as? XDebugSessionTabCustomizer)?.bottomLocalsComponentProvider != null
    return if (useSplitterView)
      XSplitterWatchesViewImpl(session, watchesIsVariables, true, withToolbar = false)
    else
      XWatchesViewImpl(session, watchesIsVariables, true, false)
  }

  override fun addVariablesAndWatches(session: XDebugSessionImpl) {
    val variablesView: XVariablesView?
    val watchesView: XVariablesView?
    if (isWatchesInVariables) {
      variablesView = getWatchesViewImpl(session, watchesIsVariables = true)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      myWatchesView = variablesView
    } else {
      variablesView = XVariablesView(session)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      watchesView = getWatchesViewImpl(session, watchesIsVariables = false)
      registerView(DebuggerContentInfo.WATCHES_CONTENT, watchesView)
      myWatchesView = watchesView

      myUi.addContent(createWatchesContent(session), 0, PlaceInGrid.right, false)
    }

    splitter.secondComponent = variablesView.panel

    UIUtil.removeScrollBorder(splitter)
  }

  override fun initDebuggerTab(session: XDebugSessionImpl) {
    val name = debuggerContentId
    val content = myUi.createContent(name, splitter, XDebuggerBundle.message("xdebugger.threads.vars.tab.title"), null, null).apply {
      isCloseable = false
    }

    val customLayoutOptions = if (Registry.`is`("debugger.new.debug.tool.window.view"))
      XDebugFramesAndThreadsLayoutOptions(session, content, this).apply {
        content.putUserData(CustomContentLayoutOptions.KEY, this)
      }
    else
      null

    val framesView = (customLayoutOptions?.getCurrentOption() as? FramesAndThreadsLayoutOptionBase)?.createView() ?: XFramesView(myProject)
    registerThreadsView(session, content, framesView, true)
    addVariablesAndWatches(session)

    myUi.addContent(content, 0, PlaceInGrid.center, false)

    ui.defaults.initContentAttraction(debuggerContentId, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION, LayoutAttractionPolicy.FocusOnce())
  }

  override fun initToolbars(session: XDebugSessionImpl) {
    (myUi as? RunnerLayoutUiImpl)?.setLeftToolbarVisible(false)

    val toolbar = DefaultActionGroup()
    val headerGroup = getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP)
    val headerActionsWithoutDuplicates = headerGroup.getChildren(null).distinct()
    toolbar.addAll(headerActionsWithoutDuplicates)

    val more = MoreActionGroup()
    more.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP))
    more.addSeparator()

    fun addWithConstraints(actions: List<AnAction>, constraints: Constraints) {
      actions.asSequence()
        .forEach {
          if (it.templatePresentation.getClientProperty(RunTab.PREFERRED_PLACE) == PreferredPlace.MORE_GROUP) {
            more.add(it)
          } else {
            toolbar.add(it, constraints)
          }
        }
    }

    // reversed because it was like this in the original tab
    addWithConstraints(session.restartActions.asReversed(), Constraints(Anchor.AFTER, IdeActions.ACTION_RERUN))
    addWithConstraints(session.extraActions.asReversed(), Constraints(Anchor.AFTER, IdeActions.ACTION_STOP_PROGRAM))
    addWithConstraints(session.extraStopActions, Constraints(Anchor.AFTER, IdeActions.ACTION_STOP_PROGRAM))

    more.addSeparator()

    val gear = DefaultActionGroup().apply {
      templatePresentation.text = ActionsBundle.message("group.XDebugger.settings.text")
      templatePresentation.icon = AllIcons.General.Settings
      isPopup = true
      addAll(*myUi.options.settingsActionsList)
    }

    registerAdditionalActions(more, toolbar, gear)
    // Constrains are required as a workaround that puts these actions into the end anyway
    more.add(gear, Constraints(Anchor.BEFORE, ""))
    toolbar.add(more, Constraints(Anchor.BEFORE, ""))

    myUi.options.setTopLeftToolbar(toolbar, ActionPlaces.DEBUGGER_TOOLBAR)

    mySingleContentSupplier = object: RunTabSupplier(toolbar) {
      override fun getMainToolbarPlace() = ActionPlaces.DEBUGGER_TOOLBAR
      override fun getContentToolbarPlace() = ActionPlaces.DEBUGGER_TOOLBAR
    }
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

  val threadFramesView: XDebugView?
    get() = getView(DebuggerContentInfo.FRAME_CONTENT, XDebugView::class.java)

  override fun getSupplier(): SingleContentSupplier? = mySingleContentSupplier

  private fun updateSplitterOrientation() {
    splitter.orientation = UIUtil.getParentOfType(InternalDecoratorImpl::class.java, splitter)
                             ?.let(PlatformDataKeys.TOOL_WINDOW::getData)
                             ?.let {
                               it.anchor == ToolWindowAnchor.LEFT || it.anchor == ToolWindowAnchor.RIGHT
                             } ?: false
  }

  internal fun registerThreadsView(session: XDebugSessionImpl, content: Content, view: XDebugView) = registerThreadsView(session, content, view, false)

  private fun registerThreadsView(session: XDebugSessionImpl, content: Content, view: XDebugView, isInitialization: Boolean) {

    unregisterView(DebuggerContentInfo.FRAME_CONTENT)
    registerView(DebuggerContentInfo.FRAME_CONTENT, view)

    splitter.firstComponent = view.mainComponent?.apply {
      minimumSize = Dimension(20, 0)
    }

    content.setPreferredFocusedComponent { view.mainComponent }

    if (!isInitialization) {
      attachViewToSession(session, view)
      view.processSessionEvent(XDebugView.SessionEvent.SETTINGS_CHANGED, session)
      initFocusingVariablesFromFramesView()
    }
    UIUtil.removeScrollBorder(splitter)
  }
}