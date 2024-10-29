// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.hotswap

import com.intellij.execution.multilaunch.design.components.RoundedCornerBorder
import com.intellij.icons.AllIcons
import com.intellij.notification.Notification
import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.Balloon
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.wm.WindowManager
import com.intellij.ui.BalloonImpl
import com.intellij.ui.JBColor
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.xdebugger.XDebuggerBundle
import com.intellij.xdebugger.impl.hotswap.HotSwapUiExtension.SuccessStatusLocation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.jetbrains.annotations.ApiStatus
import java.awt.BorderLayout
import java.awt.Point
import java.util.concurrent.CopyOnWriteArrayList
import javax.swing.JPanel
import javax.swing.SwingConstants
import kotlin.time.Duration.Companion.seconds

/**
 * Service to synchronize notifications showing during hot swap.
 * @see trackNotification
 */
@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class HotSwapStatusNotificationManager private constructor(private val project: Project) : Disposable.Default {
  companion object {
    @JvmStatic
    fun getInstance(project: Project): HotSwapStatusNotificationManager = project.service()
  }

  private val notifications = CopyOnWriteArrayList<Notification>()

  /**
   * Add the notification to a tracking list, that will be cleaned after [clearNotifications] call.
   */
  fun trackNotification(notification: Notification) {
    notification.whenExpired { notifications.remove(notification) }
    notifications.add(notification)
  }

  /**
   * Expire all previously added notifications.
   */
  internal fun clearNotifications() {
    notifications.toArray().forEach { (it as Notification).expire() }
  }

  internal fun showSuccessNotification(scope: CoroutineScope) {
    when (successStatusLocation) {
      null -> return
      SuccessStatusLocation.IDE_POPUP -> {
        scope.launch(Dispatchers.EDT) {
          val frame = WindowManager.getInstance().getFrame(project) ?: return@launch
          val factory = JBPopupFactory.getInstance() ?: return@launch

          val balloon = factory.createBalloonBuilder(SuccessfulHotSwapComponent())
            .setBorderColor(JBColor.border())
            .setCornerRadius(JBUI.scale(RADIUS))
            .setBorderInsets(JBUI.emptyInsets())
            .setFadeoutTime(NOTIFICATION_TIME_SECONDS.seconds.inWholeMilliseconds)
            .setBlockClicksThroughBalloon(true)
            .setHideOnAction(false)
            .setHideOnClickOutside(false)
            .createBalloon()
          if (balloon is BalloonImpl) {
            balloon.setShowPointer(false)
          }
          balloon.show(RelativePoint(frame, Point(frame.width / 2, 2 * frame.height / 3)), Balloon.Position.below)
        }
      }
      SuccessStatusLocation.NOTIFICATION -> {
        val notification = NotificationGroupManager.getInstance().getNotificationGroup("HotSwap Messages")
          .createNotification(XDebuggerBundle.message("xdebugger.hotswap.status.success"), NotificationType.INFORMATION)
        trackNotification(notification)
        notification.icon = AllIcons.Status.Success
        notification.notify(project)
        scope.launch(Dispatchers.Default) {
          delay(NOTIFICATION_TIME_SECONDS.seconds)
          notification.expire()
        }
      }
    }
  }
}

private const val RADIUS = 10
internal const val NOTIFICATION_TIME_SECONDS = 3

private class SuccessfulHotSwapComponent : JPanel(BorderLayout()) {
  init {
    isOpaque = false
    border = RoundedCornerBorder(JBUI.scale(RADIUS))
    add(JBLabel(XDebuggerBundle.message("xdebugger.hotswap.status.success"), AllIcons.Status.Success, SwingConstants.CENTER))
  }
}

private val successStatusLocation: SuccessStatusLocation?
  get() = HotSwapUiExtension.computeSafeIfAvailable { it.successStatusLocation }
