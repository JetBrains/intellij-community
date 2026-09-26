// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.workingTrees.actions

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.getPresentablePath
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.DoubleClickListener
import com.intellij.ui.ScrollingUtil
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.listCellRenderer.listCellRenderer
import git4idea.GitWorkingTree
import git4idea.i18n.GitBundle
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.KeyStroke

@Suppress("SplitModeApiUsage")
internal class GitChooseWorkingTreeDialog(project: Project, workingTrees: List<GitWorkingTree>) : DialogWrapper(project, true) {

  private val list = JBList(workingTrees)

  val selectedWorkingTree: GitWorkingTree?
    get() = list.selectedValue

  init {
    title = GitBundle.message("action.open.worktree.for.a.branch.choose.title")
    list.cellRenderer = listCellRenderer {
      text(value.path.name)
      text(StringUtil.trimMiddle(getPresentablePath(value.path.path), MAX_PATH_LENGTH)) {
        foreground = greyForeground
      }
    }

    ScrollingUtil.installActions(list)
    list.selectedIndex = 0

    object : DoubleClickListener() {
      override fun onDoubleClick(event: MouseEvent): Boolean {
        doOKAction()
        return true
      }
    }.installOn(list)
    list.registerKeyboardAction(ActionListener { doOKAction() }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_FOCUSED)

    list.selectionModel.addListSelectionListener { updateOkActionState() }
    updateOkActionState()

    init()
  }

  private fun updateOkActionState() {
    isOKActionEnabled = list.selectedIndex != -1
  }

  override fun createCenterPanel(): JComponent = JBScrollPane(list)

  override fun getPreferredFocusedComponent(): JComponent = list

  private companion object {
    const val MAX_PATH_LENGTH = 60
  }
}
