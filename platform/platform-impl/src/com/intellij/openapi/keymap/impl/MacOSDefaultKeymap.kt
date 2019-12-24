// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.keymap.impl

import com.intellij.configurationStore.SchemeDataHolder
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.MouseShortcut
import com.intellij.openapi.actionSystem.Shortcut
import com.intellij.openapi.extensions.PluginDescriptor
import org.intellij.lang.annotations.JdkConstants
import java.awt.event.InputEvent
import javax.swing.KeyStroke

class MacOSDefaultKeymap(dataHolder: SchemeDataHolder<KeymapImpl>,
                         defaultKeymapManager: DefaultKeymap,
                         plugin: PluginDescriptor)
  : DefaultKeymapImpl(dataHolder, defaultKeymapManager, plugin) {
  companion object {
    @JvmStatic
    fun convertShortcutFromParent(shortcut: Shortcut): Shortcut {
      if (shortcut is MouseShortcut) {
        return _convertMouseShortcut(shortcut)
      }
      if (shortcut is KeyboardShortcut) {
        return KeyboardShortcut(_convertKeyStroke(shortcut.firstKeyStroke), shortcut.secondKeyStroke?.let(::_convertKeyStroke))
      }
      return shortcut
    }
  }

  override fun convertKeyStroke(keyStroke: KeyStroke): KeyStroke = _convertKeyStroke(keyStroke)

  override fun convertMouseShortcut(shortcut: MouseShortcut): MouseShortcut = _convertMouseShortcut(shortcut)

  override fun convertShortcut(shortcut: Shortcut): Shortcut = convertShortcutFromParent(shortcut)
}

private fun _convertKeyStroke(parentKeyStroke: KeyStroke): KeyStroke = KeyStroke.getKeyStroke(parentKeyStroke.keyCode, mapModifiers(parentKeyStroke.modifiers), parentKeyStroke.isOnKeyRelease)

private fun _convertMouseShortcut(shortcut: MouseShortcut) = MouseShortcut(shortcut.button, mapModifiers(shortcut.modifiers), shortcut.clickCount)

@JdkConstants.InputEventMask
private fun mapModifiers(@JdkConstants.InputEventMask inModifiers: Int): Int {
  var modifiers = inModifiers
  var meta = false

  if (modifiers and InputEvent.META_MASK != 0) {
    modifiers = modifiers and InputEvent.META_MASK.inv()
    meta = true
  }

  var metaDown = false
  if (modifiers and InputEvent.META_DOWN_MASK != 0) {
    modifiers = modifiers and InputEvent.META_DOWN_MASK.inv()
    metaDown = true
  }

  var control = false
  if (modifiers and InputEvent.CTRL_MASK != 0) {
    modifiers = modifiers and InputEvent.CTRL_MASK.inv()
    control = true
  }

  var controlDown = false
  if (modifiers and InputEvent.CTRL_DOWN_MASK != 0) {
    modifiers = modifiers and InputEvent.CTRL_DOWN_MASK.inv()
    controlDown = true
  }

  if (meta) {
    modifiers = modifiers or InputEvent.CTRL_MASK
  }

  if (metaDown) {
    modifiers = modifiers or InputEvent.CTRL_DOWN_MASK
  }

  if (control) {
    modifiers = modifiers or InputEvent.META_MASK
  }

  if (controlDown) {
    modifiers = modifiers or InputEvent.META_DOWN_MASK
  }

  return modifiers
}
