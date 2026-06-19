package com.intellij.driver.sdk.ui.components.vcs.dialog

import com.intellij.driver.client.Remote
import com.intellij.driver.sdk.ui.components.ComponentData
import com.intellij.driver.sdk.ui.components.UiComponent
import com.intellij.driver.sdk.ui.components.common.IdeaFrameUI
import com.intellij.driver.sdk.ui.components.common.toolwindows.VcsLogGraphTableUi
import com.intellij.driver.sdk.ui.components.common.toolwindows.vcsLogGraphTable
import com.intellij.driver.sdk.ui.components.elements.ActionButtonUi
import com.intellij.driver.sdk.ui.components.elements.actionButton

fun IdeaFrameUI.gitFileHistory(action: GitFileHistoryUi.() -> Unit = {}): GitFileHistoryUi =
  x(GitFileHistoryUi::class.java) { byType("com.intellij.vcs.log.history.FileHistoryPanel") }.apply(action)

class GitFileHistoryUi(data: ComponentData) : UiComponent(data) {

  fun historyTable(): VcsLogGraphTableUi = vcsLogGraphTable("//div[@class='VcsLogGraphTable']")

  fun historyColumnCount(): Int = driver.cast(historyTable().component, HistoryTableRef::class).getColumnCount()

  fun showDiffButton(): ActionButtonUi = actionButton { byAccessibleName("Show Diff") }

  fun showAllAffectedFilesButton(): ActionButtonUi = actionButton { byAccessibleName("Show All Affected Files") }

  fun viewOptionsButton(): ActionButtonUi = actionButton { byAccessibleName("View Options") }

  fun branchFilter(): UiComponent = x { byClass("BranchFilterPopupComponent") }

  @Remote("javax.swing.JTable")
  interface HistoryTableRef {
    fun getColumnCount(): Int
  }
}
