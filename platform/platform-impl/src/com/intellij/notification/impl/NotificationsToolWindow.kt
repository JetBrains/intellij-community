// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.notification.impl

import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.notification.impl.ui.NotificationsPanel
import com.intellij.notification.impl.widget.IdeNotificationArea
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowContentUiType
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.openapi.wm.ex.ToolWindowEx
import com.intellij.openapi.wm.ex.ToolWindowManagerListener
import com.intellij.platform.ide.productMode.IdeProductMode
import com.intellij.ui.content.ContentFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus

internal class NotificationsToolWindowFactory : ToolWindowFactory, DumbAware {
  companion object {
    const val ID: String = "Notifications"
  }

  /**
   * A light process shows a notification as a balloon only, and the tool window stays hidden.
   * The move out of the light mode makes it available, see [manage].
   */
  override fun shouldBeAvailable(project: Project): Boolean = !IdeProductMode.isLight

  override suspend fun manage(toolWindow: ToolWindow, toolWindowManager: ToolWindowManager) {
    fun updateIcon() {
      val notifications = ApplicationNotificationsModel.getStateNotifications(toolWindow.project)
      val icon = IdeNotificationArea.getActionCenterNotificationIcon(notifications)
      toolWindow.setIcon(icon)
    }

    withContext(Dispatchers.EDT) {
      val connection = ApplicationManager.getApplication().getMessageBus().connect(this)
      connection.subscribe(ApplicationNotificationsModel.STATE_CHANGED, ApplicationNotificationsModel.StateEventListener {
        updateIcon()
      })
      // a move between the light mode and the smart mode is a plugin set change
      connection.subscribe(DynamicPluginListener.TOPIC, object : DynamicPluginListener {
        override fun pluginsLoaded() {
          toolWindow.isAvailable = !IdeProductMode.isLight
        }

        override fun pluginsUnloaded() {
          toolWindow.isAvailable = !IdeProductMode.isLight
        }
      })
      updateIcon()
      awaitCancellation()
    }
  }

  override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
    toolWindow.setContentUiType(ToolWindowContentUiType.TABBED, null)
    val component = NotificationsPanel(project, toolWindow.disposable)
    ApplicationNotificationsModel.register(project, component)
    Disposer.register(component, Disposable { ApplicationNotificationsModel.unregister(project) })

    project.messageBus.connect(component).subscribe(ToolWindowManagerListener.TOPIC, object : ToolWindowManagerListener {
      private var wasVisible = false

      override fun stateChanged(toolWindowManager: ToolWindowManager) {
        val visible = toolWindow.isVisible
        if (wasVisible != visible) {
          wasVisible = visible
          if (visible) {
            ApplicationNotificationsModel.fireNotificationsPanelVisible(project)
            component.updateComponents()
          }
          else {
            ApplicationNotificationsModel.markAllRead(project)
          }
        }
      }
    })

    val content = ContentFactory.getInstance().createContent(component.component, "", false)
    content.preferredFocusableComponent = component.component

    val contentManager = toolWindow.contentManager
    contentManager.addContent(content)
    contentManager.setSelectedContent(content)

    (toolWindow as ToolWindowEx).setAdditionalGearActions(component.createActions())
  }
}

@ApiStatus.Internal
object NotificationsStateWatcher {
  fun hasSuggestionNotifications(project: Project): Boolean {
    val notifications = ApplicationNotificationsModel.getNotifications(project)
    return notifications.any { it.isSuggestionType }
  }
}
