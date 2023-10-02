// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

/**
 * @author nik
 */
package com.intellij.platform.ide.impl.presentationAssistant

import java.awt.event.InputEvent
import java.awt.event.KeyEvent

//repeats logic from com.intellij.openapi.keymap.KeymapUtil.getKeyText but doesn't use Mac presentation on Mac
fun getWinKeyText(code: Int): String {
  when (code) {
    KeyEvent.VK_BACK_QUOTE -> return "`"
    KeyEvent.VK_SEPARATOR -> return ","
    KeyEvent.VK_DECIMAL -> return "."
    KeyEvent.VK_SLASH -> return "/"
    KeyEvent.VK_BACK_SLASH -> return "\\"
    KeyEvent.VK_PERIOD -> return "."
    KeyEvent.VK_SEMICOLON -> return ";"
    KeyEvent.VK_CLOSE_BRACKET -> return "]"
    KeyEvent.VK_OPEN_BRACKET -> return "["
    KeyEvent.VK_EQUALS -> return "="
  }
  return KeyEvent.getKeyText(code)
}

//repeats logic from KeyEvent.getKeyModifiersText but doesn't use Mac presentation on Mac
fun getWinModifiersText(modifiers: Int): String =
  modifiersPresentableText.filter { (modifiers and it.first) != 0 }.joinToString("+") { it.second }

private val modifiersPresentableText = listOf(
  InputEvent.META_DOWN_MASK to "Meta",
  InputEvent.CTRL_DOWN_MASK to "Ctrl",
  InputEvent.ALT_DOWN_MASK to "Alt",
  InputEvent.SHIFT_DOWN_MASK to "Shift",
  InputEvent.ALT_GRAPH_DOWN_MASK to "Alt Graph",
  InputEvent.BUTTON1_DOWN_MASK to "Button1"
)