// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.ui

import com.intellij.icons.AllIcons
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.ui.JBUI
import git4idea.i18n.GitBundle
import javax.swing.JList

internal class GitWorkingTreesListRenderer : ColoredListCellRenderer<GitWorktreeRow>() {
  override fun customizeCellRenderer(list: JList<out GitWorktreeRow?>, value: GitWorktreeRow?, index: Int, selected: Boolean, hasFocus: Boolean) {
    if (value == null) return

    iconTextGap = JBUI.scale(4)
    icon = if (value.gitWorkingTree.isCurrent) AllIcons.Actions.Checked else AllIcons.Empty

    val nameAttributes = if (value.gitWorkingTree.isMain) SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES else SimpleTextAttributes.REGULAR_ATTRIBUTES
    append(value.gitWorkingTree.path.name, nameAttributes)

    val columnGap = JBUI.scale(20)
    var padding = iconTextGap * 2 + icon.iconWidth + getWorktreeColumnWidth(list) + columnGap
    appendTextPadding(padding)

    append(value.presentableBranchName, SimpleTextAttributes.GRAY_ATTRIBUTES)

    padding += (getBranchColumnWidth(list) + columnGap)
    appendTextPadding(padding)

    append(value.location, SimpleTextAttributes.GRAY_ATTRIBUTES)

    padding += (getLocationColumnWidth(list) + columnGap)
    appendTextPadding(padding)

    val statusText = when {
      value.gitWorkingTree.isLocked -> GitBundle.message("toolwindow.working.trees.worktree.status.locked")
      value.gitWorkingTree.isPrunable -> GitBundle.message("toolwindow.working.trees.worktree.status.prunable")
      else -> null
    }
    if (statusText != null) {
      append(statusText, SimpleTextAttributes.GRAY_ATTRIBUTES)
    }
  }

  private fun getWorktreeColumnWidth(list: JList<out GitWorktreeRow?>): Int = getMaxWidth(list) { it.gitWorkingTree.path.name }
  private fun getBranchColumnWidth(list: JList<out GitWorktreeRow?>): Int = getMaxWidth(list) { it.presentableBranchName }
  private fun getLocationColumnWidth(list: JList<out GitWorktreeRow?>): Int = getMaxWidth(list) { it.location }

  private fun getMaxWidth(list: JList<out GitWorktreeRow?>, toString: (GitWorktreeRow) -> String): Int {
    val model = list.model
    var maxWidth = 0

    val fontMetrics = list.getFontMetrics(list.font)

    for (i in 0 until model.size) {
      val row = model.getElementAt(i) ?: continue
      val line = toString(row)
      val lineWidth = fontMetrics.stringWidth(line)
      if (lineWidth > maxWidth) {
        maxWidth = lineWidth
      }
    }

    return maxWidth
  }
}
