// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.Notifications
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.EDT
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import com.intellij.util.ui.accessibility.ScreenReader
import com.jetbrains.JBR
import org.jetbrains.annotations.ApiStatus
import java.awt.Container
import java.awt.KeyboardFocusManager

@Service(Service.Level.APP)
class NotificationsAnnouncer {
  companion object {
    private val LOG = Logger.getInstance(this::class.java)
    private val mode: Int get() = Registry.intValue("ide.accessibility.announcing.notifications.mode")

    @ApiStatus.Experimental
    @JvmStatic
    fun isEnabled(): Boolean {
      return ScreenReader.isActive() && mode != 0 && JBR.isAccessibleAnnouncerSupported()
    }

    private fun doNotify(notification: Notification, project: Project?) {
      if (!isEnabled()) return
      EDT.assertIsEdt()

      if (project != null) {
        val projectWindow = WindowManager.getInstance().getFrame(project)
        var focusedWindow: Container? = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
        while (focusedWindow != null && projectWindow !== focusedWindow) {
          focusedWindow = focusedWindow.parent
        }
        if (focusedWindow == null) return
      }

      val groupId = notification.groupId
      val type = NotificationsConfigurationImpl.getSettings(groupId).displayType
      if (type == NotificationDisplayType.NONE) return

      val components = getComponentsToAnnounce(notification)
      announceComponents(components, notification, mode)
    }

    private fun announceComponents(components: List<String>, notification: Notification, mode: Int) {
      val text = StringUtil.join(components, ". ")
      if (LOG.isDebugEnabled) LOG.debug("Notification will be announced with mode=$mode: $text")

      AccessibleAnnouncerUtil.announce(null, text, mode != 1)
    }


    private fun getComponentsToAnnounce(notification: Notification): List<String> {
      val components: MutableList<String> = ArrayList()
      components.add(IdeBundle.message("notification.accessible.announce.prefix"))
      if (notification.title.isNotEmpty()) {
        components.add(notification.title)
      }
      val subtitle = notification.subtitle
      if (!subtitle.isNullOrEmpty()) {
        components.add(subtitle)
      }
      if (notification.content.isNotEmpty()) {
        components.add(StringUtil.removeHtmlTags(notification.content))
      }
      return components
    }
  }

  class MyListener: Notifications {
    val project: Project?

    constructor(project: Project?) {
      this.project = project
    }

    constructor():this(null)

    override fun notify(notification: Notification) {
      doNotify(notification, project)
    }
  }
}