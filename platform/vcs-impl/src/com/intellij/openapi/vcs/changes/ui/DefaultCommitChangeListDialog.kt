// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.emptyRight
import com.intellij.util.ui.UIUtil.addBorder
import java.awt.Dimension

class DefaultCommitChangeListDialog(workflow: DialogCommitWorkflow) : CommitChangeListDialog(workflow) {
  private val browser =
    object : MultipleLocalChangeListsBrowser(project, true, true, workflow.isDefaultCommitEnabled, workflow.isPartialCommitEnabled) {
      override fun createAdditionalRollbackActions() = workflow.affectedVcses.mapNotNull { it.rollbackEnvironment }.flatMap { it.createCustomRollbackActions() }
    }

  init {
    LineStatusTrackerManager.getInstanceImpl(project).resetExcludedFromCommitMarkers()

    val branchComponent = CurrentBranchComponent(project, browser)
    addBorder(branchComponent, emptyRight(16))
    browserBottomPanel.add(branchComponent)

    val initialChangeList = workflow.initialChangeList
    if (initialChangeList != null) browser.selectedChangeList = initialChangeList
    browser.viewer.setIncludedChanges(workflow.initiallyIncluded)
    browser.viewer.rebuildTree()
    browser.viewer.setKeepTreeState(true)

    val commitMessageEditor = DiffCommitMessageEditor(project, commitMessageComponent)
    browser.setBottomDiffComponent(commitMessageEditor)

    browser.setSelectedListChangeListener { selectedChangeListChanged() }
  }

  override fun getBrowser(): CommitDialogChangesBrowser = browser
}

private class DiffCommitMessageEditor(project: Project, commitMessage: CommitMessage) : CommitMessage(project) {
  init {
    editorField.document = commitMessage.editorField.document
  }

  // we don't want to be squeezed to one line
  override fun getPreferredSize(): Dimension = JBUI.size(400, 120)
}