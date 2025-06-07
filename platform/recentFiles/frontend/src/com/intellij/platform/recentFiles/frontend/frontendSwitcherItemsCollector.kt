// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.frontend

import com.intellij.ide.actions.ActivateToolWindowAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.impl.FileEditorManagerImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.NaturalComparator
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.util.text.Strings
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ex.ToolWindowManagerEx
import com.intellij.platform.recentFiles.shared.SWITCHER_ELEMENTS_LIMIT

private val LOG by lazy { fileLogger() }

private val mnemonicAndMainTextComparator by lazy {
  Comparator.comparing(SwitcherListItem::mnemonic) { m1, m2 ->
    if (m1 == null) (if (m2 == null) 0 else 1) else if (m2 == null) -1 else m1.compareTo(m2)
  }
    .thenComparing(SwitcherListItem::mainText, NaturalComparator.INSTANCE)
}

internal fun collectFilesFromFrontendEditorSelectionHistory(project: Project): List<VirtualFile> {
  return (FileEditorManager.getInstance(project) as? FileEditorManagerImpl)
    ?.getSelectionHistoryList()
    .orEmpty()
    .asSequence()
    .take(SWITCHER_ELEMENTS_LIMIT)
    .map { it.first }
    .toList()
}

internal fun collectToolWindows(onlyEditedFilesShown: Boolean, pinned: Boolean, isSpeedSearchInstalled: Boolean, mnemonicsRegistry: SwitcherMnemonicsRegistry, project: Project): List<SwitcherToolWindow> {
  if (!shouldDisplayToolWindowsInSwitcher(onlyEditedFilesShown)) return emptyList()

  val windows = ToolWindowManagerEx.getInstanceEx(project).toolWindows
    .filter { it.isAvailable && it.isShowStripeButton }
    .map { window -> SwitcherToolWindow(window, window.id, pinned, null) }

  val showMnemonics = !isSpeedSearchInstalled || Registry.`is`("ide.recent.files.tool.window.mnemonics")
  val maybeWindowsWithMnemonics = if (showMnemonics || Registry.`is`("ide.recent.files.tool.window.sort.by.mnemonics")) {
    updateMnemonics(windows, mnemonicsRegistry)
  }
  else {
    windows
  }

  return maybeWindowsWithMnemonics
    .sortedWith(mnemonicAndMainTextComparator)
}

