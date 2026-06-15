package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.PopupMenuUiComponent
import com.intellij.driver.sdk.ui.components.elements.contentTabLabel
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor
import java.awt.event.KeyEvent

class GitLogToolWindowUi(data: ComponentData) : ToolWindowUiComponent(data) {

  private fun openTab(tab: GitLogTab) {
    contentTabLabel(tab.value).apply {
      click()
      waitFor("Wait until tab ${tab.value} will be selected") { isSelected }
    }
  }

  fun openConsole() = openTab(GitLogTab.CONSOLE)
  fun openLog() = openTab(GitLogTab.LOG)

  fun selectCommitAndGetInfo(commit: String): List<CommitId> {
    vcsLogGraphTable().clickCell { it.contains(commit) }
    return vcsLogGraphTable().logTable.getSelection().commits
  }

  fun openContextMenuForCommitAndSelectOption(partialCommitText: String, optionToSelect: String) {
    vcsLogGraphTable().apply { findRowColumn { it.contains(partialCommitText) }.apply { rightClickCell(first, second) } }
    driver.ui.popupMenu().select(optionToSelect)
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


  fun pressUndoCommit(commitMessage: String): Unit = openContextMenuForCommitAndSelectOption(commitMessage, "Undo Commit…")
  fun pressRevertCommit(commitMessage: String): Unit = openContextMenuForCommitAndSelectOption(commitMessage, "Revert Commit")

  private enum class GitLogTab(val value: String) {
    CONSOLE("Console"),
    LOG("Log");
  }
}
