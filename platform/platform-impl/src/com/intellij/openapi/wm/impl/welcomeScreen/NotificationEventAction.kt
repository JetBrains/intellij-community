// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.application.subscribe
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeBalloonLayoutImpl.BalloonNotificationListener
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame.Companion.getInstance
import javax.swing.JComponent

internal class NotificationEventAction(parentDisposable: Disposable) : DumbAwareToggleAction(), CustomComponentAction {
  private var notificationTypes = listOf<NotificationType>()
  private var selected = false
  private var hideListenerInstalled = false
  private var myAutoPopup = false

  init {
    WelcomeBalloonLayoutImpl.BALLOON_NOTIFICATION_TOPIC.subscribe<BalloonNotificationListener>(
      parentDisposable,
      object : BalloonNotificationListener {
        override fun notificationsChanged(types: List<NotificationType?>) {
          notificationTypes = types.filterNotNull()
        }

        override fun newNotifications() {
          myAutoPopup = true
        }
      })

  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return selected
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    selected = state
  }

  override fun update(e: AnActionEvent) {
    super.update(e)

    e.presentation.icon = WelcomeScreenComponentFactory.getNotificationIcon(notificationTypes, getComponent(e))
    e.presentation.isEnabledAndVisible = notificationTypes.isNotEmpty()
  }

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    return object: ActionButton(this, presentation, place, ActionToolbar.DEFAULT_MINIMUM_BUTTON_SIZE) {
      override fun setBounds(x: Int, y: Int, width: Int, height: Int) {
        super.setBounds(x, y, width, height)
        checkAutoPopup(this)
      }
    }
  }

  override fun updateCustomComponent(component: JComponent, presentation: Presentation) {
    checkAutoPopup(component)
  }

  private fun checkAutoPopup(actionButton: JComponent) {
    if (actionButton.parent != null && actionButton.width > 0 && actionButton.height > 0 && actionButton.isVisible) {
      val balloonLayout = getInstance()?.balloonLayout as? WelcomeBalloonLayoutImpl
      if (myAutoPopup && balloonLayout != null && !balloonLayout.myVisible) {
        actionPerformed { balloonLayout.locationComponent as JComponent }
      }
      myAutoPopup = false
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) {
    super.actionPerformed(e)
    actionPerformed { getComponent(e) }
  }

  private fun actionPerformed(componentProvider: () -> JComponent) {
    val balloonLayout = getInstance()?.balloonLayout as? WelcomeBalloonLayoutImpl ?: return

    if (!hideListenerInstalled || balloonLayout.hideListener == null) {
      balloonLayout.setHideListener(Runnable {
        selected = false
      })
      hideListenerInstalled = true
    }

    val locationComponent = balloonLayout.locationComponent
    if (locationComponent == null || locationComponent.parent == null) {
      balloonLayout.locationComponent = componentProvider.invoke()
    }

    selected = true
    balloonLayout.showPopup()
  }

  private fun getComponent(e: AnActionEvent): JComponent {
    return (e.inputEvent?.component ?: e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)) as JComponent
  }
}
