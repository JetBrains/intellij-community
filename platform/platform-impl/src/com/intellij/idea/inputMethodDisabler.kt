// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.idea

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsConfiguration
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.util.ReflectionUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.awt.Component
import java.awt.Container
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import javax.swing.SwingUtilities

private const val PERSISTENT_SETTING_MUTED_KEY = "input.method.disabler.muted"
private const val PERSISTENT_SETTING_AUTO_DISABLE_KEY = "input.method.disabler.auto"
private const val NOTIFICATION_GROUP = "Input method disabler"

private var IS_NOTIFICATION_REGISTERED = false

// TODO: consider to detect IM-freezes and then notify user (offer to disable IM)

internal suspend fun disableInputMethodsIfPossible() {
  if (!SystemInfo.isXWindow || !SystemInfo.isJetBrainsJvm) {
    return
  }

  val properties = PropertiesComponent.getInstance()
  val muted = properties.isTrueValue(PERSISTENT_SETTING_MUTED_KEY)
  if (muted) {
    val auto = properties.isTrueValue(PERSISTENT_SETTING_AUTO_DISABLE_KEY)
    if (auto) {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        disableInputMethdosImpl()
      }
    }
    return
  }

  properties.setValue(PERSISTENT_SETTING_MUTED_KEY, true)

  // TODO: improve heuristic to return probability and if
  //  prob == 0: don't notify user, don't disable
  //  prob == 1: automatically (silently) disable IM
  //  default: notify user that it would be better to disable IM (otherwise freezes possible)
  if (!canDisableInputMethod()) {
    return
  }

  // Offer to disable IM via notification

  if (!IS_NOTIFICATION_REGISTERED) {
    IS_NOTIFICATION_REGISTERED = true
    NotificationsConfiguration.getNotificationsConfiguration().register(
      NOTIFICATION_GROUP,
      NotificationDisplayType.STICKY_BALLOON,
      true)
  }

  val title = IdeBundle.message("notification.title.input.method.disabler")
  val message = IdeBundle.message("notification.content.input.method.disabler")
  val notification = Notification(NOTIFICATION_GROUP, title, message, NotificationType.WARNING)
  notification.addAction(DumbAwareAction.create(IdeBundle.message("action.text.disable.input.methods")) { e: AnActionEvent? ->
    PropertiesComponent.getInstance().setValue(PERSISTENT_SETTING_AUTO_DISABLE_KEY, true)
    disableInputMethdosImpl()
    notification.expire()
  })
  notification.notify(null)
}

private fun disableInputMethdosImpl() {
  try {
    val componentClass = ReflectionUtil.forName("java.awt.Component")
    val method = ReflectionUtil.getMethod(componentClass, "disableInputMethodSupport") ?: return
    method.invoke(componentClass)

    getLogger().info("Input method disabler: disabled for any java.awt.Component.")

    val frames = WindowManagerEx.getInstanceEx().projectFrameHelpers.mapNotNull { fh -> SwingUtilities.getRoot(fh.frame) }

    ApplicationManager.getApplication().executeOnPooledThread {
      val startMs = System.currentTimeMillis()
      for (frameRoot in frames) {
        freeIMRecursively(frameRoot)
      }
      getLogger().info("Input method disabler: resources of input methods were released, spent " + (System.currentTimeMillis() - startMs) + " ms.")
    }
  }
  catch (e: Throwable) {
    getLogger().warn(e)
  }
}

private fun getLogger() = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

// releases resources of input-methods support
private fun freeIMRecursively(c: Component) {
  val ic = c.getInputContext() // thread-safe
  if (ic != null)
    ic.removeNotify(c) // thread-safe

  if (c !is Container) {
    return
  }
  for (k in c.components) {
    freeIMRecursively(k)
  }
}

@Suppress("SpellCheckingInspection")
private fun canDisableInputMethod(): Boolean {
  val gdmSession = System.getenv("GDMSESSION") ?: ""
  val xdgDesktop = System.getenv("XDG_CURRENT_DESKTOP")?.toLowerCase(Locale.ENGLISH) ?: ""
  val isGTKDesktop = gdmSession.startsWith("gnome")
                     || gdmSession.startsWith("ubuntu")
                     || xdgDesktop.startsWith("unity")
                     || xdgDesktop.startsWith("ubuntu")
                     || xdgDesktop.startsWith("gnome")
  if (!isGTKDesktop) {
    getLogger().info("Input method disabler: not gtk desktop: '$gdmSession' | '$xdgDesktop'")
    return false
  }

  val startMs = System.currentTimeMillis()
  val layoutId2type = HashMap<String, String>()

  var line = ""
  try {
    @Suppress("SpellCheckingInspection")
    val cmd = "gsettings get org.gnome.desktop.input-sources sources"
    val run = Runtime.getRuntime()
    val pr = run.exec(cmd)
    pr.waitFor()

    val buf = BufferedReader(InputStreamReader(pr.inputStream))
    for (line in buf.lineSequence()) {
      //[('xkb', 'us'), ('xkb', 'ru'), ('ibus', 'bopomofo')]
      val parser = OutputParser(line)
      var first = true
      while (true) {
        val type = parser.extractString(if (first) "('" else "'", "'")
        if (type == null) {
          if (first) { // error (or empty output)
            getLogger().warn("Input method disabler: can't parse gsettings line: $line")
            return false
          }
          break
        }
        first = false
        val layoutId = parser.extractString("'", "'")
        if (layoutId == null) { // error (must be presented)
          getLogger().warn("Input method disabler: can't parse gsettings line: $line")
          return false
        }
        layoutId2type[layoutId] = type
      }
    }
  }
  catch (e: Throwable) {
    getLogger().warn("Input method disabler: error during parsing gsettings line: $line", e)
  }

  val endMs = System.currentTimeMillis()
  var canDisable = !layoutId2type.isEmpty() && !layoutId2type.values.contains("ibus")
  if (canDisable) {
    var supportedLayouts = setOf("am", "ara", "by", "jp", "kg", "kr", "la", "mk", "np", "ru", "th", "us") // list of default gnome layouts without dead-keys
    for (key in layoutId2type.keys) {
      if (!supportedLayouts.contains(key)) {
        canDisable = false
        break
      }
    }
  }

  val logInfo = StringBuilder("Input method disabler: canDisableInputMethod spent ").append(endMs - startMs).append(" ms, found keyboard layouts: [")
  for ((key, value) in layoutId2type) {
    logInfo.append('(')
    logInfo.append(key)
    logInfo.append(", ")
    logInfo.append(value)
    logInfo.append("), ")
  }
  logInfo.append("], result==").append(canDisable)
  getLogger().info(logInfo.toString())

  return canDisable
}

private class OutputParser(private val string: String) {
  private var position = 0

  fun extractString(beginMarker: String, endMarker: String): String? {
    var beginPos = string.indexOf(beginMarker, position)
    if (beginPos == -1) {
      return null
    }
    beginPos += beginMarker.length
    val endPos = string.indexOf(endMarker, beginPos + 1)
    if (endPos == -1) {
      return null
    }

    position = endPos + endMarker.length
    return string.substring(beginPos, endPos)
  }
}
