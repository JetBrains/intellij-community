// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.text.StringUtil.NON_BREAK_SPACE
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

@ApiStatus.Experimental
@ApiStatus.Internal
object ShortcutsRenderingUtil {

  /**
   * @param actionId
   * *
   * @return null if actionId is null
   */
  fun getShortcutByActionId(actionId: String?): KeyboardShortcut? {
    actionId ?: return null

    val activeKeymap = KeymapManager.getInstance().activeKeymap
    findCustomShortcut(activeKeymap, actionId)?.let {
      return it
    }
    val shortcuts = activeKeymap.getShortcuts(actionId)
    val bestShortcut: KeyboardShortcut? = shortcuts.filterIsInstance<KeyboardShortcut>().let { kbShortcuts ->
      kbShortcuts.find { !it.isNumpadKey } ?: kbShortcuts.firstOrNull()
    }
    return bestShortcut
  }

  private fun findCustomShortcut(activeKeymap: Keymap, actionId: String): KeyboardShortcut? {
    val currentShortcuts = activeKeymap.getShortcuts(actionId).toList()
    if (!activeKeymap.canModify()) return null
    val parentShortcuts = activeKeymap.parent?.getShortcuts(actionId)?.toList() ?: return null
    val shortcuts = currentShortcuts - parentShortcuts
    if (shortcuts.isEmpty()) return null

    return shortcuts.reversed().filterIsInstance<KeyboardShortcut>().firstOrNull()
  }

  private val KeyboardShortcut.isNumpadKey: Boolean
    get() = firstKeyStroke.keyCode in KeyEvent.VK_NUMPAD0..KeyEvent.VK_DIVIDE || firstKeyStroke.keyCode == KeyEvent.VK_NUM_LOCK

  private fun specificKeyString(code: Int) = when (code) {
    KeyEvent.VK_LEFT -> "←"
    KeyEvent.VK_RIGHT -> "→"
    KeyEvent.VK_UP -> "↑"
    KeyEvent.VK_DOWN -> "↓"
    else -> null
  }

  fun getKeyboardShortcutData(shortcut: KeyboardShortcut?): Pair<@NlsSafe String, List<IntRange>> {
    if (shortcut == null) return Pair("", emptyList())
    val firstKeyStrokeData = getKeyStrokeData(shortcut.firstKeyStroke)
    val secondKeyStroke = shortcut.secondKeyStroke ?: return firstKeyStrokeData
    val secondKeyStrokeData = getKeyStrokeData(secondKeyStroke)
    val firstPartString = firstKeyStrokeData.first + "${NON_BREAK_SPACE.repeat(3)},${NON_BREAK_SPACE.repeat(3)}"
    val firstPartLength = firstPartString.length

    val shiftedList = secondKeyStrokeData.second.map { IntRange(it.first + firstPartLength, it.last + firstPartLength) }

    return (firstPartString + secondKeyStrokeData.first) to (firstKeyStrokeData.second + shiftedList)
  }

  fun getKeyStrokeData(keyStroke: KeyStroke?): Pair<@NlsSafe String, List<IntRange>> {
    if (keyStroke == null) return Pair("", emptyList())
    val modifiers = getModifiersText(keyStroke.modifiers)
    val keyCode = keyStroke.keyCode
    var key = specificKeyString(keyCode)
              ?: if (SystemInfo.isMac) MacKeymapUtil.getKeyText(keyCode) else KeyEvent.getKeyText(keyCode)

    if (key.contains(' ')) {
      key = key.replace(" ", NON_BREAK_SPACE)
    }

    if (key.length == 1) getStringForMacSymbol(key[0])?.let {
      key = key + NON_BREAK_SPACE + it
    }

    val separator = NON_BREAK_SPACE.repeat(3)
    val intervals = mutableListOf<IntRange>()
    val builder = StringBuilder()

    fun addPart(part: String) {
      val start = builder.length
      builder.append(part)
      intervals.add(IntRange(start, builder.length - 1))
    }

    for (m in modifiers.getModifiers()) {
      val part = if (SystemInfo.isMac) {
        val modifierName = if (m.length == 1) getStringForMacSymbol(m[0]) else null
        if (modifierName != null) m + NON_BREAK_SPACE + modifierName else m
      }
      else m

      addPart(part)
      builder.append(separator)
    }

    addPart(key)
    return Pair(builder.toString(), intervals)
  }

  /**
   * Converts raw shortcut to presentable form. The parts should be separated by plus sign.
   * Example of input: Ctrl + Shift + T
   */
  fun getRawShortcutData(shortcut: String): Pair<@NlsSafe String, List<IntRange>> {
    val parts = shortcut.split(Regex(""" *\+ *"""))
    val separator = NON_BREAK_SPACE.repeat(3)
    val builder = StringBuilder()
    val ranges = mutableListOf<IntRange>()
    var curInd = 0
    for ((ind, part) in parts.withIndex()) {
      builder.append(part.replace(" ", NON_BREAK_SPACE))
      ranges.add(curInd until builder.length)
      if (ind != parts.lastIndex) {
        builder.append(separator)
      }
      curInd = builder.length
    }
    return builder.toString() to ranges
  }

  fun getGotoActionData(@NonNls actionId: String): Pair<String, List<IntRange>> {
    val gotoActionShortcut = getShortcutByActionId("GotoAction")
    val gotoAction = getKeyboardShortcutData(gotoActionShortcut)
    val actionName = ActionManager.getInstance().getAction(actionId).templatePresentation.text.replace(" ", NON_BREAK_SPACE)
    val updated = ArrayList<IntRange>(gotoAction.second)
    val start = gotoAction.first.length + 5
    updated.add(IntRange(start, start + actionName.length - 1))
    return Pair(gotoAction.first + "  →  " + actionName, updated)
  }

  private fun getModifiersText(modifiers: Int): String {
    return KeyEvent.getKeyModifiersText(modifiers)
  }

  private fun String.getModifiers(): Array<String> = this.split("[ +]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

  private fun getStringForMacSymbol(c: Char): String? {
    if (!SystemInfo.isMac) return null
    when (c) {
      '\u238B' -> return "Esc"
      '\u21E5' -> return "Tab"
      '\u21EA' -> return "Caps"
      '\u21E7' -> return "Shift"
      '\u2303' -> return "Ctrl"
      '\u2325' -> return "Opt"
      '\u2318' -> return "Cmd"
      '\u23CE' -> return "Enter"
      '\u232B' -> return "Backspace"
      '\u2326' -> return "Del"
      '\u2196' -> return "Home"
      '\u2198' -> return "End"
      '\u21DE' -> return "PageUp"
      '\u21DF' -> return "PageDown"
      '\u21ED' -> return "NumLock"
      '\u2328' -> return "NumPad"
      else -> return null
    }
  }
}
