// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.conflicts

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.merge.MergeDialogCustomizer
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JButtonAction
import com.intellij.util.ui.UIUtil
import git4idea.i18n.GitBundle
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class GitConflictsView(
  project: Project,
  mergeDialogCustomizer: MergeDialogCustomizer
) : Disposable {

  private val mergeHandler: GitMergeHandler = GitMergeHandler(project, mergeDialogCustomizer)

  private val panel: SimpleToolWindowPanel
  private val descriptionLabel: JLabel
  private val conflictsPanel: GitConflictsPanel

  init {
    conflictsPanel = GitConflictsPanel(project, mergeHandler)
    Disposer.register(this, conflictsPanel)

    val actionManager = ActionManager.getInstance()
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.addAction(ResolveAction())
    toolbarGroup.addAction(AcceptSideAction(false))
    toolbarGroup.addAction(AcceptSideAction(true))
    toolbarGroup.addAction(Separator.getInstance())
    toolbarGroup.addAction(actionManager.getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    val toolbar = actionManager.createActionToolbar("GitConflictsView", toolbarGroup, false)
    toolbar.setTargetComponent(conflictsPanel.preferredFocusableComponent)

    val mainPanel = JPanel(BorderLayout())

    descriptionLabel = JLabel(GitBundle.message("conflicts.loading.status"))
    conflictsPanel.addListener(object : GitConflictsPanel.Listener {
      override fun onDescriptionChange(description: String) {
        descriptionLabel.text = description
      }
    })

    mainPanel.add(descriptionLabel, BorderLayout.SOUTH)
    mainPanel.add(conflictsPanel.component, BorderLayout.CENTER)

    descriptionLabel.border = JBUI.Borders.empty(2, 5)
    descriptionLabel.foreground = UIUtil.getContextHelpForeground()
    descriptionLabel.background = UIUtil.getTreeBackground()
    mainPanel.background = UIUtil.getTreeBackground()

    panel = SimpleToolWindowPanel(true, true)
    panel.toolbar = toolbar.component
    panel.setContent(mainPanel)
  }

  val component: JComponent get() = panel
  val preferredFocusableComponent: JComponent get() = conflictsPanel.preferredFocusableComponent

  override fun dispose() {
  }

  private inner class ResolveAction
    : JButtonAction(GitBundle.message("conflicts.resolve.action.text")) {

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = conflictsPanel.canShowMergeWindowForSelection()
      updateButtonFromPresentation(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
      conflictsPanel.showMergeWindowForSelection()
    }
  }

  private inner class AcceptSideAction(val takeTheirs: Boolean)
    : JButtonAction(if (takeTheirs) GitBundle.message("conflicts.accept.theirs.action.text")
                    else GitBundle.message("conflicts.accept.yours.action.text") ) {

    override fun update(e: AnActionEvent) {
      e.presentation.isEnabled = conflictsPanel.canAcceptConflictSideForSelection()
      updateButtonFromPresentation(e)
    }

    override fun actionPerformed(e: AnActionEvent) {
      conflictsPanel.acceptConflictSideForSelection(takeTheirs)
    }
  }
}
