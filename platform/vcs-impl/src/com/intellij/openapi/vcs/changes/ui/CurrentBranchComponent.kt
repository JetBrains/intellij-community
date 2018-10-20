// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ChangesUtil.getFilePath
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingSupport.Companion.REPOSITORY_GROUPING
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColorUtil
import com.intellij.ui.JBColor
import com.intellij.ui.TextIcon
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI.Borders.customLine
import com.intellij.util.ui.JBUI.emptySize
import com.intellij.util.ui.JBUI.insets
import com.intellij.util.ui.UIUtil.*
import com.intellij.vcs.branch.BranchData
import com.intellij.vcs.branch.BranchStateProvider
import com.intellij.vcs.branch.LinkedBranchData
import com.intellij.vcsUtil.VcsUtil.getFilePath
import java.awt.Color
import java.awt.Dimension
import javax.swing.JTree.TREE_MODEL_PROPERTY

private const val BALANCE = 0.08
private val BACKGROUND = JBColor(Color.BLACK, Color.WHITE)
private val TEXT_INSETS = insets(5, 3, 4, 2)

class CurrentBranchComponent(val project: Project, val browser: CommitDialogChangesBrowser) : JBLabel() {
  private var branches = setOf<BranchData>()

  private val textIcon = TextIcon(null, TEXT_COLOR, getBranchPresentationBackground(getTreeBackground()), 0).apply {
    setInsets(TEXT_INSETS)
    setFont(getLabelFont())
  }

  private val isGroupedByRepository: Boolean
    get() {
      val groupingSupport = browser.viewer.groupingSupport
      return groupingSupport.isAvailable(REPOSITORY_GROUPING) && groupingSupport[REPOSITORY_GROUPING]
    }

  init {
    border = customLine(getTreeBackground())
    isOpaque = false
    icon = textIcon

    browser.viewer.addPropertyChangeListener { e ->
      if (e.propertyName == TREE_MODEL_PROPERTY) {
        refresh()
      }
    }
  }

  override fun getPreferredSize(): Dimension? = if (isVisible) super.getPreferredSize() else emptySize()

  private fun refresh() {
    isVisible = !isGroupedByRepository
    if (isVisible) {
      setData(browser.displayedChanges, browser.displayedUnversionedFiles)
    }
  }

  private fun setData(changes: Iterable<Change>, unversioned: Iterable<VirtualFile>) {
    val fromChanges = changes.mapNotNull { getCurrentBranch(project, it) }.toSet()
    val fromUnversioned = unversioned.mapNotNull { getCurrentBranch(project, it) }.toSet()

    branches = fromChanges + fromUnversioned
    textIcon.setText(getText(branches))
    toolTipText = getTooltip(branches)
  }

  private fun getText(branches: Collection<BranchData>): String {
    val distinct = branches.distinctBy { it.branchName }
    return when (distinct.size) {
      0 -> ""
      1 -> getPresentableText(distinct.first())
      else -> "${getPresentableText(distinct.first())},..."
    }
  }

  private fun getTooltip(branches: Collection<BranchData>): String? {
    val distinct = branches.distinctBy { it.branchName to (it as? LinkedBranchData)?.linkedBranchName }
    return when (distinct.size) {
      0 -> null
      1 -> getSingleTooltip(distinct.first())
      else -> branches.joinToString("") { getMultiTooltip(it) }
    }
  }

  private fun getMultiTooltip(branch: BranchData): String {
    val linkedBranchPart = if (branch is LinkedBranchData && branch.branchName != null)
      branch.linkedBranchName?.let { " ${rightArrow()} $it" } ?: " (no tracking branch)"
    else ""

    return "<tr><td>${branch.presentableRootName}:</td><td>${getPresentableText(branch)}$linkedBranchPart</td></tr>"
  }

  companion object {
    @JvmField
    val TEXT_COLOR = JBColor(Color(0x7a7a7a), Color(0x909090))

    fun getCurrentBranch(project: Project, change: Change) = getProviders(project).asSequence().mapNotNull {
      it.getCurrentBranch(getFilePath(change))
    }.firstOrNull()

    fun getCurrentBranch(project: Project, file: VirtualFile) = getProviders(project).asSequence().mapNotNull {
      it.getCurrentBranch(getFilePath(file))
    }.firstOrNull()

    fun getPresentableText(branch: BranchData) = if (branch is LinkedBranchData) branch.branchName ?: "!"
    else branch.branchName.orEmpty()

    fun getSingleTooltip(branch: BranchData) = if (branch is LinkedBranchData && branch.branchName != null)
      branch.linkedBranchName?.let { "${branch.branchName} ${rightArrow()} $it" } ?: "No tracking branch"
    else null

    @JvmStatic
    fun getBranchPresentationBackground(background: Color) = ColorUtil.mix(background, BACKGROUND, BALANCE)

    private fun getProviders(project: Project) = BranchStateProvider.EP_NAME.getExtensionList(project)
  }
}