// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package training.util

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.KeyboardShortcut
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

    fun KeyboardShortcut.isConflicting(): Boolean {
      return KeymapManager.getInstance().activeKeymap.getConflicts(actionId, this).isNotEmpty()
    }

    val shortcuts = KeymapManager.getInstance().activeKeymap.getShortcuts(actionId)
    var bestShortcut: KeyboardShortcut? = null
    for (curShortcut in shortcuts) {
      if (curShortcut is KeyboardShortcut) {
        val isConflicting = curShortcut.isConflicting()
        val isNumpadKey = curShortcut.isNumpadKey
        if (bestShortcut == null || !isConflicting && !isNumpadKey
            || !isNumpadKey && bestShortcut.isNumpadKey // Prefer not numpad shortcut then not conflicting shortcut
            || !isConflicting && bestShortcut.isConflicting() && bestShortcut.isNumpadKey
        ) {
          bestShortcut = curShortcut
        }
        if (!isConflicting && !isNumpadKey) break
      }
    }
    return bestShortcut?.firstKeyStroke
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
    val key = specificKeyString(keyCode)
              ?: if (SystemInfo.isMac) MacKeymapUtil.getKeyText(keyCode) else KeyEvent.getKeyText(keyCode)

    val separator = "\u00A0\u00A0\u00A0"
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
        if (modifierName != null) m + modifierName else m
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
    val actionName = ActionManager.getInstance().getAction(actionId).templatePresentation.text.replaceSpacesWithNonBreakSpace()
    val updated = ArrayList<IntRange>(gotoAction.second)
    val start = gotoAction.first.length + 5
    updated.add(IntRange(start, start + actionName.length - 1))
    return Pair(gotoAction.first + "  →  " + actionName, updated)
  }

  private fun getModifiersText(modifiers: Int): String {
    return KeyEvent.getKeyModifiersText(modifiers)
  }

  private fun String.getModifiers(): Array<String> = this.split("[ +]".toRegex()).dropLastWhile { it.isEmpty() }.toTypedArray()

  @NlsSafe
  fun decryptMacShortcut(shortcut: String): String {
    if (shortcut.contains('→')) {
      val split = shortcut.split('→')
      if (split.size != 2) return shortcut
      return decryptMacShortcut(split[0].trim()) + " →" + split[1]
    }
    val buffer = StringBuffer()
    var shouldInsertPlus = false
    for (c in shortcut) {
      if (c.isWhitespace()) {
        shouldInsertPlus = true
      }
      else {
        if (shouldInsertPlus) {
          buffer.append(" + ")
        }
        val stringForMacSymbol = getStringForMacSymbol(c)
        if (stringForMacSymbol != null) {
          buffer.append(stringForMacSymbol)
          shouldInsertPlus = true
        }
        else {
          buffer.append(c)
          shouldInsertPlus = false
        }
      }
    }
    return buffer.toString()
  }

  private fun getStringForMacSymbol(c: Char): String? {
    when (c) {
      '\u238B' -> return "Escape"
      '\u21E5' -> return "Tab"
      '\u21EA' -> return "Caps Lock"
      '\u21E7' -> return "Shift"
      '\u2303' -> return "Control"
      '\u2325' -> return "Option"
      '\u2318' -> return "Command"
      '\u23CE' -> return "Return"
      '\u232B' -> return "Backspace"
      '\u2326' -> return "Delete"
      '\u2196' -> return "Home"
      '\u2198' -> return "End"
      '\u21DE' -> return "Page Up"
      '\u21DF' -> return "Page Down"
      '\u2191' -> return "Up"
      '\u2193' -> return "Down"
      '\u2190' -> return "Left"
      '\u2192' -> return "Right"
      '\u21ED' -> return "Num Lock"
      '\u2328' -> return "NumPad"
      else -> return null
    }
  }
}
