// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.ide.DataManager
import com.intellij.ide.IdeBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.dsl.gridLayout.UnscaledGapsY
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.FocusUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Font
import java.util.concurrent.ConcurrentHashMap
import javax.swing.JComponent


@RequiresEdt
internal fun emptyStateProjectPanel(disposable: Disposable): JComponent = panel {
  row {
    label(WelcomeScreenComponentFactory.getApplicationTitle()).applyToComponent {
      font = font.deriveFont(font.getSize() + scale(13).toFloat()).deriveFont(Font.BOLD)
    }.customize(UnscaledGaps(top = 105, bottom = 21))
      .align(AlignX.CENTER)
  }
  for (text in arrayOf(
    IdeBundle.message("welcome.screen.empty.projects.create.comment"),
    IdeBundle.message("welcome.screen.empty.projects.open.comment"))) {
    row {
      text(text).align(AlignX.CENTER).customize(UnscaledGaps(0)).applyToComponent { foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND }
    }.customize(UnscaledGapsY(bottom = 7))
  }
  val (mainActions, moreActions) = createActionToolbars(disposable)
  panel {
    row {
      cell(mainActions).align(AlignX.FILL)
    }
  }.align(AlignX.CENTER).customize(UnscaledGaps(27))
  row {
    cell(moreActions).align(AlignX.CENTER)
  }
}.apply {
  background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
}


// Returns main actions, more actions
@ApiStatus.Internal
fun createActionToolbars(parentDisposable: Disposable): Pair<ActionToolbarImpl, ActionToolbarImpl> {
  val actionManager = ActionManager.getInstance()
  val baseGroup = actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART_EMPTY_STATE) as ActionGroup
  val moreActionGroup = DefaultActionGroup(IdeBundle.message("welcome.screen.more.actions.link.text"), true)

  val toolbarGroup = object : ActionGroupWrapper(baseGroup) {
    val wrappers = ConcurrentHashMap<AnAction, AnAction>()
    override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
      moreActionGroup.removeAll()
      val mapped = visibleChildren.mapIndexedNotNull { index, action ->
        when {
          index >= getWelcomeScreenPrimaryButtonsNum() -> {
            moreActionGroup.add(action)
            null
          }
          action is ActionGroup && action is ActionsWithPanelProvider -> {
            val wrapper = wrappers.getOrPut(action) {
              ActionGroupPanelWrapper.wrapGroups(action, parentDisposable)
            }
            e.updateSession.presentation(wrapper)
            wrapper
          }
          else -> action
        }
      }
      mapped.forEach { action ->
        e.updateSession.presentation(action).putClientProperty(
          ActionUtil.COMPONENT_PROVIDER, WelcomeScreenActionsUtil.createBigIconWithTextAction(action))
      }
      moreActionGroup.templatePresentation.text = when {
        moreActionGroup.childrenCount == 1 ->
          e.updateSession.presentation(moreActionGroup.getChildren(e.actionManager)[0]).text
        else -> IdeBundle.message("welcome.screen.more.actions.link.text")
      }
      return mapped
    }
  }
  val actionsToolbar: ActionToolbarImpl = createActionsToolbar(toolbarGroup)

  moreActionGroup.templatePresentation.putClientProperty(ActionUtil.COMPONENT_PROVIDER, object : CustomComponentAction {
    val ENABLED = Key.create<Boolean>("ENABLED")
    var alienUpdateScheduled = false
    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return object : DropDownLink<String>(presentation.text, { link: DropDownLink<String> ->
        JBPopupFactory.getInstance().createActionGroupPopup(
          null, moreActionGroup, DataManager.getInstance().getDataContext(link),
          JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, true)
      }) {
        override fun performAction() {
          if (moreActionGroup.childrenCount == 1) {
            WelcomeScreenActionsUtil.performAnActionForComponent(moreActionGroup.getChildren(actionManager)[0], this)
          }
          else {
            super.performAction()
          }
        }
      }
    }

    override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
      component as? DropDownLink<*> ?: return
      val single = moreActionGroup.getChildren(actionManager).singleOrNull()
      if (!alienUpdateScheduled) {
        alienUpdateScheduled = true
        (single as? OpenAlienProjectAction)?.scheduleUpdate { enabled ->
          component.isVisible = enabled
          presentation.putClientProperty(ENABLED, enabled)
        }
      }
      component.isVisible = moreActionGroup.childrenCount > 0 &&
                            presentation.getClientProperty(ENABLED) != false
      component.text = moreActionGroup.templateText
      if (single != null) component.icon = null
      else component.setDropDownLinkIcon()
    }
  })
  val moreToolbar = ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, DefaultActionGroup(moreActionGroup), true)
  moreToolbar.targetComponent = moreToolbar.component
  moreToolbar.isOpaque = false
  return Pair(actionsToolbar, moreToolbar)
}

private fun createActionsToolbar(actionGroup: ActionGroup): ActionToolbarImpl {
  val actionToolbar: ActionToolbarImpl = object : ActionToolbarImpl(ActionPlaces.WELCOME_SCREEN, actionGroup, true) {
    private var wasFocusRequested = false

    override fun isSecondaryAction(action: AnAction, actionIndex: Int): Boolean {
      return actionIndex >= getWelcomeScreenPrimaryButtonsNum()
    }

    override fun actionsUpdated(forced: Boolean, newVisibleActions: List<AnAction>) {
      super.actionsUpdated(forced, newVisibleActions)
      if (forced && !newVisibleActions.isEmpty() && componentCount > 0 && !wasFocusRequested) {
        val obj = FocusUtil.findFocusableComponentIn(components[0], null)
        if (obj != null) {
          wasFocusRequested = true
          IdeFocusManager.getGlobalInstance().requestFocus(obj, true)
        }
      }
    }
  }
  actionToolbar.setLayoutStrategy(ToolbarLayoutStrategy.WRAP_STRATEGY)
  actionToolbar.setTargetComponent(actionToolbar.component)
  actionToolbar.setOpaque(false)
  return actionToolbar
}
