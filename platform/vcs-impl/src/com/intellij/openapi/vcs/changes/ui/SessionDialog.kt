// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.ValidationInfo
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.CommitSession
import com.intellij.openapi.wm.ex.IdeFocusTraversalPolicy
import com.intellij.vcs.commit.removeEllipsisSuffix
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import javax.swing.JComponent
import javax.swing.JPanel

class SessionDialog @JvmOverloads constructor(
  title: @Nls String,
  project: Project,
  private val mySession: CommitSession,
  private val myChanges: List<Change>,
  private val myCommitMessage: String?,
  private val myConfigurationComponent: JComponent? = mySession.getAdditionalConfigurationUI(myChanges, myCommitMessage)
) : DialogWrapper(project, true) {

  private val myCenterPanel = JPanel(BorderLayout())

  init {
    val configurationComponentName = myConfigurationComponent?.getClientProperty(VCS_CONFIGURATION_UI_TITLE) as? String
    setTitle(if (configurationComponentName.isNullOrBlank()) title.removeEllipsisSuffix() else configurationComponentName)
    init()
    initValidation()
  }

  override fun createCenterPanel(): JComponent? {
    myConfigurationComponent?.let { myCenterPanel.add(it, BorderLayout.CENTER) }
    return myCenterPanel
  }

  override fun getPreferredFocusedComponent(): JComponent? =
    myConfigurationComponent?.let { IdeFocusTraversalPolicy.getPreferredFocusedComponent(it) }

  override fun doValidate(): ValidationInfo? {
    isOKActionEnabled = mySession.canExecute(myChanges, myCommitMessage)
    return mySession.validateFields()
  }

  override fun getHelpId(): String? = mySession.helpId

  companion object {
    @NonNls
    const val VCS_CONFIGURATION_UI_TITLE = "Vcs.SessionDialog.title"
  }
}
