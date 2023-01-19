// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.status.widget

import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.wm.impl.status.IdeStatusBarImpl
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.UIBundle

internal class StatusBarWidgetsActionGroup : DefaultActionGroup() {
  companion object {
    const val GROUP_ID = "ViewStatusBarWidgetsGroup"
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

    val manager = project.service<StatusBarWidgetsManager>()
    manager.getWidgetFactories().mapTo(actions) { ToggleWidgetAction(it) }
    actions.add(Separator.getInstance())
    actions.add(HideCurrentWidgetAction())
    return actions.toTypedArray()
  }

  private class ToggleWidgetAction(private val widgetFactory: StatusBarWidgetFactory) : DumbAwareToggleAction(widgetFactory.displayName) {
    override fun update(e: AnActionEvent) {
      super.update(e)
      val project = e.project
      if (project == null) {
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