// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.text

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
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
  val SHORTCUT_PART_SEPARATOR = NON_BREAK_SPACE.repeat(3)

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

  fun getKeyboardShortcutData(shortcut: KeyboardShortcut?): Pair<@NlsSafe String, List<IntRange>> {
    if (shortcut == null) return Pair("", emptyList())
    val firstKeyStrokeData = getKeyStrokeData(shortcut.firstKeyStroke)
    val secondKeyStroke = shortcut.secondKeyStroke ?: return firstKeyStrokeData
    val secondKeyStrokeData = getKeyStrokeData(secondKeyStroke)
    val firstPartString = firstKeyStrokeData.first + "$SHORTCUT_PART_SEPARATOR,$SHORTCUT_PART_SEPARATOR"
    val firstPartLength = firstPartString.length

    val shiftedList = secondKeyStrokeData.second.map { IntRange(it.first + firstPartLength, it.last + firstPartLength) }

    return (firstPartString + secondKeyStrokeData.first) to (firstKeyStrokeData.second + shiftedList)
  }

  fun getKeyStrokeData(keyStroke: KeyStroke?): Pair<@NlsSafe String, List<IntRange>> {
    if (keyStroke == null) return Pair("", emptyList())
    val modifiers: List<String> = getModifiersText(keyStroke.modifiers)
    val keyString = getKeyString(keyStroke.keyCode)

    val intervals = mutableListOf<IntRange>()
    val builder = StringBuilder()

    fun addPart(part: String) {
      val start = builder.length
      builder.append(part)
      intervals.add(IntRange(start, builder.length - 1))
    }

    for (m in modifiers) {
      addPart(m)
      builder.append(SHORTCUT_PART_SEPARATOR)
    }

    addPart(keyString)
    return Pair(builder.toString(), intervals)
  }

  /**
   * Converts raw shortcut to presentable form. The parts should be separated by plus sign.
   * Example of input: Ctrl + Shift + T
   */
  fun getRawShortcutData(shortcut: String): Pair<@NlsSafe String, List<IntRange>> {
    val parts = shortcut.split(Regex(""" *\+ *""")).map(this::getPresentableModifier)
    val builder = StringBuilder()
    val ranges = mutableListOf<IntRange>()
    var curInd = 0
    for ((ind, part) in parts.withIndex()) {
      builder.append(part.replaceSpacesWithNonBreakSpaces())
      ranges.add(curInd until builder.length)
      if (ind != parts.lastIndex) {
        builder.append(SHORTCUT_PART_SEPARATOR)
      }
      curInd = builder.length
    }
    return builder.toString() to ranges
  }

  fun getGotoActionData(@NonNls actionId: String): Pair<String, List<IntRange>> {
    val action = ActionManager.getInstance().getAction(actionId)
    val actionName = if (action != null) {
      action.templatePresentation.text.replaceSpacesWithNonBreakSpaces()
    }
    else {
      thisLogger().error("Failed to find action with id: $actionId")
      actionId
    }

    val gotoActionShortcut = getShortcutByActionId("GotoAction")
    val gotoAction = getKeyboardShortcutData(gotoActionShortcut)
    val updated = ArrayList<IntRange>(gotoAction.second)
    val start = gotoAction.first.length + 5
    updated.add(IntRange(start, start + actionName.length - 1))
    return Pair(gotoAction.first + "  →  " + actionName, updated)
  }

  private fun getModifiersText(modifiers: Int): List<String> {
    val modifiersString = if (SystemInfo.isMac) {
      // returns glyphs if native shortcuts are enabled, text presentation otherwise
      MacKeymapUtil.getModifiersText(modifiers, "+")
    }
    else KeyEvent.getKeyModifiersText(modifiers)
    return modifiersString
      .split("[ +]+".toRegex())
      .dropLastWhile { it.isEmpty() }
      .map(this::getPresentableModifier)
  }

  private fun getKeyString(code: Int) = when (code) {
    KeyEvent.VK_LEFT -> "←"
    KeyEvent.VK_RIGHT -> "→"
    KeyEvent.VK_UP -> "↑"
    KeyEvent.VK_DOWN -> "↓"
    else -> if (SystemInfo.isMac) getMacKeyString(code) else getLinuxWinKeyString(code)
  }.replaceSpacesWithNonBreakSpaces()

  private fun getLinuxWinKeyString(code: Int) = when (code) {
    KeyEvent.VK_ENTER -> "Enter"
    KeyEvent.VK_BACK_SPACE -> "Backspace"
    else -> KeyEvent.getKeyText(code)
  }

  private fun getMacKeyString(code: Int) = when (code) {
    KeyEvent.VK_ENTER -> "↩ Return"
    KeyEvent.VK_BACK_SPACE -> "⌫ Del"
    KeyEvent.VK_ESCAPE -> "⎋ Esc"
    KeyEvent.VK_TAB -> "⇥ Tab"
    KeyEvent.VK_SHIFT -> "⇧ Shift"
    else -> KeyEvent.getKeyText(code)
  }

  private fun getPresentableModifier(rawModifier: String): String {
    val modifier = if (KeymapUtil.isSimplifiedMacShortcuts()) getSimplifiedMacModifier(rawModifier) else rawModifier
    return modifier.replaceSpacesWithNonBreakSpaces()
  }

  private fun getSimplifiedMacModifier(modifier: String) = when (modifier) {
    "Ctrl" -> "⌃ Ctrl"
    "Alt" -> "⌥ Opt"
    "Shift" -> "⇧ Shift"
    "Cmd" -> "⌘ Cmd"
    else -> modifier
  }

  private fun String.replaceSpacesWithNonBreakSpaces() = this.replace(" ", NON_BREAK_SPACE)
}
