// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package com.intellij.openapi.wm.impl

import com.intellij.diagnostic.LoadingState
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.util.SystemInfoRt
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.wm.RegisterToolWindowTaskData
import com.intellij.openapi.wm.ToolWindowId
import com.intellij.openapi.wm.WindowInfo
import com.intellij.toolWindow.InternalDecoratorImpl
import com.intellij.ui.ClientProperty
import com.intellij.openapi.ui.popup.Balloon
import org.intellij.lang.annotations.MagicConstant
import java.awt.Component
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.SwingUtilities
import javax.swing.event.HyperlinkEvent
import javax.swing.event.HyperlinkListener

internal enum class KeyState {
  WAITING, PRESSED, RELEASED, HOLD
}

internal fun areAllModifiersPressed(
  @MagicConstant(flagsFromClass = InputEvent::class) modifiers: Int,
  @MagicConstant(flagsFromClass = InputEvent::class) mask: Int,
): Boolean {
  return (modifiers xor mask) == 0
}

@MagicConstant(flagsFromClass = InputEvent::class)
@Suppress("DEPRECATION")
internal fun keyCodeToInputMask(code: Int): Int {
  return when (code) {
    KeyEvent.VK_SHIFT -> InputEvent.SHIFT_MASK
    KeyEvent.VK_CONTROL -> InputEvent.CTRL_MASK
    KeyEvent.VK_META -> InputEvent.META_MASK
    KeyEvent.VK_ALT -> InputEvent.ALT_MASK
    else -> 0
  }
}

// We should filter out 'mixed' mask like InputEvent.META_MASK | InputEvent.META_DOWN_MASK
@MagicConstant(flagsFromClass = InputEvent::class)
internal fun getActivateToolWindowVKsMask(): Int {
  if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
    return 0
  }

  if (Registry.`is`("toolwindow.disable.overlay.by.double.key")) {
    return 0
  }

  val baseShortcut = KeymapManager.getInstance().activeKeymap.getShortcuts("ActivateProjectToolWindow")

  @Suppress("DEPRECATION")
  val defaultModifiers = if (SystemInfoRt.isMac) InputEvent.META_MASK else InputEvent.ALT_MASK
  var baseModifiers = 0
  for (each in baseShortcut) {
    if (each is KeyboardShortcut) {
      val keyStroke = each.firstKeyStroke
      baseModifiers = keyStroke.modifiers
      if (baseModifiers > 0) {
        break
      }
    }
  }
  // We should filter out 'mixed' mask like InputEvent.META_MASK | InputEvent.META_DOWN_MASK
  @Suppress("DEPRECATION")
  baseModifiers = baseModifiers and (InputEvent.SHIFT_MASK or InputEvent.CTRL_MASK or InputEvent.META_MASK or InputEvent.ALT_MASK)

  // If the keymap either doesn't define an ActivateProjectToolWindow shortcut
  // or defines it with multiple modifiers, fall back to the default behavior.
  if (baseModifiers.countOneBits() == 1) {
    return baseModifiers
  }
  else {
    return defaultModifiers
  }
}

internal val isStackEnabled: Boolean
  get() = Registry.`is`("ide.enable.toolwindow.stack")

internal fun getToolWindowIdForComponent(component: Component?): String? {
  var c = component
  while (c != null) {
    if (c is InternalDecoratorImpl) {
      return c.toolWindow.id
    }
    c = ClientProperty.get(c, ToolWindowManagerImpl.PARENT_COMPONENT) ?: c.parent
  }
  return null
}

internal class BalloonHyperlinkListener(private val listener: HyperlinkListener?) : HyperlinkListener {
  var balloon: Balloon? = null

  override fun hyperlinkUpdate(e: HyperlinkEvent) {
    val balloon = balloon
    if (balloon != null && e.eventType == HyperlinkEvent.EventType.ACTIVATED) {
      SwingUtilities.invokeLater { balloon.hide() }
    }
    listener?.hyperlinkUpdate(e)
  }
}

internal fun windowInfoChanges(oldInfo: WindowInfo, newInfo: WindowInfo): String {
  if (oldInfo !is WindowInfoImpl || newInfo !is WindowInfoImpl) {
    return "Logging of non-standard WindowInfo implementations is not supported"
  }
  val sb = StringBuilder("Changes:")
  for ((index, newProperty) in newInfo.__getProperties().withIndex()) {
    val oldProperty = oldInfo.__getProperties().getOrNull(index)
    val name = newProperty.name
    if (oldProperty == null || oldProperty.name != name) {
      return "Old and new window info don't have the same property set: old=$oldInfo, new=$newInfo"
    }
    if (newProperty != oldProperty) {
      sb.append(' ').append(oldProperty).append(" -> ").append(newProperty)
    }
  }
  return sb.toString()
}

internal fun isToolwindowOfBundledPlugin(task: RegisterToolWindowTaskData): Boolean {
  // platform toolwindow but registered dynamically
  if (ToolWindowId.BUILD_DEPENDENCIES == task.id) {
    return true
  }

  task.pluginDescriptor?.let {
    return it.isBundled
  }

  // check content factory, Service View, and Endpoints View goes here
  val pluginDescriptor = PluginManager.getPluginByClass(task.contentFactory?.javaClass ?: return false)
  return pluginDescriptor == null || pluginDescriptor.isBundled
}
