// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.ui.BranchPresentation
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.HoverChangesTree.Companion.getBackground
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ExperimentalUI
import com.intellij.ui.JBColor
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.ui.render.LabelIconCache
import com.intellij.vcs.log.ui.render.LabelPainter
import git4idea.log.GitRefManager
import git4idea.repo.GitRepositoryManager
import java.awt.Color
import java.awt.Dimension
import java.awt.Graphics
import java.awt.Graphics2D
import javax.swing.JPanel

class GitStashBranchComponent(val tree: ChangesTree, iconCache: LabelIconCache) : JPanel() {
  private val labelPainter = LabelPainter(tree, iconCache)

  private val rightGap get() = UIUtil.getScrollBarWidth()

  init {
    isOpaque = false
    labelPainter.setOpaque(false)
  }

  fun customise(branchName: String, root: VirtualFile, row: Int, selected: Boolean) {
    val repository = GitRepositoryManager.getInstance(tree.project).getRepositoryForRootQuick(root)
    val isCurrentBranch = repository?.currentBranch?.name == branchName

    val nodeLocation = TreeUtil.getNodeRowX(tree, row) + tree.insets.left
    val availableWidth = tree.width - rightGap - nodeLocation
    val foreground = getLabelForeground(selected)
    labelPainter.customizePainter(tree.getBackground(row, selected), foreground, selected, availableWidth,
                                  listOf(StashRefGroup(branchName, isCurrentBranch)))
  }

  private fun getLabelForeground(selected: Boolean): Color {
    if (selected) return UIUtil.getLabelForeground()
    if (ExperimentalUI.isNewUI()) {
      return JBColor.namedColor("VersionControl.Log.Commit.Reference.foreground", BranchPresentation.TEXT_COLOR)
    }
      return BranchPresentation.TEXT_COLOR
  }

  override fun paintComponent(g: Graphics?) {
    super.paintComponent(g)
    val g2 = g as? Graphics2D ?: return
    labelPainter.paint(g2, 0, 0, height)
  }

  override fun getPreferredSize(): Dimension {
    val dimension = labelPainter.size
    dimension.width += rightGap
    return dimension
  }

  private class StashRefGroup(private val branchName: @NlsSafe String, private val isCurrent: Boolean) : RefGroup {
    override fun getName() = branchName
    override fun getRefs() = mutableListOf<VcsRef>()
    override fun getColors(): List<Color> {
      if (isCurrent) return listOf(GitRefManager.HEAD.backgroundColor)
      return listOf(GitRefManager.LOCAL_BRANCH.backgroundColor)
    }
  }
}