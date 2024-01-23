// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.ui.branch.tree

import com.intellij.dvcs.DvcsUtil
import com.intellij.dvcs.ui.RepositoryChangesBrowserNode
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.*
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.tree.ui.DefaultControl
import com.intellij.ui.util.getAvailTextLength
import com.intellij.util.PlatformIcons
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UpdateScaleHelper
import com.intellij.util.ui.accessibility.AccessibleContextDelegateWithContextMenu
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import git4idea.GitBranch
import git4idea.GitLocalBranch
import git4idea.GitRemoteBranch
import git4idea.branch.GitBranchIncomingOutgoingManager
import git4idea.branch.GitBranchType
import git4idea.branch.GitBranchUtil
import git4idea.i18n.GitBundle
import git4idea.repo.GitRepository
import git4idea.ui.branch.GitBranchManager
import git4idea.ui.branch.GitBranchPopupActions
import git4idea.ui.branch.GitBranchesClippedNamesCache
import git4idea.ui.branch.popup.GitBranchesTreePopup
import git4idea.ui.branch.tree.GitBranchesTreeModel.BranchUnderRepository
import git4idea.ui.branch.tree.GitBranchesTreeUtil.canHighlight
import icons.DvcsImplIcons
import org.jetbrains.annotations.Nls
import java.awt.*
import javax.accessibility.AccessibleContext
import javax.swing.*
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

