// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.popup

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.git.branch.GitInOutCountersInProject
import com.intellij.vcs.git.branch.GitInOutStateHolder
import com.intellij.vcs.git.branch.calcTooltip
import com.intellij.vcs.git.branch.tree.GitBranchesTreeModel
import com.intellij.vcs.git.branch.tree.GitBranchesTreeRenderer
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.ui.GitIncomingOutgoingUi
import git4idea.GitRemoteBranch
import git4idea.GitStandardLocalBranch
import org.jetbrains.annotations.ApiStatus
import java.awt.Container
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.accessibility.AccessibleContext
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants

@ApiStatus.Internal
class GitDefaultBranchesTreeRenderer(treePopupStep: GitBranchesPopupStepBase) : GitBranchesTreeRenderer(treePopupStep) {
  private val secondaryLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(10)
    horizontalAlignment = SwingConstants.RIGHT
  }
  private val arrowLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(4) // 6 px in spec, but label width is differed
  }
  private val incomingLabel = GitIncomingOutgoingUi.createIncomingLabel().apply {
    border = JBUI.Borders.empty(1, 10, 0, 1)
  }

  private val outgoingLabel = GitIncomingOutgoingUi.createOutgoingLabel().apply {
    border = JBUI.Borders.empty(1, 2, 0, 10)
  }

  override val mainPanel: BorderLayoutPanel = MyMainPanel()

  override fun configureTreeCellComponent(
    tree: JTree,
    userObject: Any?,
    value: Any?,
    selected: Boolean,
    expanded: Boolean,
    leaf: Boolean,
    row: Int,
    hasFocus: Boolean,
  ) {
    val incomingOutgoingState = getIncomingOutgoingState(userObject)

    if (incomingOutgoingState != GitInOutCountersInProject.EMPTY) {
      GitIncomingOutgoingUi.updateIncomingCommitLabel(incomingLabel, incomingOutgoingState)
      GitIncomingOutgoingUi.updateOutgoingCommitLabel(outgoingLabel, incomingOutgoingState)
      tree.toolTipText = incomingOutgoingState.calcTooltip()
    }
    else {
      incomingLabel.isVisible = false
      outgoingLabel.isVisible = false
      tree.toolTipText = null
    }

    arrowLabel.apply {
      isVisible = treePopupStep.hasSubstep(userObject)
      icon = if (selected) AllIcons.Icons.Ide.MenuArrowSelected else AllIcons.Icons.Ide.MenuArrow
    }

    secondaryLabel.apply {
      text = getSecondaryText(userObject)
      //todo: LAF color
      foreground = when {
        isDisabledActionItem(userObject) -> NamedColorUtil.getInactiveTextColor()
        selected -> JBUI.CurrentTheme.Tree.foreground(true, true)
        else -> JBColor.GRAY
      }

      border = if (!arrowLabel.isVisible && ExperimentalUI.isNewUI()) {
        JBUI.Borders.empty(0, 10, 0, JBUI.CurrentTheme.Popup.Selection.innerInsets().right)
      }
      else {
        JBUI.Borders.emptyLeft(10)
      }
    }
  }


  private fun getSecondaryText(treeNode: Any?): @NlsSafe String? {
    return when (treeNode) {
      is PopupFactoryImpl.ActionItem -> KeymapUtil.getFirstKeyboardShortcutText(treeNode.action)
      is GitBranchesTreeModel.RepositoryNode -> treeNode.repository.state.getDisplayableBranchText()
      is GitStandardLocalBranch -> {
        treeNode.getCommonTrackedBranch(treePopupStep.affectedRepositories)?.name
      }
      else -> null
    }
  }

  private fun GitStandardLocalBranch.getCommonTrackedBranch(repositories: List<GitRepositoryModel>): GitRemoteBranch? {
    var commonTrackedBranch: GitRemoteBranch? = null

    for (repository in repositories) {
      val trackedBranch = repository.state.getTrackingInfo(this) ?: return null

      if (commonTrackedBranch == null) {
        commonTrackedBranch = trackedBranch
      }
      else if (commonTrackedBranch.name != trackedBranch.name) {
        return null
      }
    }
    return commonTrackedBranch
  }

  private fun getIncomingOutgoingState(treeNode: Any?): GitInOutCountersInProject {
    treeNode ?: return GitInOutCountersInProject.EMPTY

    return when (treeNode) {
      is GitStandardLocalBranch -> GitInOutStateHolder.getInstance(treePopupStep.project)
        .getState(treeNode, treePopupStep.affectedRepositoriesIds)
      is GitBranchesTreeModel.RefUnderRepository -> getIncomingOutgoingState(treeNode.ref)
      else -> GitInOutCountersInProject.EMPTY
    }
  }

  private inner class MyMainPanel : BorderLayoutPanel() {
    private val branchInfoPanel = JBUI.Panels.simplePanel(mainTextComponent)
      .addToLeft(mainIconComponent)
      .andTransparent()

    private val textPanel = JPanel(GridBagLayout()).apply {
      isOpaque = false

      val gbc = GridBagConstraints().apply {
        anchor = GridBagConstraints.LINE_START
        weightx = 0.0
      }

      add(branchInfoPanel, gbc)
      add(incomingLabel, gbc)
      add(outgoingLabel, gbc)

      gbc.anchor = GridBagConstraints.LINE_END
      gbc.weightx = 0.75
      add(secondaryLabel, gbc)
    }

    init {
      addToCenter(textPanel)
      addToRight(arrowLabel)
      andTransparent()
      withBorder(JBUI.Borders.emptyRight(JBUI.CurrentTheme.ActionsList.cellPadding().right))
    }

    override fun getAccessibleContext(): AccessibleContext {
      if (accessibleContext == null) {
        accessibleContext = object : AccessibleContextDelegateWithContextMenu(mainTextComponent.accessibleContext) {
          override fun getDelegateParent(): Container = parent

          override fun doShowContextMenu() {
            ActionManager.getInstance().tryToExecute(ActionManager.getInstance().getAction("ShowPopupMenu"), null, null, null, true)
          }
        }
      }
      return accessibleContext
    }
  }
}
