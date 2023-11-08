// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.data.NotificationData
import com.intellij.openapi.ui.VerticalFlowLayout
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.InlineBanner
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.OverlayLayout

class BannerOverlay {
  private val container = JPanel(VerticalFlowLayout(0, 0)).apply { isOpaque = false }

  fun showError(notification: NotificationData) {
    clearNotifications()

    val banner = InlineBanner(notification.message, getBannerStatus(notification.status))
    notification.customActionList.forEach {
      banner.addAction(it.name) {
        it.action()
        banner.close()
      }
    }
    container.add(banner)
  }

  private fun getBannerStatus(status: NotificationData.NotificationStatus): EditorNotificationPanel.Status {
    return when (status) {
      NotificationData.NotificationStatus.INFO -> EditorNotificationPanel.Status.Info
      NotificationData.NotificationStatus.SUCCESS -> EditorNotificationPanel.Status.Success
      NotificationData.NotificationStatus.WARNING -> EditorNotificationPanel.Status.Warning
      NotificationData.NotificationStatus.ERROR -> EditorNotificationPanel.Status.Error
    }
  }

  private fun clearNotifications() {
    container.removeAll()
    container.revalidate()
    container.repaint()
  }

  fun wrapComponent(comp: JComponent): JPanel {
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