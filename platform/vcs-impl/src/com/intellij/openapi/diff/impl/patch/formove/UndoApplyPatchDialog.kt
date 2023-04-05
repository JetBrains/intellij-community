// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.diff.impl.patch.formove

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ui.ChangesTree
import com.intellij.openapi.vcs.changes.ui.ChangesTreeImpl
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.UIUtil
import com.intellij.xml.util.XmlStringUtil
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

internal class UndoApplyPatchDialog(private val myProject: Project,
                                    filePaths: List<FilePath>,
                                    shouldInformAboutBinaries: Boolean) : DialogWrapper(myProject, true) {
  private val myFailedFilePaths: List<FilePath>
  private val myShouldInformAboutBinaries: Boolean

  init {
    title = VcsBundle.message("patch.apply.partly.failed.title")
    setOKButtonText(VcsBundle.message("patch.apply.rollback.action"))
    myFailedFilePaths = filePaths
    myShouldInformAboutBinaries = shouldInformAboutBinaries
    init()
  }

  override fun createCenterPanel(): JComponent? {
    val panel = JPanel(BorderLayout())
    val numFiles = myFailedFilePaths.size
    val labelsPanel = JPanel(BorderLayout())
    val infoLabel: JLabel = JBLabel(XmlStringUtil.wrapInHtml(VcsBundle.message("patch.apply.rollback.prompt", numFiles)))
    labelsPanel.add(infoLabel, BorderLayout.NORTH)
    if (myShouldInformAboutBinaries) {
      val warningLabel = JLabel(VcsBundle.message("patch.apply.rollback.will.not.affect.binaries.info"))
      warningLabel.icon = UIUtil.getBalloonWarningIcon()
      labelsPanel.add(warningLabel, BorderLayout.CENTER)
    }
    panel.add(labelsPanel, BorderLayout.NORTH)
    if (numFiles > 0) {
      val browser: ChangesTree = ChangesTreeImpl.FilePaths(myProject, false, false, myFailedFilePaths)
      panel.add(ScrollPaneFactory.createScrollPane(browser), BorderLayout.CENTER)
    }
    return panel
  }
}
