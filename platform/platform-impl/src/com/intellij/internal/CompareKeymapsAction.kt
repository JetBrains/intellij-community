// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.keymap.Keymap
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.keymap.ex.KeymapManagerEx
import com.intellij.ui.CollectionComboBoxModel
import com.intellij.ui.components.dialog
import com.intellij.ui.layout.*
import javax.swing.JTextArea

class CompareKeymapsAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val allKeymaps = (KeymapManager.getInstance() as KeymapManagerEx).allKeymaps
    val keymapsComboBoxModel1 = CollectionComboBoxModel(allKeymaps.toList())
    val keymapsComboBoxModel2 = CollectionComboBoxModel(allKeymaps.toList())
    var keymap1 = allKeymaps[0]
    var keymap2 = allKeymaps[1]
    val comparePanel = panel {
      row("First keymap") {
        comboBox(keymapsComboBoxModel1, { keymap1 }, { keymap1 = it })
      }
      row("Second keymap") {
        comboBox(keymapsComboBoxModel2, { keymap2 }, { keymap2 = it })
      }
    }
    if (!dialog("Compare Keymaps", comparePanel).showAndGet()) return
    val result = compareKeymaps(keymap1, keymap2)
    val report = buildString {
      appendLine("## Added shortcuts")
      appendLine("| Action | Shortcuts |")
      appendLine("| ------ | --------- |")
      val addedShortcuts = result.filter { it.keymap1Shortcuts == null }.sortedBy { it.actionName }
      for (addedShortcut in addedShortcuts) {
        appendLine("${addedShortcut.actionName} | ${addedShortcut.keymap2Shortcuts}")
      }

      appendLine("\n## Removed shortcuts")
      appendLine("| Action | Shortcuts |")
      appendLine("| ------ | --------- |")
      val removedShortcuts = result.filter { it.keymap2Shortcuts == null }.sortedBy { it.actionName }
      for (removedShortcut in removedShortcuts) {
        appendLine("${removedShortcut.actionName} | ${removedShortcut.keymap1Shortcuts}")
      }

      appendLine("\n## Changed shortcuts")
      appendLine("| Action | Old shortcuts | New shortcuts |")
      appendLine("| ------ | --------------| ------------- |")
      val changedShortcuts = result.filter { it.keymap1Shortcuts != null && it.keymap2Shortcuts != null }.sortedBy { it.actionName }
      for (changedShortcut in changedShortcuts) {
        appendLine("${changedShortcut.actionName} | ${changedShortcut.keymap1Shortcuts} |  ${changedShortcut.keymap2Shortcuts}")
      }

    }
    val reportPanel = panel {
      row {
        scrollPane(JTextArea(report, 20, 50))
      }
    }
    dialog("Compare Keymaps Result", reportPanel).show()
  }

  private data class ShortcutDifference(
    val actionId: String,
    val actionName: String,
    val keymap1Shortcuts: String?,
    val keymap2Shortcuts: String?
  )

  private fun compareKeymaps(keymap1: Keymap, keymap2: Keymap): List<ShortcutDifference> {
    val result = mutableListOf<ShortcutDifference>()
    for (actionId in ActionManager.getInstance().getActionIdList("")) {
      val actionBinding = (KeymapManager.getInstance() as KeymapManagerEx).getActionBinding(actionId)
      if (actionBinding != null) continue

      val shortcuts1 = keymap1.getShortcuts(actionId)
      val shortcuts2 = keymap2.getShortcuts(actionId)
      if (!shortcuts1.contentEquals(shortcuts2)) {
        val action = ActionManager.getInstance().getAction(actionId)
        var actionName = action.templateText ?: continue
        if (shortcuts1.all { it in shortcuts2 }) {
          val addedShortcuts = shortcuts2.filter { it !in shortcuts1 }
          result.add(ShortcutDifference(actionId, actionName,
                                        null,
                                        KeymapUtil.getShortcutsText(addedShortcuts.toTypedArray())))
        }
        else if (shortcuts2.all { it in shortcuts1 }) {
          val removedShortcuts = shortcuts1.filter { it !in shortcuts2 }
          result.add(ShortcutDifference(actionId, actionName,
                                        KeymapUtil.getShortcutsText(removedShortcuts.toTypedArray()),
                                        null))
        }
        else {
          result.add(ShortcutDifference(actionId, actionName,
                                        KeymapUtil.getShortcutsText(shortcuts1),
                                        KeymapUtil.getShortcutsText(shortcuts2)))
        }
      }
    }
    return result
  }


}