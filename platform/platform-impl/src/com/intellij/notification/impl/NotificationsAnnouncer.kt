// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.ide.IdeBundle
import com.intellij.notification.*
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.debug
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.openapi.wm.impl.IdeRootPane
import com.intellij.util.ui.EDT
import com.intellij.util.ui.EdtInvocationManager
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import org.jetbrains.annotations.ApiStatus
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.lang.ref.WeakReference
import javax.accessibility.Accessible
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel

private val mode: NotificationAnnouncingMode
  get() = NotificationsConfiguration.getNotificationsConfiguration().notificationAnnouncingMode

@Service
private class NotificationsAnnouncerService {
  private val callerCache = mutableListOf<FrameWithAccessible>()

  fun findCaller(frame: JFrame): Accessible? {
    (frame.rootPane as? IdeRootPane)?.let { return it }

    var caller = callerCache.firstOrNull { it.isValid && it.frame === frame }?.accessible
    if (caller == null){
      callerCache.removeAll { !it.isValid }
      caller = UIUtil.uiTraverser(frame).firstOrNull {
        it is Accessible && it.isVisible && it is JLabel
      }?.let {
        it as Accessible
      }?.also {
        callerCache.add(FrameWithAccessible(frame, it))
      }
    }

    return caller
  }
}

@ApiStatus.Experimental
internal fun isNotificationAnnouncerEnabled(): Boolean {
  return AccessibleAnnouncerUtil.isAnnouncingAvailable() && mode != NotificationAnnouncingMode.NONE
}


internal val isNotificationAnnouncerFeatureAvailable: Boolean
  get() = Registry.`is`("ide.accessibility.announcing.notifications.available", false)

private fun doNotify(notification: Notification, project: Project?) {
  if (!isNotificationAnnouncerEnabled()) {
    return
  }

  EDT.assertIsEdt()

  val windowManager = WindowManager.getInstance()
  val frame = (if (project == null) windowManager.findVisibleFrame() else windowManager.getFrame(project)) ?: return

  var focusedFrame: Container? = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
  while (focusedFrame != null && frame !== focusedFrame) {
    focusedFrame = focusedFrame.parent
  }
  if (focusedFrame == null) {
    return
  }

  if (NotificationsConfigurationImpl.getSettings(notification.groupId).displayType == NotificationDisplayType.NONE) {
    return
  }

  val caller = service<NotificationsAnnouncerService>().findCaller(frame) ?: return
  announceComponents(components = getComponentsToAnnounce(notification), caller = caller, mode = mode)
}

private fun announceComponents(components: List<String>, caller: Accessible, mode: NotificationAnnouncingMode) {
  val text = components.joinToString(separator = ". ")
  logger<NotificationsAnnouncerService>().debug {
    "Notification will be announced with mode=$mode, from caller=$caller, text=$text"
  }

  AccessibleAnnouncerUtil.announce(caller, text, mode == NotificationAnnouncingMode.HIGH)
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

private class NotificationsAnnouncerListener : Notifications {
  private val project: Project?

  constructor(project: Project?) {
    this.project = project
  }

  @Suppress("unused")
  constructor() : this(null)

  override fun notify(notification: Notification) {
    EdtInvocationManager.invokeAndWaitIfNeeded {
      doNotify(notification, project)
    }
  }
}

private class FrameWithAccessible(frame: JFrame, accessible: Accessible) {
  private val frameWeak = WeakReference(frame)
  private val accessibleWeak = WeakReference(accessible)
  val frame: JFrame?
    get() = frameWeak.get()
  val accessible: Accessible?
    get() = accessibleWeak.get()
  val isValid: Boolean
    get() = frame != null && accessible?.let { it is JComponent && it.isVisible && it.isValid } == true
}