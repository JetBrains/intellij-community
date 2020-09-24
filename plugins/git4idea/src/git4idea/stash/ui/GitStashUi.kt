// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.stash.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.TreeActionsToolbarPanel
import com.intellij.ui.PopupHandler
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.SideBorder
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class GitStashUi(project: Project, disposable: Disposable) : Disposable {
  val mainComponent: JPanel = JPanel(BorderLayout())
  private val tree: ChangesTree

  init {
    tree = GitStashTree(project, this)
    PopupHandler.installPopupHandler(tree, "Git.Stash.ContextMenu", GIT_STASH_UI_PLACE)

    mainComponent.add(buildToolbar(), BorderLayout.NORTH)
    mainComponent.add(ScrollPaneFactory.createScrollPane(tree, SideBorder.TOP), BorderLayout.CENTER)

    Disposer.register(disposable, this)
  }

  private fun buildToolbar(): JComponent {
    val toolbarGroup = DefaultActionGroup()
    toolbarGroup.add(ActionManager.getInstance().getAction(ChangesTree.GROUP_BY_ACTION_GROUP))
    toolbarGroup.addSeparator()
    toolbarGroup.addAll(TreeActionsToolbarPanel.createTreeActions(tree))
    val toolbar = ActionManager.getInstance().createActionToolbar(GIT_STASH_UI_PLACE, toolbarGroup, true)
    toolbar.setTargetComponent(tree)
    return toolbar.component
  }

  override fun dispose() {
  }

  companion object {
    const val GIT_STASH_UI_PLACE = "GitStashUiPlace"
  }
}