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
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.wm.IdeFocusManager
import com.intellij.ui.components.DropDownLink
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.components.panels.Wrapper
import com.intellij.ui.scale.JBUIScale.scale
import com.intellij.util.ui.FocusUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.FlowLayout
import java.awt.Font
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.SwingConstants

internal class EmptyStateProjectsPanel(parentDisposable: Disposable) : BorderLayoutPanel() {
  init {
    setBackground(WelcomeScreenUIManager.getMainAssociatedComponentBackground())
    val mainPanel: JPanel = NonOpaquePanel(VerticalFlowLayout())
    mainPanel.setBorder(JBUI.Borders.emptyTop(103))

    mainPanel.add(createTitle())
    mainPanel.add(createCommentLabel(IdeBundle.message("welcome.screen.empty.projects.create.comment")))
    mainPanel.add(createCommentLabel(IdeBundle.message("welcome.screen.empty.projects.open.comment")))

    val (actionsToolbar: ActionToolbarImpl, moreToolbar) = createActionToolbars(parentDisposable)

    mainPanel.add(Wrapper(FlowLayout(), actionsToolbar.component))
    mainPanel.add(Wrapper(FlowLayout(), moreToolbar.component))
    addToCenter(mainPanel)
  }

  // Returns main actions, more actions
  private fun createActionToolbars(parentDisposable: Disposable): Pair<ActionToolbarImpl, ActionToolbarImpl> {
    val actionManager = ActionManager.getInstance()
    val baseGroup = actionManager.getAction(IdeActions.GROUP_WELCOME_SCREEN_QUICKSTART_EMPTY_STATE) as ActionGroup
    val moreActionGroup = DefaultActionGroup(IdeBundle.message("welcome.screen.more.actions.link.text"), true)

    val toolbarGroup = object : ActionGroupWrapper(baseGroup) {
      override fun postProcessVisibleChildren(e: AnActionEvent, visibleChildren: List<AnAction>): List<AnAction> {
        moreActionGroup.removeAll()
        val mapped = visibleChildren.mapIndexedNotNull { index, action ->
          when {
            index >= getWelcomeScreenPrimaryButtonsNum() -> {
              moreActionGroup.add(action)
              null
            }
            action is ActionGroup && action is ActionsWithPanelProvider -> {
              val p = e.updateSession.presentation(action)
              val wrapper = p.getClientProperty(ActionUtil.INLINE_ACTIONS)?.first()
                            ?: ActionGroupPanelWrapper.wrapGroups(action, parentDisposable).also {
                              p.putClientProperty(ActionUtil.INLINE_ACTIONS, listOf(it))
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
    moreToolbar.setBorder(JBUI.Borders.emptyTop(5))
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

      override fun actionsUpdated(forced: Boolean, newVisibleActions: MutableList<out AnAction>) {
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
    actionToolbar.setLayoutStrategy(ToolbarLayoutStrategy.NOWRAP_STRATEGY)
    actionToolbar.setTargetComponent(actionToolbar.component)
    actionToolbar.setBorder(JBUI.Borders.emptyTop(27))
    actionToolbar.setOpaque(false)
    return actionToolbar
  }

  private fun createTitle(): JBLabel {
    val titleLabel = JBLabel(WelcomeScreenComponentFactory.getApplicationTitle(), SwingConstants.CENTER)
    titleLabel.setOpaque(false)
    val componentFont = titleLabel.getFont()
    titleLabel.setFont(componentFont.deriveFont(componentFont.getSize() + scale(13).toFloat()).deriveFont(Font.BOLD))
    titleLabel.setBorder(JBUI.Borders.emptyBottom(17))
    return titleLabel
  }

  fun createCommentLabel(text: @NlsContexts.HintText String): JBLabel {
    val commentFirstLabel = JBLabel(text, SwingConstants.CENTER)
    commentFirstLabel.setOpaque(false)
    commentFirstLabel.setForeground(UIUtil.getContextHelpForeground())
    return commentFirstLabel
  }
}