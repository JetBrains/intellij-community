// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.toolWindow

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.HelpTooltip
import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.ide.actions.ToolWindowsGroup
import com.intellij.ide.ui.NotRoamableUiSettings
import com.intellij.ide.ui.customization.ActionUrl
import com.intellij.ide.ui.customization.CustomActionsListener.Companion.fireSchemaChanged
import com.intellij.ide.ui.customization.CustomActionsSchema
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.IdeActions.GROUP_MAIN_TOOLBAR_CENTER
import com.intellij.openapi.actionSystem.IdeActions.GROUP_MAIN_TOOLBAR_NEW_UI
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.ex.InlineActionsHolder
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.actionSystem.impl.ActionMenu
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.*
import com.intellij.openapi.keymap.impl.ui.Group
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowAnchor
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.openapi.wm.impl.*
import com.intellij.ui.MouseDragHelper
import com.intellij.ui.NewUI
import com.intellij.ui.popup.KeepingPopupOpenAction
import com.intellij.util.PlatformUtils
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ConcurrentFactoryMap
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.UIUtil
import org.jdom.Element
import javax.swing.JComponent

private const val STRIPE_ACTION_GROUP_ID = "TopStripeActionGroup"

class StripeActionGroup: ActionGroup(), DumbAware {
  private val myFactory: Map<ActivateToolWindowAction, AnAction> = ConcurrentFactoryMap.create(::createAction) {
    CollectionFactory.createConcurrentWeakKeyWeakValueMap()
  }
  private val myMore = MyMoreAction()

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    super.update(e)
    val hide = ActionPlaces.isMainMenuOrActionSearch(e.place) || !NotRoamableUiSettings.getInstance().experimentalSingleStripe
    e.presentation.apply {
      isEnabled = isEnabled && !hide
      isVisible = isVisible && !hide
    }
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return emptyArray()
    val twm = ToolWindowManagerEx.getInstanceEx(project)
    val toolWindows = ToolWindowsGroup.getToolWindowActions(project, false)
    val actions = toolWindows.sortedBy { getOrder(twm, it.toolWindowId) }.mapNotNullTo(ArrayList(), myFactory::get)
    actions += myMore
    return actions.toTypedArray()
  }

  private fun getOrder(twm: ToolWindowManagerEx, twId: String): Int =
    (twm.getLayout().getInfo(twId) ?: (twm as? ToolWindowManagerImpl)?.getEntry(twId)?.readOnlyWindowInfo)?.run {
      when (anchor) {
        ToolWindowAnchor.LEFT -> order
        ToolWindowAnchor.TOP -> 100 + order
        ToolWindowAnchor.BOTTOM -> if (isSplit) 250 + order else 200 - order
        ToolWindowAnchor.RIGHT -> if (isSplit) 300 + order else 350 - order
        else -> -1
      }
    } ?: -1

  private fun createAction(activateAction: ActivateToolWindowAction) = MyButtonAction(activateAction)

  private class MyButtonAction(val activateAction: ActivateToolWindowAction)
    : DumbAwareToggleAction(activateAction.templateText, null, activateAction.templatePresentation.icon), CustomComponentAction {
    private var project: Project? = null

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean {
      activateAction.update(e)
      e.presentation.isVisible = buttonState.isPinned(activateAction.toolWindowId)
      return e.project?.let { ToolWindowManagerEx.getInstanceEx(it) }?.getToolWindow(activateAction.toolWindowId)?.isVisible == true
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      val project = e.project ?: return
      val twm = ToolWindowManager.getInstance(project)
      val toolWindowId = activateAction.toolWindowId
      val toolWindow = twm.getToolWindow(toolWindowId)
      val visible = toolWindow?.isVisible == true
      if (visible == state) {
        return
      }
      if (visible) {
        if (twm is ToolWindowManagerImpl) {
          twm.hideToolWindow(toolWindowId, false, true, false, ToolWindowEventSource.StripeButton)
        }
        else {
          toolWindow!!.hide(null)
        }
      }
      else {
        activateAction.actionPerformed(e)
      }
    }

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return object : AbstractSquareStripeButton(this@MyButtonAction, presentation, { ActionToolbar.experimentalToolbarMinimumButtonSize() }), ToolWindowDragHelper.ToolWindowProvider {
        init {
          doInit { createPopupGroup() }
          MouseDragHelper.setComponentDraggable(this, true)
        }

        private fun createPopupGroup(): DefaultActionGroup {
          val group = DefaultActionGroup()
          group.add(TogglePinActionBase(activateAction.toolWindowId))
          group.addSeparator()
          group.add(SquareStripeButton.createMoveGroup())
          return group
        }

        override val toolWindow: ToolWindowImpl?
          get() = project?.let { ToolWindowManagerEx.getInstanceEx(it) }?.getToolWindow(activateAction.toolWindowId) as? ToolWindowImpl

        override fun addNotify() {
          super.addNotify()
          project = PlatformDataKeys.PROJECT.getData(dataContext)
          project?.service<ButtonsRepaintService>()?.trackButton(this)
        }

        override fun removeNotify() {
          super.removeNotify()
          project?.service<ButtonsRepaintService>()?.unTrackButton(this)
          project = null
        }
      }
    }
  }

  private class MyMoreAction: DumbAwareAction("..."), CustomComponentAction {
    override fun actionPerformed(e: AnActionEvent) {
    }

    private fun getChildren(e: AnActionEvent?): List<AnAction> {
      val project = e?.project ?: return emptyList()
      val children = ToolWindowsGroup.getToolWindowActions(project, false).map { ac ->
        object : AnActionWrapper(ac), InlineActionsHolder {
          private val inlineActions = listOf(TogglePinAction(ac.toolWindowId))
          override fun getInlineActions(): List<AnAction> = inlineActions
        }
      }
      return children
    }

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
      return object : AbstractMoreSquareStripeButton(this, { ActionToolbar.experimentalToolbarMinimumButtonSize() }) {
        init {
          setLook(SquareStripeButtonLook(this))
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
    private val buttons = ContainerUtil.createWeakSet<ActionButton>()
    init {
      project.messageBus.connect(this).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
        override fun stateChanged(toolWindowManager: ToolWindowManager) {
          UIUtil.invokeLaterIfNeeded(this@ButtonsRepaintService::repaintButtons)
        }
      })
    }

    @RequiresEdt
    fun trackButton(btn: ActionButton) {
      buttons.add(btn)
    }

    @RequiresEdt
    fun unTrackButton(btn: ActionButton) {
      buttons.remove(btn)
    }

    @RequiresEdt
    fun repaintButtons() {
      val toolbars = buttons.mapNotNullTo(LinkedHashSet()) { ActionToolbar.findToolbarBy(it) }
      toolbars.forEach { it.updateActionsImmediately() }
    }

    override fun dispose() {
    }
  }

}

