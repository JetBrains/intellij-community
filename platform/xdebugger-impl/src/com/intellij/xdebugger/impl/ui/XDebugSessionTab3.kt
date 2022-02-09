// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.debugger.ui.DebuggerContentInfo
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.PreferredPlace
import com.intellij.execution.runners.RunTab
import com.intellij.execution.ui.layout.LayoutAttractionPolicy
import com.intellij.execution.ui.layout.PlaceInGrid
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.customization.*
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.MoreActionGroup
import com.intellij.openapi.keymap.impl.ui.Group
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.SingleContentSupplier
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.OnePixelSplitter
import com.intellij.ui.content.Content
import com.intellij.ui.content.custom.options.CustomContentLayoutOptions
import com.intellij.util.ui.UIUtil
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.XDebugSessionImpl
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.frame.*
import java.awt.Dimension
import java.util.function.Supplier
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
  private var toolbarGroup: DefaultActionGroup? = null

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

    ui.defaults
      .initContentAttraction(debuggerContentId, XDebuggerUIConstants.LAYOUT_VIEW_BREAKPOINT_CONDITION, LayoutAttractionPolicy.FocusOnce())
      .initContentAttraction(debuggerContentId, XDebuggerUIConstants.LAYOUT_VIEW_FINISH_CONDITION, LayoutAttractionPolicy.FocusOnce())

    addDebugToolwindowActions(session.project)

    CustomActionsListener.subscribe(this, object : CustomActionsListener {
      override fun schemaChanged() {
        updateToolbars()
      }
    })
  }

  override fun initToolbars(session: XDebugSessionImpl) {
    val isVerticalToolbar = Registry.get("debugger.new.tool.window.layout.toolbar").isOptionEnabled("Vertical")
    (myUi as? RunnerLayoutUiImpl)?.setLeftToolbarVisible(isVerticalToolbar)

    val toolbar = DefaultActionGroup()

    mySingleContentSupplier = object: RunTabSupplier(toolbar) {
      override fun getToolbarActions(): ActionGroup? {
        return if (isVerticalToolbar) ActionGroup.EMPTY_GROUP else super.getToolbarActions()
      }
      override fun getMainToolbarPlace() = ActionPlaces.DEBUGGER_TOOLBAR
      override fun getContentToolbarPlace() = ActionPlaces.DEBUGGER_TOOLBAR
    }

    toolbarGroup = toolbar
    updateToolbars()

    if (isVerticalToolbar) {
      myUi.options.setLeftToolbar(toolbar, ActionPlaces.DEBUGGER_TOOLBAR)
    } else {
      myUi.options.setTopLeftToolbar(toolbar, ActionPlaces.DEBUGGER_TOOLBAR)
    }
  }

  fun updateToolbars() {
    val toolbar = toolbarGroup ?: return
    val session = mySession
    toolbar.removeAll()

    fun Array<AnAction>.removeDuplicatesExceptSeparators(collection: Collection<AnAction>): List<AnAction> {
      val actions = toMutableList()
      val visited = collection.toMutableSet()
      val iterator = actions.iterator()
      while (iterator.hasNext()) {
        val action = iterator.next()
        if (action !is Separator && !visited.add(action)) {
          iterator.remove()
        }
      }
      return actions
    }

    val headerGroup = getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP)
    val headerActionsWithoutDuplicates = headerGroup.getChildren(null).removeDuplicatesExceptSeparators(emptyList())
    toolbar.addAll(headerActionsWithoutDuplicates)

    val more = MoreActionGroup()
    val moreGroup = getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP)
    val moreActionsWithoutDuplicates = moreGroup.getChildren(null).removeDuplicatesExceptSeparators(headerActionsWithoutDuplicates)
    more.addAll(moreActionsWithoutDuplicates)
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
    if (session != null) {
      addWithConstraints(session.restartActions.asReversed(), Constraints(Anchor.AFTER, IdeActions.ACTION_RERUN))
      addWithConstraints(session.extraActions.asReversed(), Constraints(Anchor.AFTER, IdeActions.ACTION_STOP_PROGRAM))
      addWithConstraints(session.extraStopActions, Constraints(Anchor.AFTER, IdeActions.ACTION_STOP_PROGRAM))
    }

    more.addSeparator()

    val gear = DefaultActionGroup().apply {
      templatePresentation.text = ActionsBundle.message("group.XDebugger.settings.text")
      templatePresentation.icon = AllIcons.General.Settings
      isPopup = true
      addAll(*myUi.options.settingsActionsList)
    }

    if (session != null) {
      registerAdditionalActions(more, toolbar, gear)
    }
    // Constrains are required as a workaround that puts these actions into the end anyway
    more.add(gear, Constraints(Anchor.BEFORE, ""))
    toolbar.add(more, Constraints(Anchor.BEFORE, ""))
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

private fun addDebugToolwindowActions(project: Project) {
  val window = ToolWindowManager.getInstance(project).getToolWindow(ToolWindowId.DEBUG)
  fun showAddActionDialog(): List<Any>? = ActionGroupPanel.showDialog(
    XDebuggerBundle.message("debugger.add.action.dialog.title"),
    XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP
  ) {
    filterButtonText = Supplier { XDebuggerBundle.message("debugger.add.action.dialog.filter.name") }
    enableFilterAction = true
  }

  if (window != null && window is ToolWindowEx) {
    window.setAdditionalGearActions(DefaultActionGroup(
      object : DumbAwareAction({ IdeBundle.message("action.customizations.customize.action") }) {
        override fun actionPerformed(e: AnActionEvent) {
          val result = CustomizeActionGroupPanel.showDialog(
              XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP,
              listOf(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP),
              XDebuggerBundle.message("debugger.customize.toolbar.actions.dialog.title")
            ) {
            addActionHandler = ::showAddActionDialog
          }
          if (result != null) {
            CustomizationUtil.updateActionGroup(result, XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP)
          }
        }
      },
      object : DumbAwareAction({ IdeBundle.message("group.customizations.add.action.group") }) {
        override fun actionPerformed(e: AnActionEvent) {
          showAddActionDialog()
            ?.filter { it is String || it is Group }
            ?.let {
              val actions = CustomizationUtil.getGroupActions(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP,
                                                              CustomActionsSchema.getInstance())
              CustomizationUtil.updateActionGroup(actions + it, XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP)
            }
        }
      }
    ))
  }
}