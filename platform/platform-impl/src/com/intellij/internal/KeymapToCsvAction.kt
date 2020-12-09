// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.keymap.KeymapManager

class KeymapToCsvAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val allShortcuts = linkedMapOf<String, MutableMap<String, MutableList<String>>>()
    val seenModifierSets = mutableSetOf<String>()
    val columns = mutableListOf("", "shift", "ctrl", "alt", "meta")

    for (key in 'A'..'Z') {
      allShortcuts[key.toString()] = hashMapOf()
    }
    for (key in '0'..'9') {
      allShortcuts[key.toString()] = hashMapOf()
    }
    for (key in 1..12) {
      allShortcuts["F$key"] = hashMapOf()
    }

    val keymap = KeymapManager.getInstance().activeKeymap
    for (actionId in keymap.actionIdList) {
      val shortcuts = keymap.getShortcuts(actionId).filterIsInstance<KeyboardShortcut>()
      for (shortcut in shortcuts) {
        val keyStroke = shortcut.firstKeyStroke
        val str = keyStroke.toString()
        val keyName = str.substringAfterLast(' ', str)
        val modifiers = str.substringBeforeLast(' ').replace("pressed", "").trim()
        seenModifierSets += modifiers
        val forKey = allShortcuts.getOrPut(keyName) { hashMapOf() }
        val actionIds = forKey.getOrPut(modifiers) { mutableListOf() }
        actionIds.add(actionId)
      }
    }

    for (seenModifierSet in seenModifierSets) {
      if (seenModifierSet !in columns) {
        columns.add(seenModifierSet)
      }
    }

    val result = buildString {
      appendln("key," + columns.joinToString(","))
      for ((key, shortcutsForKey) in allShortcuts) {
        append(key)
        for (column in columns) {
          append(",")
          val actionsForShortcut = shortcutsForKey[column] ?: emptyList<String>()
          append(actionsForShortcut.joinToString("|"))
        }
        appendln()
      }
    }

    val dialog = FileChooserFactory.getInstance().createSaveFileDialog(
      FileSaverDescriptor("Export Keymap to .csv", "Select file to save to:", "csv"), e.project)
    val virtualFileWrapper = dialog.save(null)
    virtualFileWrapper?.file?.writeText(result.replace("\n", "\r\n"))
  }
}