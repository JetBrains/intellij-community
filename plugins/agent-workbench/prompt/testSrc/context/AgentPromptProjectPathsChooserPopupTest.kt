// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.agent.workbench.prompt.context

import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.runInEdtAndWait
import com.intellij.ui.treeStructure.Tree
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.awt.event.ActionEvent
import java.awt.event.KeyEvent
import javax.swing.JComponent
import javax.swing.KeyStroke
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

@TestApplication
class AgentPromptProjectPathsChooserPopupTest {
  @Test
  fun collectConfirmedSelectionUsesTreeSelectionWhenProjectTabIsActive() {
    val existing = ManualPathSelectionEntry(path = "/repo/project/keep.txt", isDirectory = false)
    val added = ManualPathSelectionEntry(path = "/repo/project/src", isDirectory = true)
    val selectionState = ManualPathSelectionState(listOf(existing))
    var treeSyncCalls = 0
    var searchSyncCalls = 0

    val result = collectConfirmedSelection(
      isSearchTabSelected = false,
      selectionState = selectionState,
      syncTreeSelection = {
        treeSyncCalls++
        selectionState.addTreeSelection(listOf(added))
      },
      syncSearchSelection = {
        searchSyncCalls++
      },
    )

    assertThat(treeSyncCalls).isEqualTo(1)
    assertThat(searchSyncCalls).isZero()
    assertThat(result).containsExactly(existing, added)
  }

  @Test
  fun collectConfirmedSelectionUsesSearchSelectionWhenSearchTabIsActive() {
    val existing = ManualPathSelectionEntry(path = "/repo/project/keep.txt", isDirectory = false)
    val added = ManualPathSelectionEntry(path = "/repo/project/new.txt", isDirectory = false)
    val selectionState = ManualPathSelectionState(listOf(existing))
    var treeSyncCalls = 0
    var searchSyncCalls = 0

    val result = collectConfirmedSelection(
      isSearchTabSelected = true,
      selectionState = selectionState,
      syncTreeSelection = {
        treeSyncCalls++
      },
      syncSearchSelection = {
        searchSyncCalls++
        selectionState.addSearchSelection(listOf(added))
      },
    )

    assertThat(treeSyncCalls).isZero()
    assertThat(searchSyncCalls).isEqualTo(1)
    assertThat(result).containsExactly(existing, added)
  }

  @Test
  fun installConfirmSelectionOnEnterDelegatesToFallbackWhenConfirmationIsNotHandled() {
    runInEdtAndWait {
      val tree = createTree()
      val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
      var fallbackCalls = 0
      var confirmCalls = 0

      tree.registerKeyboardAction({ fallbackCalls++ }, enter, JComponent.WHEN_FOCUSED)
      installConfirmSelectionOnEnter(tree) {
        confirmCalls++
        false
      }

      invokeEnter(tree)

      assertThat(confirmCalls).isEqualTo(1)
      assertThat(fallbackCalls).isEqualTo(1)
    }
  }

  @Test
  fun installConfirmSelectionOnEnterSkipsFallbackWhenConfirmationIsHandled() {
    runInEdtAndWait {
      val tree = createTree()
      val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
      var fallbackCalls = 0
      var confirmCalls = 0

      tree.registerKeyboardAction({ fallbackCalls++ }, enter, JComponent.WHEN_FOCUSED)
      installConfirmSelectionOnEnter(tree) {
        confirmCalls++
        true
      }

      invokeEnter(tree)

      assertThat(confirmCalls).isEqualTo(1)
      assertThat(fallbackCalls).isZero()
    }
  }

  private fun createTree(): Tree {
    return Tree(DefaultTreeModel(DefaultMutableTreeNode("Root")))
  }

  private fun invokeEnter(tree: Tree) {
    val enter = KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0)
    val action = checkNotNull(tree.getActionForKeyStroke(enter)) { "No Enter action registered" }
    action.actionPerformed(ActionEvent(tree, ActionEvent.ACTION_PERFORMED, "Enter"))
  }
}
