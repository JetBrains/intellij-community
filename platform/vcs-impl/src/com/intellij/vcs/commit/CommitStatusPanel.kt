// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.commit

import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.changes.ui.ChangeInfoCalculator
import com.intellij.openapi.vcs.changes.ui.CommitLegendComponent
import com.intellij.util.ui.JBInsets
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent


private val isCompactCommitLegend get() = Registry.get("vcs.non.modal.commit.legend.compact")

class CommitStatusPanel(private val commitWorkflowUi: CommitWorkflowUi) : BorderLayoutPanel(), InclusionListener {
  private val commitLegendCalculator = ChangeInfoCalculator()
  private val commitLegend = CommitLegendComponent.create(commitLegendCalculator).apply {
    component.myBorder = empty(0, 1)
    component.ipad = JBInsets.emptyInsets()
  }

  private val commitLegendWrapperPanel = BorderLayoutPanel().apply { isOpaque = false }

  init {
    isOpaque = false

    commitLegendWrapperPanel.addToRight(commitLegend.component)

    commitLegendWrapperPanel.addComponentListener(object : ComponentAdapter() {
      override fun componentResized(e: ComponentEvent) {
        updateLegend()
      }
    })

    setupLegend()
    addToCenter(commitLegendWrapperPanel)

    commitWorkflowUi.addInclusionListener(this, commitWorkflowUi)
  }

  override fun inclusionChanged() = updateLegend()

  private fun setupLegend() {
    isCompactCommitLegend.addListener(object : RegistryValueListener {
      override fun afterValueChanged(value: RegistryValue) = updateLegend()
    }, commitWorkflowUi)
  }

  private fun updateLegend() {
    // Displayed changes and unversioned files are not actually used in legend - so we don't pass them
    commitLegendCalculator.update(
      includedChanges = commitWorkflowUi.getIncludedChanges(),
      includedUnversionedFilesCount = commitWorkflowUi.getIncludedUnversionedFiles().size
    )
    commitLegend.update()
    adjustLegendToFitPanel()
  }

  private fun adjustLegendToFitPanel() {
    val availableWidth = commitLegendWrapperPanel.width
    if (availableWidth <= 0) return

    val forceCompact = isCompactCommitLegend.asBoolean()
    if (forceCompact) {
      commitLegend.isCompact = true
      return
    }

    val fullLegendString = commitLegend.getFullLegendString()
    val fm = commitLegend.component.getFontMetrics(commitLegend.component.font)
    val legendWidth = fm.getStringBounds(fullLegendString, null).width

    commitLegend.isCompact = legendWidth > availableWidth
  }
}