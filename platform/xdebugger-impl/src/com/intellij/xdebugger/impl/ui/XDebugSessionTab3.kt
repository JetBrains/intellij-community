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
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.impl.InternalDecoratorImpl
import com.intellij.openapi.wm.impl.content.SingleContentSupplier
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.tabs.PinToolwindowTabAction
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.frame.XFramesView
import com.intellij.xdebugger.impl.frame.XVariablesView
import com.intellij.xdebugger.impl.frame.XWatchesViewImpl
import java.awt.Dimension
import javax.swing.Icon

class XDebugSessionTab3(
  session: XDebugSessionImpl,
  icon: Icon?,
  environment: ExecutionEnvironment?
) : XDebugSessionTab(session, icon, environment, false) {

  companion object {
    private const val threadsIsVisibleKey = "threadsIsVisibleKey"
    private const val viewProportionKey = "debugger.layout.watches.defaultThreadsProportion"
    private const val debuggerContentId = "DebuggerView"
  }

  private val project = session.project
  private var threadsIsVisible
    get() = PropertiesComponent.getInstance(project).getBoolean(threadsIsVisibleKey, true)
    set(value) = PropertiesComponent.getInstance(project).setValue(threadsIsVisibleKey, value, true)

  private val lifetime = Disposer.newDisposable()

  private val splitter = OnePixelSplitter(viewProportionKey, 0.35f).apply {
    addPropertyChangeListener {
      if ("ancestor" == it.propertyName && it.newValue != null) {
        updateSplitterOrientation()
      }
    }
  }
  private val xThreadsFramesView = XFramesView(myProject)

  private var variables: XVariablesView? = null

  private var mySingleContentSupplier: SingleContentSupplier? = null

  override fun getWatchesContentId() = debuggerContentId
  override fun getFramesContentId() = debuggerContentId

  override fun addVariablesAndWatches(session: XDebugSessionImpl) {
    val variablesView: XVariablesView?
    val watchesView: XVariablesView?
    if (isWatchesInVariables) {
      variablesView = XWatchesViewImpl(session, true, true, false)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      variables = variablesView

      myWatchesView = variablesView
    } else {
      variablesView = XVariablesView(session)
      registerView(DebuggerContentInfo.VARIABLES_CONTENT, variablesView)
      variables = variablesView

      watchesView = XWatchesViewImpl(session, false, true, false)
      registerView(DebuggerContentInfo.WATCHES_CONTENT, watchesView)
      myWatchesView = watchesView

      myUi.addContent(createWatchesContent(session), 0, PlaceInGrid.right, false)
    }

    splitter.secondComponent = variablesView.panel

    UIUtil.removeScrollBorder(splitter)
  }

  override fun initDebuggerTab(session: XDebugSessionImpl) {
    val framesView = xThreadsFramesView
    registerView(DebuggerContentInfo.FRAME_CONTENT, framesView)

    splitter.firstComponent = xThreadsFramesView.mainPanel.apply {
      minimumSize = Dimension(20, 0)
    }
    addVariablesAndWatches(session)

    val name = debuggerContentId
    val content = myUi.createContent(name, splitter, XDebuggerBundle.message("xdebugger.threads.vars.tab.title"), null, framesView.defaultFocusedComponent).apply {
      isCloseable = false
    }

    myUi.addContent(content, 0, PlaceInGrid.center, false)

    ui.defaults.initContentAttraction(debuggerContentId, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION, LayoutAttractionPolicy.FocusOnce())
  }

  override fun initToolbars(session: XDebugSessionImpl) {
    (myUi as? RunnerLayoutUiImpl)?.setLeftToolbarVisible(false)

    val gearActions = DefaultActionGroup().apply {
      templatePresentation.text = ActionsBundle.message("group.XDebugger.settings.text")
      templatePresentation.icon = AllIcons.General.Settings
      isPopup = true
      addAll(*myUi.options.settingsActionsList)
      registerAdditionalActions(DefaultActionGroup(), DefaultActionGroup(), this)
    }

    val toolbar = DefaultActionGroup()
    toolbar.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP))

    val more = MoreActionGroup()
    more.addAll(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP))
    toolbar.add(more)
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
    more.add(gearActions)

    myUi.options.setTopLeftToolbar(toolbar, ActionPlaces.DEBUGGER_TOOLBAR)

    mySingleContentSupplier = RunTabSupplier(toolbar)
  }

  override fun getSupplier(): SingleContentSupplier? = mySingleContentSupplier

  override fun registerAdditionalActions(leftToolbar: DefaultActionGroup, topLeftToolbar: DefaultActionGroup, settings: DefaultActionGroup) {
    leftToolbar.apply {
      val constraints = Constraints(Anchor.BEFORE, XDebuggerActions.VIEW_BREAKPOINTS)

      add(object : ToggleAction() {
        override fun setSelected(e: AnActionEvent, state: Boolean) {
          if (threadsIsVisible != state) {
            threadsIsVisible = state
          }
          //xThreadsFramesView.setThreadsVisible(state)
          Toggleable.setSelected(e.presentation, state)
        }

        override fun isSelected(e: AnActionEvent) = threadsIsVisible

        override fun update(e: AnActionEvent) {
          e.presentation.icon = AllIcons.Actions.SplitVertically
          if (threadsIsVisible) {
            e.presentation.text = XDebuggerBundle.message("session.tab.hide.threads.view")
          }
          else {
            e.presentation.text = XDebuggerBundle.message("session.tab.show.threads.view")
          }

          setSelected(e, threadsIsVisible)
        }
      }, constraints)

      add(ActionManager.getInstance().getAction(XDebuggerActions.FRAMES_TOP_TOOLBAR_GROUP), constraints)
      add(Separator.getInstance(), constraints)
    }

    super.registerAdditionalActions(leftToolbar, topLeftToolbar, settings)
  }

  private fun updateSplitterOrientation() {
    splitter.orientation = UIUtil.getParentOfType(InternalDecoratorImpl::class.java, splitter)
                             ?.let(PlatformDataKeys.TOOL_WINDOW::getData)
                             ?.let {
                               it.anchor == ToolWindowAnchor.LEFT || it.anchor == ToolWindowAnchor.RIGHT
                             } ?: false
  }

  override fun dispose() {
    Disposer.dispose(lifetime)
    super.dispose()
  }
}