abstract class GitBranchesTreeRenderer(private val project: Project,
                                       private val treeModel: GitBranchesTreeModel,
                                       private val selectedRepository: GitRepository?,
                                       private val repositories: List<GitRepository>) : TreeCellRenderer {

  private val colorManager = RepositoryChangesBrowserNode.getColorManager(project)

  private val updateScaleHelper = UpdateScaleHelper()

  private val affectedRepositories get() = selectedRepository?.let(::listOf) ?: repositories

  abstract fun hasRightArrow(nodeUserObject: Any?): Boolean

  private fun getBranchNameClipper(treeNode: Any?): SimpleColoredComponent.FragmentTextClipper? =
    GitBranchesTreeRendererClipper.create(project, treeNode)

  fun getLeftTreeIconRenderer(path: TreePath): Control? {
    val lastComponent = path.lastPathComponent
    val defaultIcon = getNodeIcon(lastComponent, false) ?: return null
    val selectedIcon = getNodeIcon(lastComponent, true) ?: return null

    return DefaultControl(defaultIcon, defaultIcon, selectedIcon, selectedIcon)
  }

  fun getIcon(treeNode: Any?, isSelected: Boolean): Icon? {
    val value = treeNode ?: return null
    return when (value) {
      is GitBranchesTreeModel.BranchesPrefixGroup -> PlatformIcons.FOLDER_ICON
      is BranchUnderRepository -> getBranchIcon(value.branch, listOf(value.repository), isSelected)
      is GitBranch -> getBranchIcon(value, affectedRepositories, isSelected)
      else -> null
    }
  }

  private fun getBranchIcon(branch: GitBranch, repositories: List<GitRepository>, isSelected: Boolean): Icon {
    val isCurrent =
      selectedRepository?.let { it.currentBranch == branch } ?: repositories.all { it.currentBranch == branch }

    val branchManager = project.service<GitBranchManager>()
    val isFavorite =
      selectedRepository?.let { branchManager.isFavorite(GitBranchType.of(branch), it, branch.name) }
      ?: repositories.all { branchManager.isFavorite(GitBranchType.of(branch), it, branch.name) }

    return when {
      isSelected && isFavorite -> AllIcons.Nodes.Favorite
      isSelected -> AllIcons.Nodes.NotFavoriteOnHover
      isCurrent && isFavorite -> DvcsImplIcons.CurrentBranchFavoriteLabel
      isCurrent -> DvcsImplIcons.CurrentBranchLabel
      isFavorite -> AllIcons.Nodes.Favorite
      else -> AllIcons.Vcs.BranchNode
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

  private fun getNodeIcon(treeNode: Any?, isSelected: Boolean): Icon? {
    val value = treeNode ?: return null
    return when (value) {
      is PopupFactoryImpl.ActionItem -> value.getIcon(isSelected)
      is GitRepository -> RepositoryChangesBrowserNode.getRepositoryIcon(value, colorManager)
      is GitBranchesTreeModel.TopLevelRepository -> RepositoryChangesBrowserNode.getRepositoryIcon(value.repository, colorManager)
      else -> null
    }
  }

  private fun getIncomingOutgoingIconWithTooltip(treeNode: Any?): Pair<Icon?, @Nls(capitalization = Nls.Capitalization.Sentence) String?> {
    val empty = null to null
    val value = treeNode ?: return empty
    return when (value) {
      is GitBranch -> getIncomingOutgoingIconWithTooltip(value)
      is BranchUnderRepository -> getIncomingOutgoingIconWithTooltip(value.branch)
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

  private val mainIconComponent = JLabel().apply {
    ClientProperty.put(this, MAIN_ICON, true)
    border = JBUI.Borders.emptyRight(4)  // 6 px in spec, but label width is differed
  }
  private val mainTextComponent = SimpleColoredComponent().apply {
    isOpaque = false
    border = JBUI.Borders.empty()
  }
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

  private val branchInfoPanel = JBUI.Panels.simplePanel(mainTextComponent)
    .addToLeft(mainIconComponent)
    .addToRight(incomingOutgoingLabel)
    .andTransparent()

  private val textPanel =
    JPanel(GridBagLayout()).apply {
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

  private inner class MyMainPanel : BorderLayoutPanel() {
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

  private val mainPanel = MyMainPanel()

  override fun getTreeCellRendererComponent(tree: JTree,
                                            value: Any?,
                                            selected: Boolean,
                                            expanded: Boolean,
                                            leaf: Boolean,
                                            row: Int,
                                            hasFocus: Boolean): Component {
    val userObject = TreeUtil.getUserObject(value)
    // render separator text in accessible mode
    if (userObject is SeparatorWithText) return userObject
    val disabledAction = userObject is PopupFactoryImpl.ActionItem && !userObject.isEnabled

    mainIconComponent.apply {
      icon = getIcon(userObject, selected)
      isVisible = icon != null
    }

    mainTextComponent.apply {
      background = JBUI.CurrentTheme.Tree.background(selected, true)
      foreground = JBUI.CurrentTheme.Tree.foreground(selected, true)

      clear()
      val text = getText(userObject, treeModel, affectedRepositories).orEmpty()

      if (disabledAction) {
        append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      else {
        appendWithClipping(text, getBranchNameClipper(userObject))
      }
    }

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
        disabledAction -> NamedColorUtil.getInactiveTextColor()
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

    if (value != null && canHighlight(project, tree, userObject)) {
      SpeedSearchUtil.applySpeedSearchHighlightingFiltered(tree, value, mainTextComponent, true, selected)
    }

    if (updateScaleHelper.saveScaleAndUpdateUIIfChanged(mainPanel)) {
      tree.rowHeight = GitBranchesTreePopup.treeRowHeight
    }

    return mainPanel
  }

  companion object {
    @JvmField
    internal val MAIN_ICON = Key.create<Boolean>("MAIN_ICON")

    internal fun getText(treeNode: Any?, model: GitBranchesTreeModel, repositories: List<GitRepository>): @NlsSafe String? {
      val value = treeNode ?: return null
      return when (value) {
        GitBranchesTreeModel.RecentNode -> {
          when (model) {
            is GitBranchesTreeSelectedRepoModel -> GitBundle.message("group.Git.Recent.Branch.in.repo.title",
                                                                     DvcsUtil.getShortRepositoryName(model.selectedRepository))
            else -> GitBundle.message("group.Git.Recent.Branch.title")
          }
        }
        GitBranchType.LOCAL -> {
          when {
            model is GitBranchesTreeSelectedRepoModel -> GitBundle.message("branches.local.branches.in.repo",
                                                                           DvcsUtil.getShortRepositoryName(model.selectedRepository))
            repositories.size > 1 -> GitBundle.message("common.local.branches")
            else -> GitBundle.message("group.Git.Local.Branch.title")
          }
        }
        GitBranchType.REMOTE -> {
          when {
            model is GitBranchesTreeSelectedRepoModel -> GitBundle.message("branches.remote.branches.in.repo",
                                                                           DvcsUtil.getShortRepositoryName(model.selectedRepository))
            repositories.size > 1 -> GitBundle.message("common.remote.branches")
            else -> GitBundle.message("group.Git.Remote.Branch.title")
          }
        }
        is GitBranchesTreeModel.BranchesPrefixGroup -> value.prefix.last()
        is GitRepository -> DvcsUtil.getShortRepositoryName(value)
        is GitBranchesTreeModel.BranchTypeUnderRepository -> {
          when (value.type) {
            GitBranchesTreeModel.RecentNode -> GitBundle.message("group.Git.Recent.Branch.title")
            GitBranchType.LOCAL -> GitBundle.message("group.Git.Local.Branch.title")
            GitBranchType.REMOTE -> GitBundle.message("group.Git.Remote.Branch.title")
            else -> null
          }
        }
        is BranchUnderRepository -> getText(value.branch, model, repositories)
        is GitBranch -> if (model.isPrefixGrouping) value.name.split('/').last() else value.name
        is PopupFactoryImpl.ActionItem -> value.text
        is GitBranchesTreeModel.PresentableNode -> value.presentableText
        else -> null
      }
    }
  }
}

private class GitBranchesTreeRendererClipper(private val project: Project) : SimpleColoredComponent.FragmentTextClipper {

  override fun clipText(component: SimpleColoredComponent, g2: Graphics2D, fragmentIndex: Int, text: String, availTextWidth: Int): String {
    if (component.fragmentCount > 1) return text
    val clipCache = project.service<GitBranchesClippedNamesCache>()
    return clipCache.getOrCache(text, component.getAvailTextLength(text, availTextWidth))
  }

  companion object {
    fun create(project: Project, treeNode: Any?): SimpleColoredComponent.FragmentTextClipper? {
      if (treeNode is BranchUnderRepository ||
          treeNode is GitBranch) {
        return GitBranchesTreeRendererClipper(project)
      }
      return null
    }
  }
}
