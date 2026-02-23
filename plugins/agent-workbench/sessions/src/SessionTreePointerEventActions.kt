// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.input.pointer.PointerEvent
import androidx.compose.ui.input.pointer.isCtrlPressed
import androidx.compose.ui.input.pointer.isSecondary
import org.jetbrains.jewel.foundation.lazy.SelectableColumnKeybindings
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListKey
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.SelectionMode
import org.jetbrains.jewel.foundation.lazy.tree.DefaultTreeViewPointerEventAction
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.jetbrains.skiko.hostOs

@OptIn(ExperimentalComposeUiApi::class)
internal class SessionTreePointerEventActions(
  private val treeState: TreeState,
) : DefaultTreeViewPointerEventAction(treeState) {
  private data class PendingOpenDecision(
    @JvmField val key: Any,
    @JvmField val shouldOpen: Boolean,
  )

  private var pendingOpenDecision: PendingOpenDecision? = null

  fun consumeShouldOpenOnClick(key: Any): Boolean {
    val decision = pendingOpenDecision
    if (decision != null) {
      pendingOpenDecision = null
      if (decision.key == key) {
        return decision.shouldOpen
      }
      if (!decision.shouldOpen) {
        return false
      }
    }
    return treeState.selectedKeys.size == 1 && key in treeState.selectedKeys
  }

  override fun handlePointerEventPress(
    pointerEvent: PointerEvent,
    keybindings: SelectableColumnKeybindings,
    selectableLazyListState: SelectableLazyListState,
    selectionMode: SelectionMode,
    allKeys: List<SelectableLazyListKey>,
    key: Any,
  ) {
    selectableLazyListState.isKeyboardNavigating = false

    val contextMenuClick = isContextMenuClick(pointerEvent)
    val shouldOpen = shouldOpenFromPress(pointerEvent, keybindings, contextMenuClick)
    if (contextMenuClick) {
      val itemIndex = allKeys.indexOfFirst { it.key == key }
      if (selectionMode != SelectionMode.None && key !in selectableLazyListState.selectedKeys) {
        selectableLazyListState.selectedKeys = setOf(key)
      }
      selectableLazyListState.lastActiveItemIndex = itemIndex.takeIf { it >= 0 }
      pendingOpenDecision = PendingOpenDecision(key = key, shouldOpen = false)
      return
    }

    super.handlePointerEventPress(
      pointerEvent = pointerEvent,
      keybindings = keybindings,
      selectableLazyListState = selectableLazyListState,
      selectionMode = selectionMode,
      allKeys = allKeys,
      key = key,
    )
    pendingOpenDecision = PendingOpenDecision(key = key, shouldOpen = shouldOpen)
  }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun shouldOpenFromPress(
  pointerEvent: PointerEvent,
  keybindings: SelectableColumnKeybindings,
  contextMenuClick: Boolean,
): Boolean {
  return with(keybindings) {
    shouldOpenOnTreeRowClick(
      isContextMenuClick = contextMenuClick,
      hasMultiSelectionModifier = pointerEvent.keyboardModifiers.isMultiSelectionKeyPressed,
      hasContiguousSelectionModifier = pointerEvent.keyboardModifiers.isContiguousSelectionKeyPressed,
    )
  }
}

@OptIn(ExperimentalComposeUiApi::class)
private fun isContextMenuClick(pointerEvent: PointerEvent): Boolean {
  return isContextMenuClick(
    isSecondaryButton = pointerEvent.button.isSecondary,
    hasCtrlModifier = pointerEvent.keyboardModifiers.isCtrlPressed,
    isMacOs = hostOs.isMacOS,
  )
}

internal fun isContextMenuClick(
  isSecondaryButton: Boolean,
  hasCtrlModifier: Boolean,
  isMacOs: Boolean,
): Boolean {
  return isSecondaryButton || (isMacOs && hasCtrlModifier)
}

internal fun shouldOpenOnTreeRowClick(
  isContextMenuClick: Boolean,
  hasMultiSelectionModifier: Boolean,
  hasContiguousSelectionModifier: Boolean,
): Boolean {
  return !isContextMenuClick && !hasMultiSelectionModifier && !hasContiguousSelectionModifier
}