private open class TogglePinActionBase(val toolWindowId: String): DumbAwareAction(ActionsBundle.messagePointer("action.TopStripePinButton.text")) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun update(e: AnActionEvent) {
    val pinned = buttonState.isPinned(toolWindowId)
    Toggleable.setSelected(e.presentation, pinned)
    e.presentation.text = if (pinned)
      ActionsBundle.message("action.TopStripeUnPinButton.text")
    else
      ActionsBundle.message("action.TopStripePinButton.text")
  }

  override fun actionPerformed(e: AnActionEvent) {
    buttonState.setPinned(toolWindowId, !Toggleable.isSelected(e.presentation))
    ActionToolbarImpl.updateAllToolbarsImmediately()
  }
}
private class TogglePinAction(toolWindowId: String): TogglePinActionBase(toolWindowId), KeepingPopupOpenAction {
  override fun update(e: AnActionEvent) {
    super.update(e)
    val pinned = Toggleable.isSelected(e.presentation)
    e.presentation.icon = if (!pinned) ExpUiIcons.General.Pin else ExpUiIcons.General.PinSelected
    e.presentation.selectedIcon = if (!pinned) ExpUiIcons.General.PinHovered else ExpUiIcons.General.PinSelectedHovered
    e.presentation.putClientProperty(ActionMenu.ALWAYS_VISIBLE, pinned)
  }
}

