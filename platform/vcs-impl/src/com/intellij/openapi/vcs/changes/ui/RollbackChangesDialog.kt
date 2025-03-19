// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.CommonBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.LocalChangesBrowser.*
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.RollbackUtil
import org.jetbrains.annotations.Nls
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JTree

class RollbackChangesDialog private constructor(private val project: Project,
                                                private val browser: LocalChangesBrowser)
  : DialogWrapper(project, true) {

  private val changeInfoCalculator = ChangeInfoCalculator()
  private val commitLegend = CommitLegendPanel(changeInfoCalculator)

  private lateinit var deleteLocallyAddedFilesCheckBox: JCheckBox
  private val operationName: @Nls String

  private val isInvokedFromModalContext = LaterInvocator.isInModalContext()

  init {
    Disposer.register(disposable, browser)
    browser.setInclusionChangedListener { inclusionListener() }
    browser.viewer.addPropertyChangeListener(JTree.TREE_MODEL_PROPERTY) { inclusionListener() }

    val operationNameWithMnemonic = RollbackUtil.getRollbackOperationName(project)
    setOKButtonText(operationNameWithMnemonic)
    setCancelButtonText(CommonBundle.getCloseButtonText())

    operationName = UIUtil.removeMnemonic(operationNameWithMnemonic)
    browser.setToggleActionTitle(VcsBundle.message("changes.action.include.in.operation.name", StringUtil.toLowerCase(operationName)))
    title = VcsBundle.message("changes.action.rollback.custom.title", operationName)

    init()

    inclusionListener()
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row {
        cell(browser)
          .align(Align.FILL)
      }.resizableRow()
      row {
        cell(commitLegend.component)
      }
      row {
        deleteLocallyAddedFilesCheckBox = checkBox(VcsBundle.message("changes.checkbox.delete.locally.added.files"))
          .bindSelected({ PropertiesComponent.getInstance().isTrueValue(DELETE_LOCALLY_ADDED_FILES_KEY) },
                        { newValue -> PropertiesComponent.getInstance().setValue(DELETE_LOCALLY_ADDED_FILES_KEY, newValue) })
          .component
      }
    }.also {
      // Temporary workaround for IDEA-302779
      it.minimumSize = JBUI.size(200, 150)
    }
  }

  private fun inclusionListener() {
    val allChanges = browser.allChanges
    val includedChanges = browser.includedChanges
    changeInfoCalculator.update(allChanges, includedChanges.toList())
    commitLegend.update()

    val hasNewFiles = includedChanges.any { change -> change.type == Change.Type.NEW }
    deleteLocallyAddedFilesCheckBox.isEnabled = hasNewFiles

    okAction.isEnabled = includedChanges.isNotEmpty()
  }

  override fun doOKAction() {
    super.doOKAction()

    RollbackWorker(project, operationName, isInvokedFromModalContext)
      .doRollback(browser.includedChanges, deleteLocallyAddedFilesCheckBox.isSelected)
  }

  override fun getPreferredFocusedComponent(): JComponent = browser.preferredFocusedComponent

  override fun getDimensionServiceKey(): String = "RollbackChangesDialog"

  companion object {
    private const val DELETE_LOCALLY_ADDED_FILES_KEY = "delete.locally.added.files"

    @JvmStatic
    fun rollbackChanges(project: Project, changes: Collection<Change>) {
      val changeListManager = ChangeListManagerEx.getInstanceEx(project)
      val browser = if (changeListManager.areChangeListsEnabled()) {
        val lists = changeListManager.getAffectedLists(changes)
        when {
          lists.isNotEmpty() -> SelectedChangeLists(project, lists)
          else -> NonEmptyChangeLists(project)
        }
      }
      else {
        AllChanges(project)
      }
      browser.viewer.invokeAfterRefresh {
        // Set included changes when model is built.
        // This is important if 'changes' is non-ChangeListChange but tree has ChangeListChange
        browser.setIncludedChangesBy(changes)

        // set initial selection by included changes
        browser.viewer.resetTreeState()
      }
      showRollbackDialog(project, browser)
    }

    @JvmStatic
    fun rollbackChanges(project: Project) {
      val changeListManager = ChangeListManager.getInstance(project)
      val browser = if (changeListManager.areChangeListsEnabled()) {
        val defaultChangeList = changeListManager.defaultChangeList
        when {
          defaultChangeList.changes.isNotEmpty() -> SelectedChangeLists(project, listOf(defaultChangeList))
          else -> NonEmptyChangeLists(project)
        }
      }
      else {
        AllChanges(project)
      }
      showRollbackDialog(project, browser)
    }

    @JvmStatic
    fun rollbackChanges(project: Project, changeList: LocalChangeList) {
      val lists = listOf(changeList)
      val browser = SelectedChangeLists(project, lists)
      showRollbackDialog(project, browser)
    }

    private fun showRollbackDialog(project: Project, browser: LocalChangesBrowser) {
      RollbackChangesDialog(project, browser).show()
    }

    @JvmStatic
    fun operationNameByChanges(project: Project, changes: Collection<Change>): @Nls(capitalization = Nls.Capitalization.Title) String {
      return RollbackUtil.getRollbackOperationName(ChangesUtil.getAffectedVcses(changes, project))
    }
  }
}