// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.CommonBundle
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.impl.LaterInvocator
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.*
import com.intellij.openapi.vcs.changes.ui.LocalChangesBrowser.AllChanges
import com.intellij.openapi.vcs.changes.ui.LocalChangesBrowser.SelectedChangeLists
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.vcsUtil.RollbackUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JCheckBox
import javax.swing.JComponent
import javax.swing.JPanel

class RollbackChangesDialog private constructor(private val myProject: Project, private val myBrowser: LocalChangesBrowser)
  : DialogWrapper(myProject, true) {

  private val myInvokedFromModalContext: Boolean
  private val myDeleteLocallyAddedFiles: JCheckBox
  private val myInfoCalculator: ChangeInfoCalculator
  private val myCommitLegendPanel: CommitLegendPanel
  private val myListChangeListener: Runnable
  private val myOperationName: @Nls String?

  init {
    myInvokedFromModalContext = LaterInvocator.isInModalContext()
    myInfoCalculator = ChangeInfoCalculator()
    myCommitLegendPanel = CommitLegendPanel(myInfoCalculator)

    Disposer.register(disposable, myBrowser)
    val operationName = operationNameByChanges(myProject, myBrowser.allChanges)
    setOKButtonText(operationName)
    myOperationName = UIUtil.removeMnemonic(operationName)
    myBrowser.setToggleActionTitle(VcsBundle.message("changes.action.include.in.operation.name", StringUtil.toLowerCase(myOperationName)))
    title = VcsBundle.message("changes.action.rollback.custom.title", myOperationName)
    setCancelButtonText(CommonBundle.getCloseButtonText())
    myDeleteLocallyAddedFiles = JCheckBox(VcsBundle.message("changes.checkbox.delete.locally.added.files"))
    myDeleteLocallyAddedFiles.isSelected = PropertiesComponent.getInstance().isTrueValue(DELETE_LOCALLY_ADDED_FILES_KEY)
    myDeleteLocallyAddedFiles.addActionListener {
      PropertiesComponent.getInstance().setValue(DELETE_LOCALLY_ADDED_FILES_KEY, myDeleteLocallyAddedFiles.isSelected)
    }
    init()

    myListChangeListener = Runnable {
      val allChanges = myBrowser.allChanges
      val includedChanges: Collection<Change> = myBrowser.includedChanges
      myInfoCalculator.update(allChanges, ArrayList(includedChanges))
      myCommitLegendPanel.update()
      val hasNewFiles = ContainerUtil.exists(includedChanges) { change: Change -> change.type == Change.Type.NEW }
      myDeleteLocallyAddedFiles.isEnabled = hasNewFiles
    }
    myBrowser.setInclusionChangedListener(myListChangeListener)
    myListChangeListener.run()
  }

  override fun doOKAction() {
    super.doOKAction()
    val worker = RollbackWorker(myProject, myOperationName, myInvokedFromModalContext)
    worker.doRollback(myBrowser.includedChanges, myDeleteLocallyAddedFiles.isSelected)
  }

  override fun createCenterPanel(): JComponent {
    val panel = JPanel(GridBagLayout())
    val gb = GridBagConstraints(0, 0, 1, 1, 0.0, 0.0, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                JBUI.insets(1), 0, 0)
    gb.fill = GridBagConstraints.HORIZONTAL
    gb.weightx = 1.0
    val border = JPanel(BorderLayout())
    border.border = JBUI.Borders.emptyTop(2)
    border.add(myBrowser, BorderLayout.CENTER)
    gb.fill = GridBagConstraints.BOTH
    gb.weighty = 1.0
    ++gb.gridy
    panel.add(border, gb)
    val commitLegendPanel: JComponent = myCommitLegendPanel.component
    commitLegendPanel.border = JBUI.Borders.emptyLeft(4)
    gb.fill = GridBagConstraints.NONE
    gb.weightx = 0.0
    gb.weighty = 0.0
    ++gb.gridy
    panel.add(commitLegendPanel, gb)
    ++gb.gridy
    panel.add(myDeleteLocallyAddedFiles, gb)
    return panel
  }

  override fun getPreferredFocusedComponent(): JComponent {
    return myBrowser.preferredFocusedComponent
  }

  override fun getDimensionServiceKey(): String {
    return "RollbackChangesDialog"
  }

  companion object {
    const val DELETE_LOCALLY_ADDED_FILES_KEY = "delete.locally.added.files"

    @JvmStatic
    fun rollbackChanges(project: Project, changes: Collection<Change>) {
      val browser: LocalChangesBrowser
      val changeListManager = ChangeListManagerEx.getInstanceEx(project)
      browser = if (changeListManager.areChangeListsEnabled()) {
        val lists = changeListManager.getAffectedLists(changes)
        SelectedChangeLists(project, lists)
      }
      else {
        AllChanges(project)
      }
      browser.setIncludedChanges(changes)
      browser.viewer.resetTreeState() // set initial selection by included changes
      showRollbackDialog(project, browser)
    }

    @JvmStatic
    fun rollbackChanges(project: Project) {
      val browser: LocalChangesBrowser
      val changeListManager = ChangeListManager.getInstance(project)
      browser = if (changeListManager.areChangeListsEnabled()) {
        val lists = listOf(changeListManager.defaultChangeList)
        SelectedChangeLists(project, lists)
      }
      else {
        AllChanges(project)
      }
      showRollbackDialog(project, browser)
    }

    @JvmStatic
    fun rollbackChanges(project: Project, changeList: LocalChangeList) {
      val lists = listOf(changeList)
      val browser: LocalChangesBrowser = SelectedChangeLists(project, lists)
      showRollbackDialog(project, browser)
    }

    private fun showRollbackDialog(project: Project, browser: LocalChangesBrowser) {
      if (browser.allChanges.isEmpty()) {
        val operationName = UIUtil.removeMnemonic(RollbackUtil.getRollbackOperationName(project))
        Messages.showWarningDialog(project, VcsBundle.message("commit.dialog.no.changes.detected.text"),
                                   VcsBundle.message("changes.action.rollback.nothing", operationName))
        return
      }
      RollbackChangesDialog(project, browser).show()
    }

    @JvmStatic
    fun operationNameByChanges(project: Project, changes: Collection<Change>): @Nls(capitalization = Nls.Capitalization.Title) String {
      return RollbackUtil.getRollbackOperationName(ChangesUtil.getAffectedVcses(changes, project))
    }
  }
}