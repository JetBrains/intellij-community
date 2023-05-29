package com.intellij.toolWindow

import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.customization.ActionUrl
import com.intellij.ide.ui.customization.CustomActionsListener.Companion.fireSchemaChanged
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.ide.ui.customization.CustomActionsSchema.Companion.getInstance
import com.intellij.ide.ui.customization.CustomActionsSchema.Companion.setCustomizationSchemaForCurrentProjects
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.IdeActions.GROUP_MAIN_TOOLBAR_CENTER
import com.intellij.openapi.actionSystem.IdeActions.GROUP_MAIN_TOOLBAR_NEW_UI
import com.intellij.openapi.actionSystem.ex.ActionButtonLook
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.impl.ui.Group
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.SquareAnActionButton
import com.intellij.openapi.wm.impl.SquareStripeButton
import com.intellij.openapi.wm.impl.SquareStripeButtonLook
import com.intellij.openapi.wm.impl.ToolWindowImpl
import com.intellij.ui.NewUI
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

private const val STRIPE_ACTION_GROUP_ID = "TopStripeActionGroup"

class StripeActionGroup: ActionGroup(), DumbAware {
  private val myFactory: Map<ToolWindowImpl, AnAction> = ConcurrentFactoryMap.create(::createAction) {
    ContainerUtil.createConcurrentWeakKeyWeakValueMap()
  }
  private val myMore = MyMoreAction()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val hide = ActionPlaces.isMainMenuOrActionSearch(e.place)
    e.presentation.apply {
      isEnabled = isEnabled && !hide
      isVisible = isVisible && !hide
    }
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val twm = e?.project?.let { ToolWindowManager.getInstance(it) } ?: return emptyArray()
    val toolWindows = twm.toolWindowIds.mapNotNullTo(ArrayList()) { twm.getToolWindow(it) as? ToolWindowImpl }
    toolWindows.sortBy(::getOrder)
    val actions = toolWindows.mapNotNullTo(ArrayList(), myFactory::get)
    actions += myMore
    return actions.toTypedArray()
  }

  private fun getOrder(tw: ToolWindowImpl): Int =
    tw.windowInfo.run {
      when (anchor) {
        ToolWindowAnchor.LEFT -> 0 + order
        ToolWindowAnchor.TOP -> 100 + order
        ToolWindowAnchor.BOTTOM -> 200 + order
        ToolWindowAnchor.RIGHT -> 300 + order
        else -> -1
      }
    }

  private fun createAction(tw: ToolWindowImpl) = MyButtonAction(tw)

  private class MyButtonAction(tw: ToolWindowImpl): SquareAnActionButton(tw), CustomComponentAction {
    override fun isSelected(e: AnActionEvent): Boolean = super.isSelected(e).apply {
      e.presentation.isEnabledAndVisible = ToolWindowManager.getInstance(window.project).isStripeButtonShow(window)
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return object : SquareStripeButton(this@MyButtonAction, window) {
        override fun isFocused(): Boolean = false

        override fun addNotify() {
          super.addNotify()
          window.project.service<ButtonsRepaintService>().trackButton(this)
        }

        override fun removeNotify() {
          super.removeNotify()
          window.project.service<ButtonsRepaintService>().unTrackButton(this)
        }

        override fun setLook(look: ActionButtonLook?) {
          if (look is SquareStripeButtonLook) super.setLook(look)
        }

        override fun getAlignment(anchor: ToolWindowAnchor, splitMode: Boolean): HelpTooltip.Alignment {
          return HelpTooltip.Alignment.BOTTOM
        }
      }
    }
  }

  private class MyMoreAction: DumbAwareAction("..."), CustomComponentAction {
    override fun actionPerformed(e: AnActionEvent) {
    }

    private fun getChildren(e: AnActionEvent?): List<AnAction> {
      val project = e?.project ?: return emptyList()
      return ToolWindowsGroup.getToolWindowActions(project, true)
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return object : AbstractMoreSquareStripeButton(this) {
        init {
          setLook(SquareStripeButtonLook(this))
        }

        override fun setLook(look: ActionButtonLook?) {
          if (look is SquareStripeButtonLook) super.setLook(look)
        }

        override val side: ToolWindowAnchor
          get() = ToolWindowAnchor.TOP

        override fun actionPerformed(event: AnActionEvent) {
          HelpTooltip.hide(this)
          showActionGroupPopup(DefaultActionGroup(getChildren(event)), event)
        }
      }
    }
  }


  @Service(Service.Level.PROJECT)
  private class ButtonsRepaintService(project: Project): Disposable {
    private val buttons = ContainerUtil.createWeakSet<SquareStripeButton>()
    init {
      project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
        override fun stateChanged(toolWindowManager: ToolWindowManager) {
          UIUtil.invokeAndWaitIfNeeded(this@ButtonsRepaintService::repaintButtons)
        }
      })
    }

    @RequiresEdt
    fun trackButton(btn: SquareStripeButton) {
      buttons.add(btn)
    }

    @RequiresEdt
    fun unTrackButton(btn: SquareStripeButton) {
      buttons.remove(btn)
    }

    @RequiresEdt
    fun repaintButtons() {
      for (button in buttons.toList()) {
        button.repaint()
      }
    }

    override fun dispose() {
    }
  }

}

class EnableStripeGroup : ToggleAction(), DumbAware {
  companion object {
    private val customizedGroup get() = getGroupPath(GROUP_MAIN_TOOLBAR_NEW_UI, GROUP_MAIN_TOOLBAR_CENTER)

    fun setSingleStripeEnabled(enabled: Boolean) {
      updateActionGroup(enabled, customizedGroup ?: return, STRIPE_ACTION_GROUP_ID)
      UISettings.getInstance().hideToolStripes = enabled
    }

    private fun isSingleStripeEnabled() = customizedGroup?.let { isActionGroupAdded(it, STRIPE_ACTION_GROUP_ID) } == true

    private fun isActionGroupAdded(groupPath: List<String>, actionId: String): Boolean {
      return getInstance().getActions().find { it.groupPath == groupPath && matchesId(it.component, actionId) } != null
    }

    private fun updateActionGroup(add: Boolean, groupPath: List<String>, actionId: String) {
      val globalSchema = getInstance()
      val actions = globalSchema.getActions().toMutableList()
      actions.removeIf { it.groupPath == groupPath && matchesId(it.component, actionId) }
      if (add) {
        actions.add(ActionUrl(ArrayList(groupPath), actionId, ActionUrl.ADDED, 0))
      }
      globalSchema.setActions(actions)
      fireSchemaChanged()
      setCustomizationSchemaForCurrentProjects()
    }

    private fun matchesId(component: Any?, actionId: String) = when (component) {
      is AnAction -> ActionManager.getInstance().getId(component) == actionId
      is Group -> component.id == actionId
      else -> component == actionId
    }

    private fun getGroupPath(vararg ids: String): List<String>? {
      val globalSchema = getInstance()
      val groupPath = ArrayList<String>()
      groupPath += "root"
      for (id in ids) {
        groupPath += getActionName(globalSchema, id) ?: return null
      }
      return groupPath
    }

    private fun getActionName(globalSchema: CustomActionsSchema, actionId: String): String? =
      globalSchema.getDisplayName(actionId) ?: ActionManager.getInstance().getActionOrStub(actionId)?.templateText
  }
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabledAndVisible = NewUI.isEnabled() && customizedGroup != null
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    isSingleStripeEnabled()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    setSingleStripeEnabled(state)
  }

}