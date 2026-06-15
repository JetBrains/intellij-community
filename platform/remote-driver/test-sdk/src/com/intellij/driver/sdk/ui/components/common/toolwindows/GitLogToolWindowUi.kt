package com.intellij.driver.sdk.ui.components.common.toolwindows

import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.elements.contentTabLabel
import com.intellij.driver.sdk.ui.components.elements.popupMenu
import com.intellij.driver.sdk.ui.ui
import com.intellij.driver.sdk.waitFor

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

  fun pressUndoCommit(commitMessage: String): Unit = openContextMenuForCommitAndSelectOption(commitMessage, "Undo Commit…")

  private enum class GitLogTab(val value: String) {
    CONSOLE("Console"),
    LOG("Log");
  }
}
