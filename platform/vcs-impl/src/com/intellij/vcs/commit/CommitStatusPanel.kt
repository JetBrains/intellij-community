// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.changes.ui.ChangeInfoCalculator
import com.intellij.openapi.vcs.changes.ui.CommitLegendPanel
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.components.BorderLayoutPanel

private val isCompactCommitLegend get() = Registry.get("vcs.non.modal.commit.legend.compact")

class CommitStatusPanel(private val commitWorkflowUi: CommitWorkflowUi) : BorderLayoutPanel(), InclusionListener {
  private val commitLegendCalculator = ChangeInfoCalculator()
  private val commitLegend = CommitLegendPanel(commitLegendCalculator).apply {
    component.myBorder = empty(0, 1)
    component.ipad = JBInsets.emptyInsets()
  }

  init {
    setupLegend()
    addToRight(commitLegend.component)

    commitWorkflowUi.addInclusionListener(this, commitWorkflowUi)
  }

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