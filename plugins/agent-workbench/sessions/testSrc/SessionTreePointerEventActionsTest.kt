// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.sessions

import androidx.compose.foundation.lazy.LazyListState
import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.jewel.foundation.lazy.SelectableLazyListState
import org.jetbrains.jewel.foundation.lazy.tree.TreeState
import org.junit.jupiter.api.Test

@TestApplication
class SessionTreePointerEventActionsTest {
  @Test
  fun plainPrimaryClickOpensThread() {
    assertThat(
      shouldOpenOnTreeRowClick(
        isContextMenuClick = false,
        hasMultiSelectionModifier = false,
        hasContiguousSelectionModifier = false,
      )
    ).isTrue()
  }

  @Test
  fun secondaryClickDoesNotOpenThread() {
    assertThat(
      shouldOpenOnTreeRowClick(
        isContextMenuClick = true,
        hasMultiSelectionModifier = false,
        hasContiguousSelectionModifier = false,
      )
    ).isFalse()
  }

  @Test
  fun macCtrlClickIsContextMenuClick() {
    assertThat(
      isContextMenuClick(
        isSecondaryButton = false,
        hasCtrlModifier = true,
        isMacOs = true,
      )
    ).isTrue()
  }

  @Test
  fun nonMacCtrlClickIsNotContextMenuClick() {
    assertThat(
      isContextMenuClick(
        isSecondaryButton = false,
        hasCtrlModifier = true,
        isMacOs = false,
      )
    ).isFalse()
  }

  @Test
  fun multiselectModifierClickDoesNotOpenThread() {
    assertThat(
      shouldOpenOnTreeRowClick(
        isContextMenuClick = false,
        hasMultiSelectionModifier = true,
        hasContiguousSelectionModifier = false,
      )
    ).isFalse()
  }

  @Test
  fun contiguousSelectionModifierClickDoesNotOpenThread() {
    assertThat(
      shouldOpenOnTreeRowClick(
        isContextMenuClick = false,
        hasMultiSelectionModifier = false,
        hasContiguousSelectionModifier = true,
      )
    ).isFalse()
  }

  @Test
  fun consumeShouldOpenFallsBackToSelectionState() {
    val selectedKey = "selected"
    val otherKey = "other"
    val treeState = TreeState(SelectableLazyListState(LazyListState()))
    val actions = SessionTreePointerEventActions(treeState)

    treeState.selectedKeys = setOf(selectedKey)
    assertThat(actions.consumeShouldOpenOnClick(selectedKey)).isTrue()

    treeState.selectedKeys = setOf(selectedKey, otherKey)
    assertThat(actions.consumeShouldOpenOnClick(selectedKey)).isFalse()
  }
}
