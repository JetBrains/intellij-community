package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.PopupMenuUiComponent
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import java.awt.event.KeyEvent

class GitLogToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {
  fun selectCommitAndGetInfo(commit: String): List<CommitId> {
    vcsLogGraphTable().clickCell { it.contains(commit) }
    return vcsLogGraphTable().logTable.getSelection().commits
  }

  fun openContextMenuForCommit(partialCommitText: String): PopupMenuUiComponent {
    vcsLogGraphTable().apply { findRowColumn { it.contains(partialCommitText) }.apply { rightClickCell(first, second) } }
    return driver.ui.popupMenu()
  }

  /**
   * Selects a commit, then extends the selection downward by [extraRows] rows using Shift+Down.
   * Right-clicks on the last selected row and returns the context menu.
   */
  fun selectMultipleCommitsAndOpenContextMenu(firstCommit: String, extraRows: Int = 1): PopupMenuUiComponent {
    val table = vcsLogGraphTable {
      waitFound()
    }
    val (row, column) = table.findRowColumn { it.contains(firstCommit) }
    table.clickCell(row, column)
    driver.ui.keyboard {
      pressing(KeyEvent.VK_SHIFT) {
        repeat(extraRows) { key(KeyEvent.VK_DOWN) }
      }
    }
    // Right-click on the originally selected commit row (still part of the multi-selection)
    table.rightClickCell(row, column)
    return driver.ui.popupMenu()
  }
}
