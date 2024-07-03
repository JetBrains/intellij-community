// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.ui.RowIcon
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu
import com.intellij.util.ui.components.BorderLayoutPanel
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchUtil
import git4idea.branch.TagsNode
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.repo.GitRepositoryManager
import git4idea.ui.branch.GitBranchPopupActions
import icons.DvcsImplIcons
import org.jetbrains.annotations.Nls
import java.awt.Container
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.accessibility.AccessibleContext
import javax.swing.*

abstract class GitBranchesWithDetailsTreeRenderer(
  project: Project,
  treeModel: GitBranchesTreeModel,
  selectedRepository: GitRepository?,
  repositories: List<GitRepository>,
) : GitBranchesTreeRenderer(project, treeModel, selectedRepository, repositories) {
  private val secondaryLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(10)
    horizontalAlignment = SwingConstants.RIGHT
  }
  private val arrowLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(4) // 6 px in spec, but label width is differed
  }
  private val incomingOutgoingLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(10)
  }

  override val mainPanel: BorderLayoutPanel = MyMainPanel()

  abstract fun hasRightArrow(nodeUserObject: Any?): Boolean

  override fun configureTreeCellComponent(tree: JTree,
                                          userObject: Any?,
                                          value: Any?,
                                          selected: Boolean,
                                          expanded: Boolean,
                                          leaf: Boolean,
                                          row: Int,
                                          hasFocus: Boolean) {
    val (inOutIcon, inOutTooltip) = getIncomingOutgoingIconWithTooltip(userObject)
    tree.toolTipText = inOutTooltip

    incomingOutgoingLabel.apply {
      icon = inOutIcon
      isVisible = icon != null
    }

    arrowLabel.apply {
      isVisible = hasRightArrow(userObject)
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
      is GitRepository -> GitBranchUtil.getDisplayableBranchText(treeNode)
      is GitBranchesTreeModel.TopLevelRepository -> GitBranchUtil.getDisplayableBranchText(treeNode.repository)
      is GitLocalBranch -> {
        treeNode.getCommonTrackedBranch(affectedRepositories)?.name
      }
      is TagsNode -> {
        if (GitRepositoryManager.getInstance(project).repositories.any { it.tagHolder.isLoading }) GitBundle.message(
          "group.Git.Tags.loading.text")
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

  private fun getIncomingOutgoingIconWithTooltip(treeNode: Any?): Pair<Icon?, @Nls(capitalization = Nls.Capitalization.Sentence) String?> {
    val empty = null to null
    val value = treeNode ?: return empty
    return when (value) {
      is GitBranch -> getIncomingOutgoingIconWithTooltip(value)
      is GitBranchesTreeModel.RefUnderRepository -> getIncomingOutgoingIconWithTooltip(value.ref)
      else -> empty
    }
  }

  private fun getIncomingOutgoingIconWithTooltip(branch: GitBranch): Pair<Icon?, String?> {
    val branchName = branch.name
    val incomingOutgoingManager = GitBranchIncomingOutgoingManager.getInstance(project)

    val hasIncoming = affectedRepositories.any { incomingOutgoingManager.hasIncomingFor(it, branchName) }
    val hasOutgoing = affectedRepositories.any { incomingOutgoingManager.hasOutgoingFor(it, branchName) }

    val tooltip = GitBranchPopupActions.LocalBranchActions.constructIncomingOutgoingTooltip(hasIncoming, hasOutgoing).orEmpty()

    return when {
      hasIncoming && hasOutgoing -> RowIcon(DvcsImplIcons.Incoming, DvcsImplIcons.Outgoing)
      hasIncoming -> DvcsImplIcons.Incoming
      hasOutgoing -> DvcsImplIcons.Outgoing
      else -> null
    } to tooltip
  }

  private inner class MyMainPanel : BorderLayoutPanel() {
    private val branchInfoPanel = JBUI.Panels.simplePanel(mainTextComponent)
      .addToLeft(mainIconComponent)
      .addToRight(incomingOutgoingLabel)
      .andTransparent()

    private val textPanel = JPanel(GridBagLayout()).apply {
      isOpaque = false

      add(branchInfoPanel,
          GridBagConstraints().apply {
            anchor = GridBagConstraints.LINE_START
            weightx = 0.0
          })

      add(secondaryLabel,
          GridBagConstraints().apply {
            anchor = GridBagConstraints.LINE_END
            weightx = 0.75
          })
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