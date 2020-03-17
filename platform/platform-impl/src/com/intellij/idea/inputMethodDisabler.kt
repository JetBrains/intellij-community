// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.idea

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.ex.WindowManagerEx
import com.intellij.util.ReflectionUtil
import java.awt.Component
import java.awt.Container
import java.awt.EventQueue
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.*
import javax.swing.SwingUtilities

internal fun disableInputMethodsIfPossible() {
  if (!canDisableInputMethod() || !Registry.`is`("auto.disable.input.methods")) {
    return
  }

  EventQueue.invokeLater {
    try {
      val componentClass = ReflectionUtil.forName("java.awt.Component")
      val method = ReflectionUtil.getMethod(componentClass, "disableInputMethodSupport") ?: return@invokeLater
      method.invoke(componentClass)

      for (frameHelper in WindowManagerEx.getInstanceEx().projectFrameHelpers) {
        freeIMRecursively(SwingUtilities.getRoot(frameHelper.frame))
      }
      Logger.getInstance(Main::class.java).info("InputMethods was disabled")
    }
    catch (e: Throwable) {
      getLogger().warn(e)
    }
  }
}

private fun getLogger() = Logger.getInstance("#com.intellij.idea.ApplicationLoader")

private fun freeIMRecursively(c: Component) {
  val ic = c.getInputContext()
  if (ic != null)
    ic.removeNotify(c);

  if (c !is Container) {
    return
  }
  for (k in c.components) {
    freeIMRecursively(k)
  }
}

@Suppress("SpellCheckingInspection")
private fun canDisableInputMethod(): Boolean {
  if (!SystemInfo.isXWindow || !SystemInfo.isJetBrainsJvm) {
    return false
  }

  val gdmSession = System.getenv("GDMSESSION") ?: ""
  val xdgDesktop = System.getenv("XDG_CURRENT_DESKTOP")?.toLowerCase(Locale.ENGLISH) ?: ""
  val isGTKDesktop = gdmSession.startsWith("gnome")
                     || gdmSession.startsWith("ubuntu")
                     || xdgDesktop.startsWith("unity")
                     || xdgDesktop.startsWith("ubuntu")
                     || xdgDesktop.startsWith("gnome")
  if (!isGTKDesktop) {
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
            Logger.getInstance(Main::class.java).warn("can't parse gsettings line: $line")
            return false
          }
          break
        }
        first = false
        val layoutId = parser.extractString("'", "'")
        if (layoutId == null) { // error (must be presented)
          Logger.getInstance(Main::class.java).warn("can't parse gsettings line: $line")
          return false
        }
        layoutId2type[layoutId] = type
      }
    }
  }
  catch (e: Throwable) {
    getLogger().warn("error during parsing gsettings line: $line", e)
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

  val logInfo = StringBuilder("canDisableInputMethod spent ").append(endMs - startMs).append(" ms, found keyboard layouts: [")
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