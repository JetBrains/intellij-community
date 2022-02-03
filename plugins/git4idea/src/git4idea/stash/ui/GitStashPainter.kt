// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package git4idea.stash.ui

import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.vcs.changes.savedPatches.SavedPatchesProvider
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesTreeCellRenderer
import com.intellij.openapi.vcs.changes.ui.HoverChangesTree.Companion.getBackground
import com.intellij.openapi.vcs.changes.ui.HoverChangesTree.Companion.getRowHeight
import com.intellij.openapi.vcs.changes.ui.HoverChangesTree.Companion.getTransparentScrollbarWidth
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.tree.TreeUtil
import com.intellij.vcs.log.RefGroup
import com.intellij.vcs.log.VcsRef
import com.intellij.vcs.log.ui.render.LabelIconCache
import com.intellij.vcs.log.ui.render.LabelPainter
import git4idea.log.GitRefManager
import git4idea.repo.GitRepositoryManager
import git4idea.ui.StashInfo
import git4idea.ui.StashInfo.Companion.branchName
import java.awt.Color
import java.awt.Graphics2D

class GitStashPainter(val tree: ChangesTree, private val renderer: ChangesTreeCellRenderer, iconCache: LabelIconCache) :
  LabelPainter(tree, iconCache), SavedPatchesProvider.PatchObject.Painter {

  private val labelRightGap = JBUI.scale(1)

  private var startLocation: Int = 0
  private var endLocation: Int = 0
  private var rowHeight = 0

  fun customise(stashInfo: StashInfo,
                row: Int,
                selected: Boolean) {
    val branchName = stashInfo.branchName
    if (branchName == null) {
      clearPainter()
      return
    }

    val repository = GitRepositoryManager.getInstance(tree.project).getRepositoryForRootQuick(stashInfo.root)
    val isCurrentBranch = repository?.currentBranch?.name == branchName

    val nodeLocation = TreeUtil.getNodeRowX(tree, row) + tree.insets.left
    val availableWidth = tree.visibleRect.width - tree.getTransparentScrollbarWidth() -
                         (nodeLocation - tree.visibleRect.x).coerceAtLeast(0)
    customizePainter(tree.getBackground(row, selected), UIUtil.getLabelForeground(), selected, availableWidth,
                     listOf(StashRefGroup(branchName, isCurrentBranch)))

    // label coordinates are calculated relative to the node location
    val labelEndLocation = tree.visibleRect.x + tree.visibleRect.width - nodeLocation
    val labelStartLocation = labelEndLocation - size.width - labelRightGap - tree.getTransparentScrollbarWidth()
    customizeLocation(labelStartLocation, labelEndLocation, tree.getRowHeight(renderer))
  }

  private fun customizeLocation(startLocation: Int, endLocation: Int, rowHeight: Int) {
    this.startLocation = startLocation
    this.endLocation = endLocation
    this.rowHeight = rowHeight
  }

  private fun clearPainter() {
    myLabels.clear()
  }

  override fun paint(g2: Graphics2D) {
    paint(g2, startLocation, 0, rowHeight)

    if (myLabels.isNotEmpty()) {
      // paint the space after the label
      val labelEnd = startLocation + size.width
      if (labelEnd != endLocation) {
        g2.color = myBackground
        g2.fillRect(labelEnd, 0, endLocation - labelEnd, rowHeight)
      }
    }
  }

  private class StashRefGroup(private val branchName: @NlsSafe String, private val isCurrent: Boolean) : RefGroup {
    override fun getName() = branchName
    override fun getRefs() = mutableListOf<VcsRef>()
    override fun isExpanded() = false
    override fun getColors(): List<Color> {
      if (isCurrent) return listOf(GitRefManager.HEAD.backgroundColor,
                                   GitRefManager.LOCAL_BRANCH.backgroundColor)
      return listOf(GitRefManager.LOCAL_BRANCH.backgroundColor)
    }
  }
}