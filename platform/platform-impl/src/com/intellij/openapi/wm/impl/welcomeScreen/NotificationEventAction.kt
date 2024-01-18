// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.application.subscribe
import com.intellij.ide.IdeBundle
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeBalloonLayoutImpl.BalloonNotificationListener
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeFrame.Companion.getInstance
import javax.swing.JComponent

internal class NotificationEventAction(parentDisposable: Disposable) : DumbAwareToggleAction() {
  private var notificationTypes = listOf<NotificationType>()
  private var selected = false
  private var hideListenerInstalled = false

  init {
    WelcomeBalloonLayoutImpl.BALLOON_NOTIFICATION_TOPIC.subscribe<BalloonNotificationListener>(
      parentDisposable,
      BalloonNotificationListener { types -> notificationTypes = types.filterNotNull() })
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
    e.presentation.text = IdeBundle.message("toolwindow.stripe.Notifications")
    e.presentation.isEnabledAndVisible = notificationTypes.isNotEmpty()
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.EDT
  }

  override fun actionPerformed(e: AnActionEvent) {
    super.actionPerformed(e)
    val balloonLayout = getInstance()?.balloonLayout as? WelcomeBalloonLayoutImpl ?: return

    if (!hideListenerInstalled || balloonLayout.hideListener == null) {
      balloonLayout.setHideListener(Runnable {
        selected = false
      })
      hideListenerInstalled = true
    }

    val locationComponent = balloonLayout.locationComponent
    if (locationComponent == null || locationComponent.parent == null) {
      balloonLayout.locationComponent = getComponent(e)
    }

    selected = true
    balloonLayout.showPopup()
  }

  private fun getComponent(e: AnActionEvent): JComponent {
    return (e.inputEvent?.component ?: e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT)) as JComponent
  }
}
