// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.popup

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil.ComponentStyle
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.vcs.git.shared.branch.GitInOutCountersInProject
import com.intellij.vcs.git.shared.branch.GitInOutStateHolder
import com.intellij.vcs.git.shared.branch.GitIncomingOutgoingColors
import com.intellij.vcs.git.shared.branch.calcTooltip
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchUtil
import git4idea.branch.GitTagType
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.tree.GitBranchesTreeModel
import git4idea.ui.branch.tree.GitBranchesTreeRenderer
import icons.DvcsImplIcons
import java.awt.Container
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.accessibility.AccessibleContext
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.SwingConstants

internal class GitBranchesTreePopupRenderer(treePopupStep: GitBranchesTreePopupStepBase) : GitBranchesTreeRenderer(treePopupStep) {
  private val secondaryLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(10)
    horizontalAlignment = SwingConstants.RIGHT
  }
  private val arrowLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(4) // 6 px in spec, but label width is differed
  }
  private val incomingLabel = createIncomingLabel().apply {
    border = JBUI.Borders.empty(1, 10, 0, 1)
  }

  private val outgoingLabel = createOutgoingLabel().apply {
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
      updateIncomingCommitLabel(incomingLabel, incomingOutgoingState)
      updateOutgoingCommitLabel(outgoingLabel, incomingOutgoingState)
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
      is GitBranchesTreeModel.RepositoryNode -> GitBranchUtil.getDisplayableBranchText(treeNode.repository)
      is GitLocalBranch -> {
        treeNode.getCommonTrackedBranch(treePopupStep.affectedRepositories)?.name
      }
      is GitTagType -> {
        if (treePopupStep.repositories.any { it.tagHolder.isLoading }) GitBundle.message("group.Git.Tags.loading.text")
        else null
      }
      else -> null
    }
  }

  private fun GitLocalBranch.getCommonTrackedBranch(repositories: List<GitRepository>): GitRemoteBranch? {
    var commonTrackedBranch: GitRemoteBranch? = null

    for (repository in repositories) {
      val trackedBranch = findTrackedBranch(repository) ?: return null

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
      is GitLocalBranch -> GitInOutStateHolder.getInstance(treePopupStep.project)
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

internal fun createIncomingLabel(): JLabel = JBLabel().apply {
  icon = DvcsImplIcons.Incoming
  iconTextGap = ICON_TEXT_GAP
  componentStyle = ComponentStyle.SMALL
  foreground = GitIncomingOutgoingColors.INCOMING_FOREGROUND
}

internal fun createOutgoingLabel(): JLabel = JBLabel().apply {
  icon = DvcsImplIcons.Outgoing
  iconTextGap = ICON_TEXT_GAP
  componentStyle = ComponentStyle.SMALL
  foreground = GitIncomingOutgoingColors.OUTGOING_FOREGROUND
}

private val ICON_TEXT_GAP
  get() = JBUI.scale(1)

internal fun updateIncomingCommitLabel(label: JLabel, incomingOutgoingState: GitInOutCountersInProject) {
  val isEmpty = incomingOutgoingState == GitInOutCountersInProject.EMPTY
  val totalIncoming = incomingOutgoingState.totalIncoming()

  label.isVisible = !isEmpty && (totalIncoming > 0 || incomingOutgoingState.hasUnfetched())
  if (!label.isVisible) return

  label.text = if (totalIncoming > 0) shrinkTo99(totalIncoming) else ""
}

internal fun updateOutgoingCommitLabel(label: JLabel, state: GitInOutCountersInProject) {
  val isEmpty = state == GitInOutCountersInProject.EMPTY
  val totalOutgoing = state.totalOutgoing()

  label.isVisible = !isEmpty && totalOutgoing > 0
  if (!label.isVisible) return

  label.text = if (totalOutgoing > 0) shrinkTo99(totalOutgoing) else ""
}


private fun shrinkTo99(commits: Int): @NlsSafe String {
  if (commits > 99) return "99+"
  return commits.toString()
}
