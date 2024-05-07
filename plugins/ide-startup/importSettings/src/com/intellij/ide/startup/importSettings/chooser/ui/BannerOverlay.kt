// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.NotificationData
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.openapi.wm.impl.welcomeScreen.WelcomeScreenUIManager
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import com.intellij.ui.PlainInlineBanner
import com.intellij.ui.components.ActionLink
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.lifetime.Lifetime
import java.awt.Dimension
import javax.swing.*

internal class BannerOverlay(comp: JComponent) {

  private val container = JPanel(VerticalFlowLayout(0, 0)).apply { isOpaque = false }
  private val pane = wrapComponent(comp)

  val component: JComponent
    get() {
      return pane
    }

  fun showOverlay(notification: NotificationData, lifetime: Lifetime, isPlain: Boolean) {
    clearNotifications()
    container.add(getOverlay(notification, isPlain))

    lifetime.onTermination {
      clearNotifications()
    }
  }

  private fun getOverlay(notification: NotificationData, isPlain: Boolean): JComponent {
    return when (notification.status) {
             NotificationData.NotificationStatus.INFO -> EditorNotificationPanel.Status.Info
             NotificationData.NotificationStatus.SUCCESS -> EditorNotificationPanel.Status.Success
             NotificationData.NotificationStatus.WARNING -> EditorNotificationPanel.Status.Warning
             NotificationData.NotificationStatus.ERROR -> EditorNotificationPanel.Status.Error
             else -> null
           }?.let {
      val banner =
        if (isPlain) PlainInlineBanner(notification.message, it).apply {
          background = WelcomeScreenUIManager.getMainAssociatedComponentBackground()
        }
        else InlineBanner(notification.message, it)

      notification.customActionList.forEach {
        banner.addAction(it.name) {
          it.action()
          banner.close()
        }
      }

      banner

    } ?: run {
      val lb = if (notification.status == NotificationData.NotificationStatus.WAITING) {
        JLabel(notification.message, AnimatedIcon.Default.INSTANCE, SwingConstants.LEADING)
      }
      else {
        JLabel(notification.message)
      }
      panel {
        row {
          cell(lb).align(Align.CENTER)
        }.resizableRow()
        if (notification.customActionList.isNotEmpty()) {
          row {
            cell(JPanel(HorizontalLayout(JBUI.scale(5))).apply {
              isOpaque = false
              notification.customActionList.forEach { act ->
                add(ActionLink(act.name) { act.action() })
              }
            }).align(Align.CENTER)
          }.resizableRow()
        }
      }
    }

  }

  fun clearNotifications() {
    container.removeAll()
    container.revalidate()
    container.repaint()
  }

  private fun wrapComponent(comp: JComponent): JPanel {
    val res = object : JPanel() {
      override fun getPreferredSize(): Dimension = comp.preferredSize
      override fun getMinimumSize(): Dimension = comp.minimumSize
      override fun getMaximumSize(): Dimension = comp.maximumSize
    }
    val overlay = container
    comp.alignmentX = 0f
    comp.alignmentY = 0f
    overlay.alignmentX = 0f
    overlay.alignmentY = 0f
    res.layout = OverlayLayout(res)
    res.add(overlay)
    res.add(comp)
    return res
  }

}