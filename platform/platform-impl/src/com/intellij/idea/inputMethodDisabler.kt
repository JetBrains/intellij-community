// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment")

package com.intellij.idea

import com.intellij.ide.IdeBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.notification.Notification
import com.intellij.notification.NotificationDisplayType
import com.intellij.notification.NotificationType
import com.intellij.notification.NotificationsConfiguration
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.util.ReflectionUtil
import com.intellij.util.io.awaitExit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Container
import java.util.*
import javax.swing.SwingUtilities

private const val PERSISTENT_SETTING_MUTED_KEY = "input.method.disabler.muted"
private const val PERSISTENT_SETTING_AUTO_DISABLE_KEY = "input.method.disabler.auto"
private const val NOTIFICATION_GROUP = "Input method disabler"

private var IS_NOTIFICATION_REGISTERED = false

// TODO: consider to detect IM-freezes and then notify user (offer to disable IM)

@ApiStatus.Internal
suspend fun disableInputMethodsIfPossible() {
  if (SystemInfo.isWindows || SystemInfo.isMac || !SystemInfo.isJetBrainsJvm) {
    return
  }

  val properties = PropertiesComponent.getInstance()
  val muted = properties.isTrueValue(PERSISTENT_SETTING_MUTED_KEY)
  if (muted) {
    val auto = properties.isTrueValue(PERSISTENT_SETTING_AUTO_DISABLE_KEY)
    if (auto) {
      withContext(Dispatchers.EDT + ModalityState.any().asContextElement()) {
        disableInputMethodsImpl()
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
  notification.addAction(DumbAwareAction.create(IdeBundle.message("action.text.disable.input.methods")) {
    PropertiesComponent.getInstance().setValue(PERSISTENT_SETTING_AUTO_DISABLE_KEY, true)
    disableInputMethodsImpl()
    notification.expire()
  })
  notification.notify(null)
}

private fun disableInputMethodsImpl() {
  try {
    val componentClass = ReflectionUtil.forName("java.awt.Component")
    val method = ReflectionUtil.getMethod(componentClass, "disableInputMethodSupport") ?: return
    method.invoke(componentClass)

    LOG.info("Input method disabler: disabled for any java.awt.Component.")

    val frames = WindowManagerEx.getInstanceEx().projectFrameHelpers.mapNotNull { fh -> SwingUtilities.getRoot(fh.frame) }

    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      val startMs = System.currentTimeMillis()
      for (frameRoot in frames) {
        freeIMRecursively(frameRoot)
      }
      LOG.info("Input method disabler: resources of input methods were released, spent ${System.currentTimeMillis() - startMs} ms.")
    }
  }
  catch (e: Throwable) {
    LOG.warn(e)
  }
}

@Suppress("SSBasedInspection")
private val LOG: Logger
  get() = Logger.getInstance("#com.intellij.platform.ide.bootstrap.ApplicationLoader")

// releases resources of input-methods support
private fun freeIMRecursively(c: Component) {
  c.inputContext?.removeNotify(c) // thread-safe

  if (c !is Container) {
    return
  }

  for (k in c.components) {
    freeIMRecursively(k)
  }
}

@Suppress("SpellCheckingInspection")
private suspend fun canDisableInputMethod(): Boolean {
  val gdmSession = System.getenv("GDMSESSION") ?: ""
  val xdgDesktop = System.getenv("XDG_CURRENT_DESKTOP")?.lowercase(Locale.ENGLISH) ?: ""
  val isGTKDesktop = gdmSession.startsWith("gnome")
                     || gdmSession.startsWith("ubuntu")
                     || xdgDesktop.startsWith("unity")
                     || xdgDesktop.startsWith("ubuntu")
                     || xdgDesktop.startsWith("gnome")
  if (!isGTKDesktop) {
    LOG.info("Input method disabler: not gtk desktop: '$gdmSession' | '$xdgDesktop'")
    return false
  }

  val startMs = System.currentTimeMillis()
  val layoutId2type = HashMap<String, String>()

  if (!withContext(Dispatchers.IO) { processInputSources(layoutId2type) }) {
    return false
  }

  val endMs = System.currentTimeMillis()
  var canDisable = !layoutId2type.isEmpty() && !layoutId2type.values.contains("ibus")
  if (canDisable) {
    // list of default gnome layouts without dead-keys
    val supportedLayouts = hashSetOf("am", "ara", "by", "jp", "kg", "kr", "la", "mk", "np", "ru", "th", "us")
    for (key in layoutId2type.keys) {
      if (!supportedLayouts.contains(key)) {
        canDisable = false
        break
      }
    }
  }

  val logInfo = StringBuilder("Input method disabler: canDisableInputMethod spent ")
    .append(endMs - startMs)
    .append(" ms, found keyboard layouts: [")
  for ((key, value) in layoutId2type) {
    logInfo.append('(')
    logInfo.append(key)
    logInfo.append(", ")
    logInfo.append(value)
    logInfo.append("), ")
  }
  logInfo.append("], result==").append(canDisable)
  LOG.info(logInfo.toString())

  return canDisable
}

private suspend fun processInputSources(layoutId2type: MutableMap<String, String>): Boolean {
  @Suppress("SpellCheckingInspection")
  val process = ProcessBuilder("gsettings", "get", "org.gnome.desktop.input-sources", "sources").start()
  process.awaitExit()

  var lastLine = ""
  val reader = process.inputStream.bufferedReader()
  try {
    for (line in reader.lineSequence()) {
      lastLine = line
      //[('xkb', 'us'), ('xkb', 'ru'), ('ibus', 'bopomofo')]
      val parser = OutputParser(line)
      var first = true
      while (true) {
        val type = parser.extractString(if (first) "('" else "'", "'")
        if (type == null) {
          if (first) { // error (or empty output)
            LOG.warn("Input method disabler: can't parse gsettings line: $line")
            return false
          }
          break
        }
        first = false
        val layoutId = parser.extractString("'", "'")
        if (layoutId == null) {
          // error (must be presented)
          LOG.warn("Input method disabler: can't parse gsettings line: $line")
          return false
        }

        layoutId2type.put(layoutId, type)
      }
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    LOG.warn("Input method disabler: error during parsing gsettings line: $lastLine", e)
  }
  return true
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
