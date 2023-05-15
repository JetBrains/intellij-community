// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.notification.impl

import com.intellij.ide.IdeBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.Notifications
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.wm.WindowManager
import com.intellij.util.ui.EDT
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleAnnouncerUtil
import com.intellij.util.ui.accessibility.ScreenReader
import com.jetbrains.JBR
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Container
import java.awt.KeyboardFocusManager
import java.lang.ref.WeakReference
import javax.accessibility.Accessible
import javax.swing.JComponent
import javax.swing.JFrame
import javax.swing.JLabel

@Internal
object NotificationsAnnouncer {
  private val LOG = Logger.getInstance(this::class.java)
  private val mode: Int get() = Registry.intValue("ide.accessibility.announcing.notifications.mode")

  @ApiStatus.Experimental
  @JvmStatic
  fun isEnabled(): Boolean {
    return ScreenReader.isActive() && mode != 0 && JBR.isAccessibleAnnouncerSupported()
  }

  private val callersCache = mutableListOf<FrameWithAccessible>()

  private fun doNotify(notification: Notification, project: Project?) {
    if (!isEnabled()) return
    EDT.assertIsEdt()

    val frame = WindowManager.getInstance().getFrame(project) ?: return
    var focusedFrame: Container? = KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow
    while (focusedFrame != null && frame !== focusedFrame) {
      focusedFrame = focusedFrame.parent
    }
    if (focusedFrame == null) return

    val groupId = notification.groupId
    val type = NotificationsConfigurationImpl.getSettings(groupId).displayType
    if (type == NotificationDisplayType.NONE) return

    val caller = findCaller(frame) ?: return
    val components = getComponentsToAnnounce(notification)
    announceComponents(components, caller, mode)
  }

  private fun findCaller(frame: JFrame): Accessible? {
    var caller = callersCache.firstOrNull { it.isValid && it.frame === frame }?.accessible
    if (caller == null){
      callersCache.removeAll { !it.isValid }
      caller = UIUtil.uiTraverser(frame).firstOrNull {
        it is Accessible && it.isVisible && it is JLabel
      }?.let {
        it as Accessible
      }?.also {
        callersCache.add(FrameWithAccessible(frame, it))
      }
    }

    return caller
  }

  private fun announceComponents(components: List<String>, caller: Accessible, mode: Int) {
    val text = StringUtil.join(components, ". ")
    if (LOG.isDebugEnabled) LOG.debug("Notification will be announced with mode=$mode, from caller=$caller, text=$text")

    AccessibleAnnouncerUtil.announce(caller, text, mode != 1)
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

  class MyListener: Notifications {
    val project: Project?

    constructor(project: Project?) {
      this.project = project
    }

    constructor():this(null)

    override fun notify(notification: Notification) {
      UIUtil.invokeLaterIfNeeded {
        doNotify(notification, project)
      }
    }
  }

  private class FrameWithAccessible(frame: JFrame, accessible: Accessible) {
    private val frameWeak: WeakReference<JFrame> = WeakReference(frame)
    private val accessibleWeak: WeakReference<Accessible> = WeakReference(accessible)
    val frame: JFrame? get() = frameWeak.get()
    val accessible: Accessible? get() = accessibleWeak.get()
    val isValid: Boolean get() = frame != null &&
                                 accessible?.let { it is JComponent && it.isVisible } == true
  }
}