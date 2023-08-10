// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.util.EventDispatcher
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.JBUI.Borders.emptyRight
import com.intellij.util.ui.JBUI.Panels.simplePanel
import com.intellij.util.ui.UIUtil.addBorder
import com.intellij.util.ui.UIUtil.getRegularPanelInsets
import com.intellij.vcs.commit.SingleChangeListCommitWorkflow
import com.intellij.vcs.commit.SingleChangeListCommitWorkflowUi
import com.intellij.vcs.commit.getDisplayedPaths
import java.awt.Dimension
import javax.swing.JComponent
import javax.swing.border.EmptyBorder

class DefaultCommitChangeListDialog(workflow: SingleChangeListCommitWorkflow) : CommitChangeListDialog(workflow) {
  private val changeListEventDispatcher = EventDispatcher.create(SingleChangeListCommitWorkflowUi.ChangeListListener::class.java)

  private val browser = MultipleLocalChangeListsBrowser(project, workflow.vcses, true, true,
                                                        workflow.isDefaultCommitEnabled, workflow.isPartialCommitEnabled)

  init {
    LineStatusTrackerManager.getInstanceImpl(project).resetExcludedFromCommitMarkers()

    val branchComponent = CurrentBranchComponent(browser.viewer, pathsProvider = { getDisplayedPaths() })
    Disposer.register(this, branchComponent)

    addBorder(branchComponent, emptyRight(16))
    browserBottomPanel.add(branchComponent)

    val initialChangeList = workflow.initialChangeList
    if (initialChangeList != null) browser.selectedChangeList = initialChangeList
    browser.viewer.setIncludedChanges(workflow.initiallyIncluded)
    browser.viewer.rebuildTree()
    browser.viewer.setKeepTreeState(true)

    browser.setBottomDiffComponent {
      DiffCommitMessageEditor(project, commitMessageComponent).also { editor ->
        Disposer.register(this, editor)
      }
    }

    browser.setSelectedListChangeListener(changeListEventDispatcher.multicaster)

    addChangeListListener(object : SingleChangeListCommitWorkflowUi.ChangeListListener {
      override fun changeListChanged(oldChangeList: LocalChangeList, newChangeList: LocalChangeList) {
        this@DefaultCommitChangeListDialog.changeListChanged()
      }
    }, this)
  }

  override fun createCenterPanel(): JComponent =
    simplePanel(super.createCenterPanel()).apply {
      putClientProperty(IS_VISUAL_PADDING_COMPENSATED_ON_COMPONENT_LEVEL_KEY, false)

      val insets = getRegularPanelInsets()
      border = EmptyBorder(insets.top, insets.left, 0, insets.right)
    }

  override fun getBrowser(): CommitDialogChangesBrowser = browser

  override fun addChangeListListener(listener: SingleChangeListCommitWorkflowUi.ChangeListListener, parent: Disposable) =
    changeListEventDispatcher.addListener(listener, parent)

  private fun changeListChanged() {
    commitMessageComponent.setChangesSupplier(ChangeListChangesSupplier(getChangeList()))
    updateWarning()
  }
}

private class DiffCommitMessageEditor(project: Project, commitMessage: CommitMessage) : CommitMessage(project) {
  init {
    editorField.document = commitMessage.editorField.document
  }

  // we don't want to be squeezed to one line
  override fun getPreferredSize(): Dimension = JBUI.size(400, 120)
}