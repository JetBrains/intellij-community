// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.ui

import com.intellij.execution.actions.CreateAction
import com.intellij.execution.runners.ExecutionEnvironment
import com.intellij.execution.runners.ExecutionEnvironmentProxy
import com.intellij.execution.runners.RunContentBuilder
import com.intellij.execution.ui.layout.impl.RunnerLayoutUiImpl
import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.ui.customization.*
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.keymap.impl.ui.Group
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.impl.content.SingleContentSupplier
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.actions.XDebuggerActions
import com.intellij.xdebugger.impl.frame.XDebugSessionProxy
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.function.Supplier
import javax.swing.Icon

@Internal
open class XDebugSessionTabNewUI(
  session: XDebugSessionProxy,
  icon: Icon?,
  environmentProxy: ExecutionEnvironmentProxy?,
) : XDebugSessionTab(session, icon, environmentProxy, false) {

  private var mySingleContentSupplier: SingleContentSupplier? = null
  private var toolbarGroup: DefaultActionGroup? = null

  override fun initDebuggerTab(session: XDebugSessionProxy) {
    ui.defaults.initTabDefaults(0, XDebuggerBundle.message("xdebugger.threads.vars.tab.title"), null)
    createDefaultTabs(session)
    addDebugToolwindowActions(session.project)
    CustomActionsListener.subscribe(this, object : CustomActionsListener {
      override fun schemaChanged() {
        if (isSingleContent()) {
          updateToolbars()
        }
        else {
          initToolbars(session)
        }
      }
    })
  }

  override fun initToolbars(session: XDebugSessionProxy) {
    val isVerticalToolbar = Registry.get("debugger.new.tool.window.layout.toolbar").isOptionEnabled("Vertical")
    (myUi as? RunnerLayoutUiImpl)?.also {
      it.setLeftToolbarVisible(isVerticalToolbar)
      if (Registry.`is`("debugger.toolbar.before.tabs", true)) {
        it.setTopLeftActionsBefore(true)
      }
    }

    val toolbar = DefaultActionGroupWithDelegate(getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP))

    if (isSingleContent()) {
      mySingleContentSupplier = object : RunTabSupplier(toolbar) {
        override fun getToolbarActions(): ActionGroup? {
          return if (isVerticalToolbar) ActionGroup.EMPTY_GROUP else super.getToolbarActions()
        }

        override fun getMainToolbarPlace() = ActionPlaces.DEBUGGER_TOOLBAR
        override fun getContentToolbarPlace() = ActionPlaces.DEBUGGER_TOOLBAR
      }
    }

    toolbarGroup = toolbar
    updateToolbars()

    if (isVerticalToolbar) {
      myUi.options.setLeftToolbar(toolbar, ActionPlaces.DEBUGGER_TOOLBAR)
    }
    else {
      myUi.options.setTopLeftToolbar(toolbar, ActionPlaces.DEBUGGER_TOOLBAR)
    }
  }

  fun updateToolbars() {
    val toolbar = toolbarGroup ?: return
    val session = mySession
    toolbar.removeAll()

    val headerGroup = getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_GROUP)
    val headerActions = (headerGroup as? CustomisedActionGroup)?.defaultChildrenOrStubs ?: AnAction.EMPTY_ARRAY
    RunContentBuilder.addAvoidingDuplicates(toolbar, headerActions)

    val more = RunContentBuilder.createToolbarMoreActionGroup(toolbar)
    val moreGroup = getCustomizedActionGroup(XDebuggerActions.TOOL_WINDOW_TOP_TOOLBAR_3_EXTRA_GROUP)
    RunContentBuilder.addAvoidingDuplicates(more, (moreGroup as? CustomisedActionGroup)?.defaultChildrenOrStubs ?: AnAction.EMPTY_ARRAY)
    more.addSeparator()

    // reversed because it was like this in the original tab
    if (session != null) {
      RunContentBuilder.addActionsWithConstraints(session.restartActions, Constraints(Anchor.AFTER, IdeActions.ACTION_RERUN), toolbar, more)
      RunContentBuilder.addActionsWithConstraints(session.extraActions, Constraints(Anchor.AFTER, IdeActions.ACTION_STOP_PROGRAM), toolbar,
                                                  more)
      RunContentBuilder.addActionsWithConstraints(session.extraStopActions.asReversed(),
                                                  Constraints(Anchor.AFTER, IdeActions.ACTION_STOP_PROGRAM), toolbar, more)
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
    more.add(CreateAction(), Constraints(Anchor.BEFORE, ""))
    toolbar.add(more, Constraints(Anchor.BEFORE, ""))
  }

  open fun isSingleContent() = Registry.`is`("debugger.new.tool.window.layout.single.content", false)
  override fun getSupplier(): SingleContentSupplier? = mySingleContentSupplier
}

fun addDebugToolwindowActions(project: Project) {
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