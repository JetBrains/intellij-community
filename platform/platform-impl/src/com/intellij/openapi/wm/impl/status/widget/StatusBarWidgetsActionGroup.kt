// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget

import com.intellij.ide.lightEdit.LightEdit
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.extensions.ExtensionPointListener
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.keymap.KeymapExtension
import com.intellij.openapi.keymap.KeymapGroup
import com.intellij.openapi.keymap.KeymapGroupFactory
import com.intellij.openapi.keymap.impl.ui.ActionsTreeUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.Condition
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.UIBundle
import com.intellij.util.asDisposable
import com.intellij.util.ui.UIUtil
import kotlinx.coroutines.CoroutineScope
import org.intellij.lang.annotations.Language
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

internal class StatusBarWidgetsActionGroup : DefaultActionGroup() {
  companion object {
    @Language("devkit-action-id") const val GROUP_ID: String = "ViewStatusBarWidgetsGroup"
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val project = e?.project ?: return EMPTY_ARRAY

    val actions = mutableListOf<AnAction>()
    actions.addAll(super.getChildren(e))
    if (e.place == ActionPlaces.STATUS_BAR_PLACE && ExperimentalUI.isNewUI()) {
      val navBarLocationGroup = e.actionManager.getAction("NavbarLocationGroup")
      if (navBarLocationGroup is ActionGroup) {
        actions.add(navBarLocationGroup)
      }
    }
    if (!actions.isEmpty()) {
      actions.add(Separator.getInstance())
    }

    val widgetFactories = project.service<StatusBarWidgetsManager>().getWidgetFactories()
    actions += StatusBarActionManager.getInstance().getActionsFor(widgetFactories)
    actions.add(Separator.getInstance())
    actions.add(HideCurrentWidgetAction())
    return actions.toTypedArray()
  }
}

internal class ToggleWidgetAction(val widgetFactory: StatusBarWidgetFactory) : DumbAwareToggleAction(),
                                                                               ActionRemoteBehaviorSpecification.FrontendThenBackend {
  init {
    templatePresentation.text = UIBundle.message("status.bar.toggle.widget.action.name", widgetFactory.displayName)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    if (e.isFromSearchPopup) {
      e.presentation.text = UIBundle.message("status.bar.toggle.widget.action.name.search.everywhere", widgetFactory.displayName)
    }
    else {
      e.presentation.text = widgetFactory.displayName
    }

    val project = e.project
    if (project == null) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (LightEdit.owns(project) && widgetFactory !is LightEditCompatible) {
      e.presentation.isEnabledAndVisible = false
      return
    }

    if (ActionPlaces.isMainMenuOrActionSearch(e.place)) {
      e.presentation.isEnabledAndVisible = widgetFactory.isConfigurable && widgetFactory.isAvailable(project)
      return
    }

    val statusBar = e.getData(PlatformDataKeys.STATUS_BAR)
    e.presentation.isEnabledAndVisible = statusBar != null && project.service<StatusBarWidgetsManager>()
      .canBeEnabledOnStatusBar(widgetFactory, statusBar)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean = StatusBarWidgetSettings.getInstance().isEnabled(widgetFactory)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    StatusBarWidgetSettings.getInstance().setEnabled(widgetFactory, state)
    for (project in ProjectManager.getInstance().openProjects) {
      project.service<StatusBarWidgetsManager>().updateWidget(widgetFactory)
    }
  }
}

private class HideCurrentWidgetAction : DumbAwareAction() {
  companion object {
    private fun getFactory(e: AnActionEvent): StatusBarWidgetFactory? {
      val project = e.project ?: return null
      val hoveredWidgetId = e.getData(IdeStatusBarImpl.HOVERED_WIDGET_ID)  ?: return null
      e.getData(PlatformDataKeys.STATUS_BAR)  ?: return null
      return project.service<StatusBarWidgetsManager>().findWidgetFactory(hoveredWidgetId)
    }
  }

