// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.MacKeymapUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.SystemInfo
import org.jetbrains.annotations.NonNls
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

object KeymapUtil {

  /**
   * @param actionId
   * *
   * @return null if actionId is null
   */
  fun getShortcutByActionId(actionId: String?): KeyStroke? {
    actionId ?: return null

    val activeKeymap = KeymapManager.getInstance().activeKeymap
    findCustomShortcut(activeKeymap, actionId)?.let {
      return it.firstKeyStroke
    }
    val shortcuts = activeKeymap.getShortcuts(actionId)
    val bestShortcut: KeyboardShortcut? = shortcuts.filterIsInstance<KeyboardShortcut>().let { kbShortcuts ->
      kbShortcuts.find { !it.isNumpadKey } ?: kbShortcuts.firstOrNull()
    }
    return bestShortcut?.firstKeyStroke
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

  @NlsSafe
  fun getKeyStrokeData(keyStroke: KeyStroke?): Pair<String, List<IntRange>> {
    if (keyStroke == null) return Pair("", emptyList())
    val modifiers = getModifiersText(keyStroke.modifiers)
    val keyCode = keyStroke.keyCode
    var key = specificKeyString(keyCode)
              ?: if (SystemInfo.isMac) MacKeymapUtil.getKeyText(keyCode) else KeyEvent.getKeyText(keyCode)

    if (key.contains(' ')) {
      key = key.replace(' ', '\u00A0')
    }

    if (key.length == 1) getStringForMacSymbol(key[0])?.let {
      key = key + "\u00A0" + it
    }

    val separator = "\u00A0\u00A0\u00A0\u00A0"
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
        if (modifierName != null) m + "\u00A0" + modifierName else m
      }
      else m

      addPart(part)
      builder.append(separator)
    }

    addPart(key)
    return Pair(builder.toString(), intervals)
  }

  fun getGotoActionData(@NonNls actionId: String): Pair<String, List<IntRange>> {
    val keyStroke = getShortcutByActionId("GotoAction")
    val gotoAction = getKeyStrokeData(keyStroke)
    val actionName = getActionById(actionId).templatePresentation.text.replaceSpacesWithNonBreakSpace()
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
