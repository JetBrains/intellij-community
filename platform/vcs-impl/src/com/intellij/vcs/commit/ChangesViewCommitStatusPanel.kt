// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.changes.ui.ChangeInfoCalculator
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.CommitLegendPanel
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent
import com.intellij.openapi.vcs.changes.ui.CurrentBranchComponent.Companion.getBranchPresentationBackground
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBPanel
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.emptyInsets
import com.intellij.util.ui.JBUI.emptySize
import java.awt.Dimension

private val isCompactCommitLegend get() = Registry.get("vcs.non.modal.commit.legend.compact")

internal class ChangesViewCommitStatusPanel(tree: ChangesTree, private val commitWorkflowUi: CommitWorkflowUi, bgColor: JBColor) :
  JBPanel<ChangesViewCommitStatusPanel>(HorizontalLayout(10)),
  InclusionListener {

  private val branchComponent = CurrentBranchComponent(tree.project, tree, commitWorkflowUi).apply {
    icon = null
    isOpaque = true
    background = JBColor { getBranchPresentationBackground(bgColor) }
    border = empty(0, 4)
  }

  private val commitLegendCalculator = ChangeInfoCalculator()
  private val commitLegend = CommitLegendPanel(commitLegendCalculator).apply {
    component.myBorder = empty(0, 1)
    component.ipad = emptyInsets()
  }

  init {
    setupLegend()

    add(branchComponent)
    add(commitLegend.component)
    border = empty(6)
    background = bgColor

    commitWorkflowUi.addInclusionListener(this, commitWorkflowUi)
  }

  override fun getPreferredSize(): Dimension? =
    if (branchComponent.isVisible || commitLegend.component.isVisible) super.getPreferredSize() else emptySize()

  override fun inclusionChanged() = updateLegend()

  private fun setupLegend() {
    setLegendCompact()
    isCompactCommitLegend.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) = setLegendCompact()
    }, commitWorkflowUi)
  }

  private fun setLegendCompact() {
    commitLegend.isCompact = isCompactCommitLegend.asBoolean()
  }

  private fun updateLegend() {
    // Displayed changes and unversioned files are not actually used in legend - so we don't pass them
    commitLegendCalculator.update(
      includedChanges = commitWorkflowUi.getIncludedChanges(),
      includedUnversionedFilesCount = commitWorkflowUi.getIncludedUnversionedFiles().size
    )
    commitLegend.update()
  }
}