@Service
@State(name = "SingleStripeButtonsState", storages = [Storage("window.state.xml")])
private class ButtonsStateService: PersistentStateComponent<Element> {
  private val pinnedIds = linkedSetOf("Database", "Project", "Services")

  fun isPinned(id: String): Boolean = id in pinnedIds
  fun setPinned(id: String, pinned: Boolean) {
    if (pinned) {
      pinnedIds += id
    }
    else {
      pinnedIds -= id
    }
  }

  override fun getState(): Element = Element("pinnedIds").apply {
    for (id in pinnedIds) {
      addContent(Element("id").apply {
        setText(id)
      })
    }
  }

  override fun loadState(state: Element) {
    pinnedIds.clear()
    for (child in state.children) {
      if (child.name == "id") {
        pinnedIds.add(child.text)
      }
    }
  }
}

private val buttonState get() = ApplicationManager.getApplication().service<ButtonsStateService>()


class EnableStripeGroup : ToggleAction(), DumbAware {
  companion object {
    private val customizedGroup get() = getGroupPath(GROUP_MAIN_TOOLBAR_NEW_UI, GROUP_MAIN_TOOLBAR_CENTER)

    fun setSingleStripeEnabled(enabled: Boolean) {
      if (enabled) updateActionGroup(true, customizedGroup ?: return, STRIPE_ACTION_GROUP_ID)
      NotRoamableUiSettings.getInstance().experimentalSingleStripe = enabled
    }

    fun isSingleStripeEnabled() = hasActionOnToolbar()
                                  && shouldSingleStripeBeEnabled()

    fun shouldSingleStripeBeEnabled() = NotRoamableUiSettings.getInstance().experimentalSingleStripe

    fun hasActionOnToolbar() = customizedGroup?.let { isActionGroupAdded(it, STRIPE_ACTION_GROUP_ID) } == true

    @Suppress("SameParameterValue")
    private fun isActionGroupAdded(groupPath: List<String>, actionId: String): Boolean {
      return CustomActionsSchema.getInstance().getActions().find { it.groupPath == groupPath && matchesId(it.component, actionId) } != null
    }

    @Suppress("SameParameterValue")
    private fun updateActionGroup(add: Boolean, groupPath: List<String>, actionId: String) {
      val globalSchema = CustomActionsSchema.getInstance()
      val actions = globalSchema.getActions().toMutableList()
      actions.removeIf { it.groupPath == groupPath && matchesId(it.component, actionId) }
      if (add) {
        actions.add(ActionUrl(ArrayList(groupPath), actionId, ActionUrl.ADDED, 0))
      }
      globalSchema.setActions(actions)
      fireSchemaChanged()
      globalSchema.setCustomizationSchemaForCurrentProjects()
    }

    private fun matchesId(component: Any?, actionId: String) = when (component) {
      is AnAction -> ActionManager.getInstance().getId(component) == actionId
      is Group -> component.id == actionId
      else -> component == actionId
    }

    @Suppress("SameParameterValue")
    private fun getGroupPath(vararg ids: String): List<String>? {
      val globalSchema = CustomActionsSchema.getInstance()
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
    e.presentation.isEnabledAndVisible = NewUI.isEnabled() && customizedGroup != null && (PlatformUtils.isDataGrip() || Toggleable.isSelected(e.presentation))
  }

  override fun isSelected(e: AnActionEvent): Boolean =
    isSingleStripeEnabled()

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    setSingleStripeEnabled(state)
  }

}