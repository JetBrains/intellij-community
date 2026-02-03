// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

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
import javax.swing.JComponent

class SessionDialog(
  title: @Nls String,
  project: Project,
  private val mySession: CommitSession,
  private val myChanges: List<Change>,
  private val myCommitMessage: String?,
  private val myConfigurationComponent: JComponent
) : DialogWrapper(project, true) {

  init {
    val configurationComponentName = myConfigurationComponent.getClientProperty(VCS_CONFIGURATION_UI_TITLE) as? String
    setTitle(if (configurationComponentName.isNullOrBlank()) title.removeEllipsisSuffix() else configurationComponentName)
    init()
    initValidation()
  }

  override fun createCenterPanel(): JComponent {
    return myConfigurationComponent
  }

  override fun getPreferredFocusedComponent(): JComponent? =
    IdeFocusTraversalPolicy.getPreferredFocusedComponent(myConfigurationComponent)

  override fun doValidate(): ValidationInfo? {
    isOKActionEnabled = mySession.canExecute(myChanges, myCommitMessage)
    return mySession.validateFields()
  }

  override fun getHelpId(): String? = mySession.helpId

  companion object {
    /**
     * return true if commit operation should proceed
     */
    @JvmStatic
    fun configureCommitSession(project: Project,
                               title: @Nls String,
                               commitSession: CommitSession,
                               changes: List<Change>,
                               commitMessage: String?): Boolean {
      val configurationUI = commitSession.getAdditionalConfigurationUI(changes, commitMessage) ?: return true
      val sessionDialog = SessionDialog(title, project, commitSession, changes, commitMessage, configurationUI)
      if (sessionDialog.showAndGet()) {
        return true
      }
      else {
        commitSession.executionCanceled()
        return false
      }
    }

    @NonNls
    const val VCS_CONFIGURATION_UI_TITLE = "Vcs.SessionDialog.title"
  }
}
