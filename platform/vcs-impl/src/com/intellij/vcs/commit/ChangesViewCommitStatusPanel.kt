// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.changes.InclusionListener
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.ChangesViewContentManager.Companion.LOCAL_CHANGES
import com.intellij.ui.content.Content
import com.intellij.util.ui.JBUI.Borders.empty
import com.intellij.util.ui.JBUI.Borders.emptyRight
import com.intellij.util.ui.JBUI.emptyInsets
import com.intellij.util.ui.components.BorderLayoutPanel
import com.intellij.util.ui.update.UiNotifyConnector.doWhenFirstShown

private val isCompactCommitLegend get() = Registry.get("vcs.non.modal.commit.legend.compact")

private fun Project.getLocalChangesTab(): Content? =
  ChangesViewContentManager.getInstance(this).findContents { it.tabName == LOCAL_CHANGES }.firstOrNull()

internal class ChangesViewCommitStatusPanel(tree: ChangesTree, private val commitWorkflowUi: CommitWorkflowUi) :
  BorderLayoutPanel(), ChangesViewContentManagerListener, InclusionListener {

  private val branchComponent = CurrentBranchComponent(tree.project, tree, commitWorkflowUi)

  private val commitLegendCalculator = ChangeInfoCalculator()
  private val commitLegend = CommitLegendPanel(commitLegendCalculator).apply {
    component.myBorder = empty(0, 1)
    component.ipad = emptyInsets()
  }

  private val project get() = branchComponent.project

  init {
    setupLegend()

    addToRight(commitLegend.component)
    border = emptyRight(6)
    background = tree.background

    commitWorkflowUi.addInclusionListener(this, commitWorkflowUi)
    setupTabUpdater()
  }

  private fun setupTabUpdater() {
    doWhenFirstShown(this) { updateTab() } // as UI components could be created before tool window `Content`

    branchComponent.addChangeListener(this::updateTab, commitWorkflowUi)
    project.messageBus.connect(commitWorkflowUi).subscribe(ChangesViewContentManagerListener.TOPIC, this)

    Disposer.register(commitWorkflowUi) {
      val tab = project.getLocalChangesTab() ?: return@register

      tab.displayName = message("local.changes.tab")
      tab.description = null
    }
  }

  override fun toolWindowMappingChanged() = updateTab()

  private fun updateTab() {
    if (!project.isCommitToolWindow) return
    val tab = project.getLocalChangesTab() ?: return

    val branch = branchComponent.text
    tab.displayName = if (branch?.isNotBlank() == true) message("tab.title.commit.to.branch", branch) else message("tab.title.commit")
    tab.description = branchComponent.toolTipText
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