private fun updateMnemonics(windows: List<SwitcherToolWindow>, mnemonicsRegistry: SwitcherMnemonicsRegistry): List<SwitcherToolWindow> {
  val keymap: MutableMap<String?, SwitcherToolWindow?> = HashMap(windows.size)
  keymap[mnemonicsRegistry.forbiddenMnemonic] = null
  addForbiddenMnemonics(keymap, "SwitcherForward", mnemonicsRegistry)
  addForbiddenMnemonics(keymap, "SwitcherBackward", mnemonicsRegistry)
  addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_UP, mnemonicsRegistry)
  addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_DOWN, mnemonicsRegistry)
  addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_LEFT, mnemonicsRegistry)
  addForbiddenMnemonics(keymap, IdeActions.ACTION_EDITOR_MOVE_CARET_RIGHT, mnemonicsRegistry)

  val toolWindowsToAssignMnemonicsTo = mutableListOf<SwitcherToolWindow>()
  val toolWindowsWithAlreadyAssignedMnemonics = mutableListOf<SwitcherToolWindow>()

  for (window in windows) {
    val index = ActivateToolWindowAction.Manager.getMnemonicForToolWindow(window.window.id)
    val indexShortcut = getIndexShortcut(index - '0'.code) // can never be null here in the current implementation
    if (index < '0'.code || index > '9'.code || indexShortcut == null) {
      val maybeToolWindowWithAssignedMnemonics = if (indexShortcut != null) addShortcut(keymap, window, indexShortcut) else null
      if (maybeToolWindowWithAssignedMnemonics != null) {
        toolWindowsWithAlreadyAssignedMnemonics.add(maybeToolWindowWithAssignedMnemonics)
      }
      else {
        toolWindowsToAssignMnemonicsTo.add(window)
      }
    }
    else {
      val toolWindowWithShortcut = addShortcut(keymap, window, indexShortcut) ?: continue
      toolWindowsWithAlreadyAssignedMnemonics.add(toolWindowWithShortcut)
    }
  }
  var i = 0

  for (window in toolWindowsToAssignMnemonicsTo) {
    val maybeToolWindowWithAssignedSmartMnemonics = addSmartShortcut(window, keymap)
    if (maybeToolWindowWithAssignedSmartMnemonics != null) {
      toolWindowsWithAlreadyAssignedMnemonics.add(maybeToolWindowWithAssignedSmartMnemonics)
      continue
    }

    while (true) {
      val indexShortcut = getIndexShortcut(i)
      if (indexShortcut == null) {
        break // ran out of shortcuts
      }
      val maybeToolWindowWithAssignedMnemonics = addShortcut(keymap, window, indexShortcut)
      if (maybeToolWindowWithAssignedMnemonics != null) {
        toolWindowsWithAlreadyAssignedMnemonics.add(maybeToolWindowWithAssignedMnemonics)
        ++i // added successfully should use the next shortcut for the next window
        break
      }
      else {
        ++i // shortcut not suitable, let's try the next one
      }
    }
  }
  if (windows.size != toolWindowsWithAlreadyAssignedMnemonics.size) {
    val processedToolWindows = toolWindowsWithAlreadyAssignedMnemonics
      .joinToString(separator = ", ", postfix = "\n") { it.window.id }
    val skippedToolWindows = windows.toSet().minus(toolWindowsWithAlreadyAssignedMnemonics)
      .filter { it !in toolWindowsWithAlreadyAssignedMnemonics }
      .joinToString(separator = ", ", postfix = "\n") { it.window.id }
    LOG.warn("There are ${windows.size - toolWindowsWithAlreadyAssignedMnemonics.size} excluded from the resulting toolwindows list. " +
             "The following toolwindows were processed: ${processedToolWindows}The following toolwindows were skipped: $skippedToolWindows"
    )
  }
  return toolWindowsWithAlreadyAssignedMnemonics
}

private fun getIndexShortcut(index: Int): String? {
  if (index !in 0..35) return null
  return Strings.toUpperCase(index.toString(radix = (index + 1).coerceIn(2..36)))
}

private fun addForbiddenMnemonics(keymap: MutableMap<String?, SwitcherToolWindow?>, actionId: String, mnemonicsRegistry: SwitcherMnemonicsRegistry) {
  for (shortcut in ActionUtil.getShortcutSet(actionId).shortcuts) {
    if (shortcut is KeyboardShortcut) {
      keymap[mnemonicsRegistry.getForbiddenMnemonic(shortcut.firstKeyStroke)] = null
    }
  }
}

private fun addSmartShortcut(window: SwitcherToolWindow, keymap: MutableMap<String?, SwitcherToolWindow?>): SwitcherToolWindow? {
  val title = window.mainText
  if (StringUtil.isEmpty(title)) return null
  for (c in title) {
    if (Character.isUpperCase(c)) {
      val maybeToolWindowWithAssignedMnemonics = addShortcut(keymap, window, c.toString())
      if (maybeToolWindowWithAssignedMnemonics != null) {
        return maybeToolWindowWithAssignedMnemonics
      }
    }
  }
  return null
}

// returns new tool window wrapper if a mnemomonic was assigned, null otherwise
private fun addShortcut(keymap: MutableMap<String?, SwitcherToolWindow?>, window: SwitcherToolWindow, shortcut: String): SwitcherToolWindow? {
  val maybeTheSameToolWindow = keymap[shortcut]
  if (maybeTheSameToolWindow == window) return window
  if (maybeTheSameToolWindow != null) return null
  keymap[shortcut] = window
  return window.copy(mnemonic = shortcut)
}


private fun shouldDisplayToolWindowsInSwitcher(onlyEditedFilesShown: Boolean): Boolean {
  return if (onlyEditedFilesShown)
    Registry.`is`("ide.recent.files.tool.window.list")
  else
    Registry.`is`("ide.switcher.tool.window.list")
}