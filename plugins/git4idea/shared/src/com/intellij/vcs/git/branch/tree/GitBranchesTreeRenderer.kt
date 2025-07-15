// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.branch.tree

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.vcs.impl.shared.ui.RepositoryColorGeneratorFactory
import com.intellij.ui.ClientProperty
import com.intellij.ui.SeparatorWithText
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.ui.tree.ui.Control
import com.intellij.ui.tree.ui.DefaultControl
import com.intellij.ui.util.getAvailTextLength
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UpdateScaleHelper
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.git.branch.GitBranchesClippedNamesCache
import com.intellij.vcs.git.branch.popup.GitBranchesPopupBase
import com.intellij.vcs.git.branch.popup.GitBranchesPopupStepBase
import com.intellij.vcs.git.branch.tree.GitBranchesTreeModel.RefUnderRepository
import com.intellij.vcs.git.branch.tree.GitBranchesTreeUtil.canHighlight
import com.intellij.vcs.git.repo.GitRepositoryModel
import com.intellij.vcs.git.ui.GitBranchesTreeIconProvider
import git4idea.GitBranch
import git4idea.GitReference
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import java.awt.Graphics2D
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JTree
import javax.swing.tree.TreeCellRenderer
import javax.swing.tree.TreePath

@ApiStatus.Internal
abstract class GitBranchesTreeRenderer(
  protected val treePopupStep: GitBranchesPopupStepBase,
  private val favoriteToggleOnClickSupported: Boolean = true,
) : TreeCellRenderer {
  private val updateScaleHelper = UpdateScaleHelper()

  private val repoColorGenerator = RepositoryColorGeneratorFactory.create(treePopupStep.repositories.map { it.repositoryId })

  private fun getBranchNameClipper(treeNode: Any?): SimpleColoredComponent.FragmentTextClipper? =
    GitBranchesTreeRendererClipper.create(treePopupStep.project, treeNode)

  fun getLeftTreeIconRenderer(path: TreePath): Control? {
    val lastComponent = path.lastPathComponent
    val defaultIcon = getNodeIcon(lastComponent, false) ?: return null
    val selectedIcon = getNodeIcon(lastComponent, true) ?: return null

    return DefaultControl(defaultIcon, defaultIcon, selectedIcon, selectedIcon)
  }

  fun getIcon(treeNode: Any?, isSelected: Boolean): Icon? = when (treeNode) {
    is GitBranchesTreeModel.BranchesPrefixGroup -> GitBranchesTreeIconProvider.forGroup()
    is RefUnderRepository -> getBranchIcon(treeNode.ref, listOf(treeNode.repository), isSelected)
    is GitReference -> getBranchIcon(treeNode, treePopupStep.affectedRepositories, selected = isSelected)
    else -> null
  }

  private fun getBranchIcon(reference: GitReference,
                            repositories: List<GitRepositoryModel>,
                            selected: Boolean): Icon {
    val isCurrent = repositories.all { it.state.isCurrentRef(reference) }
    val isFavorite = repositories.all { it.favoriteRefs.contains(reference) }

    return GitBranchesTreeIconProvider.forRef(reference, current = isCurrent, favorite = isFavorite, favoriteToggleOnClick = favoriteToggleOnClickSupported, selected = selected)
  }

  private fun getNodeIcon(treeNode: Any?, isSelected: Boolean): Icon? {
    val value = treeNode ?: return null
    return when (value) {
      is PopupFactoryImpl.ActionItem -> value.getIcon(isSelected)
      is GitBranchesTreeModel.RepositoryNode -> GitBranchesTreeIconProvider.forRepository(treePopupStep.project, value.repository.repositoryId)
      else -> null
    }
  }

  protected val mainIconComponent = JLabel().apply {
    ClientProperty.put(this, MAIN_ICON, true)
    border = JBUI.Borders.emptyRight(4)  // 6 px in spec, but label width is differed
  }

  protected val mainTextComponent = SimpleColoredComponent().apply {
    isOpaque = false
    border = JBUI.Borders.empty()
  }

  abstract val mainPanel: BorderLayoutPanel

  final override fun getTreeCellRendererComponent(tree: JTree,
                                                  value: Any?,
                                                  selected: Boolean,
                                                  expanded: Boolean,
                                                  leaf: Boolean,
                                                  row: Int,
                                                  hasFocus: Boolean): Component {
    val userObject = TreeUtil.getUserObject(value)
    // render separator text in accessible mode
    if (userObject is SeparatorWithText) return userObject

    mainIconComponent.apply {
      icon = getIcon(userObject, selected)
      isVisible = icon != null
    }

    mainTextComponent.apply {
      background = JBUI.CurrentTheme.Tree.background(selected, true)
      foreground = JBUI.CurrentTheme.Tree.foreground(selected, true)

      clear()
      val text = treePopupStep.getNodeText(userObject) ?: ""

      if (isDisabledActionItem(userObject)) {
        append(text, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      }
      else {
        appendWithClipping(text, getBranchNameClipper(userObject))
      }
    }

    configureTreeCellComponent(tree, userObject, value, selected, expanded, leaf, row, hasFocus)

    if (value != null && canHighlight(treePopupStep.project, tree, userObject)) {
      SpeedSearchUtil.applySpeedSearchHighlightingFiltered(tree, value, mainTextComponent, true, selected)
    }

    if (updateScaleHelper.saveScaleAndUpdateUIIfChanged(mainPanel)) {
      tree.rowHeight = GitBranchesPopupBase.treeRowHeight
    }

    return mainPanel
  }

  abstract fun configureTreeCellComponent(tree: JTree, userObject: Any?, value: Any?, selected: Boolean, expanded: Boolean, leaf: Boolean, row: Int, hasFocus: Boolean)

  companion object {
    @JvmField
    internal val MAIN_ICON = Key.create<Boolean>("MAIN_ICON")

    internal fun isDisabledActionItem(userObject: Any?) = userObject is PopupFactoryImpl.ActionItem && !userObject.isEnabled
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
      if (treeNode is RefUnderRepository ||
          treeNode is GitBranch) {
        return GitBranchesTreeRendererClipper(project)
      }
      return null
    }
  }
}