  override fun actionPerformed(e: AnActionEvent) {
    val factory = getFactory(e) ?: return
    StatusBarWidgetSettings.getInstance().setEnabled(factory = factory, newValue = false)
    for (project in ProjectManager.getInstance().openProjects) {
      project.getService(StatusBarWidgetsManager::class.java).updateWidget(factory)
    }
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    val factory = getFactory(e)
    e.presentation.isEnabledAndVisible = factory != null && factory.isConfigurable
    if (factory != null) {
      e.presentation.text = UIBundle.message("status.bar.hide.widget.action.name", factory.displayName)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

@Service(Service.Level.APP)
@ApiStatus.Internal
class StatusBarActionManager(coroutineScope: CoroutineScope) {
  companion object {
    fun getInstance(): StatusBarActionManager = service()

    private const val TOGGLE_ACTION_ID_PREFIX = "StatusBarWidgets.Toggle."

    private fun getToggleActionId(widgetFactory: StatusBarWidgetFactory): @NonNls String {
      return "$TOGGLE_ACTION_ID_PREFIX${widgetFactory.id.replace(" ", "")}"
    }
  }

  init {
    StatusBarWidgetFactory.EP_NAME.point.addExtensionPointListener(object : ExtensionPointListener<StatusBarWidgetFactory> {
      override fun extensionAdded(widgetFactory: StatusBarWidgetFactory, pluginDescriptor: PluginDescriptor) {
        if (widgetFactory.isConfigurable) { // avoid creating actions in 'Settings | Keymap' for implementation-detail widgets
          val actionId = getToggleActionId(widgetFactory)

          val oldAction = ActionManager.getInstance().getAction(actionId)
          if (oldAction == null) {
            ActionManager.getInstance().registerAction(actionId, ToggleWidgetAction(widgetFactory))
          }
          else {
            logger<StatusBarWidgetFactory>().debug("Skip $actionId - already registered as $oldAction");
          }
        }
      }

      override fun extensionRemoved(widgetFactory: StatusBarWidgetFactory, pluginDescriptor: PluginDescriptor) {
        val actionId = getToggleActionId(widgetFactory)
        if (ActionManager.getInstance().getAction(actionId) is ToggleWidgetAction) {
          ActionManager.getInstance().unregisterAction(actionId)
        }
      }
    }, true, coroutineScope.asDisposable())
  }

  internal fun getStatusBarToggleActions(): List<AnAction> {
    val actionManager = ActionManager.getInstance()
    return StatusBarWidgetFactory.EP_NAME.extensionList
      .filter { it.isConfigurable }
      .mapNotNull { factory -> actionManager.getActionOrStub(getToggleActionId(factory)) }
  }

  fun getActionsFor(widgetFactories: Collection<StatusBarWidgetFactory>): List<AnAction> {
    return widgetFactories
      .filter { it.isConfigurable }
      .map { widgetFactory -> getActionFor(widgetFactory) }
  }

  fun getActionFor(widgetFactory: StatusBarWidgetFactory): AnAction {
    return ActionManager.getInstance().getAction(getToggleActionId(widgetFactory)) ?: ToggleWidgetAction(widgetFactory)
  }
}

@ApiStatus.Internal
class StatusBarKeymapExtension : KeymapExtension {
  override fun createGroup(filtered: Condition<in AnAction?>?, project: Project?): KeymapGroup? {
    val title = UIUtil.removeMnemonic(UIBundle.message("group.StatusBar.KeymapGroup.text"))
    val result = KeymapGroupFactory.getInstance().createGroup(title)

    val widgetActions = StatusBarActionManager.getInstance().getStatusBarToggleActions()
    for (action in widgetActions) {
      ActionsTreeUtil.addAction(result, action, filtered)
    }
    return result
  }

  override fun getGroupLocation(): KeymapExtension.KeymapLocation {
    return KeymapExtension.KeymapLocation.OTHER
  }
}
