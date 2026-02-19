// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.actionSystem.impl

import com.intellij.diagnostic.StartUpMeasurer
import com.intellij.idea.AppMode
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.impl.Utils.getTracer
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.ui.popup.AbstractPopup
import com.intellij.util.TimeoutUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.update.UiNotifyConnector
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Window
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.concurrent.TimeUnit

@ApiStatus.Internal
object PopupShowingTimeTracker {
  private val MARKER_KEY = Key.create<Boolean>("PopupShowingTimeMarker")

  @JvmStatic
  fun showElapsedMillisIfConfigured(startNanos: Long, component: Component) {
    if (startNanos <= 0 || !Registry.`is`("ide.diagnostics.show.context.menu.invocation.time") || AppMode.isRemoteDevHost()) {
      return
    }

    UiNotifyConnector.doWhenFirstShown(component, isDeferred = false) {
      UIUtil.getWindow(component)?.addWindowListener(object : WindowAdapter() {
        override fun windowOpened(e: WindowEvent) {
          val time = TimeoutUtil.getDurationMillis(startNanos)

          System.getProperty("perf.test.popup.name")?.let { popupName ->
            val startTimeUnixNano = startNanos + StartUpMeasurer.getStartTimeUnixNanoDiff()
            getTracer(false).spanBuilder("popupShown#$popupName")
              .setStartTimestamp(startTimeUnixNano, TimeUnit.NANOSECONDS)
              .startSpan()
              .end(startTimeUnixNano + TimeUnit.MILLISECONDS.toNanos(time), TimeUnit.NANOSECONDS)
          }

          e.window.removeWindowListener(this)
          @Suppress("DEPRECATION", "removal", "HardCodedStringLiteral")
          Notification(Notifications.SYSTEM_MESSAGES_GROUP_ID, "Popup invocation took $time ms",
                       NotificationType.INFORMATION).notify(null)
        }
      })
    }
  }

  fun showElapsedMillisIfConfigured(startNanos: Long, popup: JBPopup) {
    if (!Registry.`is`("ide.diagnostics.show.context.menu.invocation.time")) {
      return
    }

    if (AppMode.isRemoteDevHost()) {
      (popup as? AbstractPopup)?.putUserData(MARKER_KEY, true)
      return
    }
    showElapsedMillisIfConfigured(startNanos, popup.content)
  }

  fun isTrackedPopup(popup: JBPopup): Boolean {
    return (popup as? AbstractPopup)?.getUserData(MARKER_KEY) ?: false
  }

  fun isTrackedPopupWindow(window: Window): Boolean {
    val popup = PopupUtil.getPopupFor(window)
    return popup != null && isTrackedPopup(popup)
  }
}