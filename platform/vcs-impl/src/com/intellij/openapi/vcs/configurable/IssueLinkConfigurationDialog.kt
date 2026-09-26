// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.configurable

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.vcs.IssueNavigationConfiguration
import com.intellij.openapi.vcs.IssueNavigationLink
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.ui.DocumentAdapter
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBTextField
import com.intellij.ui.dsl.builder.AlignX
import com.intellij.ui.dsl.builder.COLUMNS_MEDIUM
import com.intellij.ui.dsl.builder.columns
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.containers.ContainerUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.event.DocumentEvent

@ApiStatus.Internal
class IssueLinkConfigurationDialog internal constructor(project: Project) : DialogWrapper(project, false) {
  private val issueIDTextField = JBTextField()
  private val issueLinkTextField = JBTextField()
  private val exampleIssueIDTextField = JBTextField()
  private val exampleIssueLinkTextField = JBTextField().apply { isEditable = false }
  private val errorLabel = JBLabel(" ").apply { foreground = JBColor.RED }

  var link: IssueNavigationLink
    get() = IssueNavigationLink(issueIDTextField.text, issueLinkTextField.text)
    set(value) {
      issueIDTextField.text = value.issueRegexp
      issueLinkTextField.text = value.linkRegexp
    }

  init {
    init()
    val documentChangeListener = object : DocumentAdapter() {
      override fun textChanged(e: DocumentEvent) {
        updateFeedback()
      }
    }
    issueIDTextField.document.addDocumentListener(documentChangeListener)
    issueLinkTextField.document.addDocumentListener(documentChangeListener)
    exampleIssueIDTextField.document.addDocumentListener(documentChangeListener)

    issueIDTextField.text = "Task_([A-Za-z]+)_(\\d+)" //NON-NLS // placeholder
    issueLinkTextField.text = "https://example.com/issue/\$1/\$2" //NON-NLS // placeholder
    exampleIssueIDTextField.text = "Task_DA_113" //NON-NLS // placeholder
  }

  override fun createCenterPanel(): JComponent {
    return panel {
      row(VcsBundle.message("add.issue.dialog.issue.id.regular.expression")) {
        cell(issueIDTextField)
          .columns(COLUMNS_MEDIUM)
          .align(AlignX.FILL)
      }
      row(VcsBundle.message("add.issue.dialog.issue.link.replacement.expression")) {
        cell(issueLinkTextField)
          .columns(COLUMNS_MEDIUM)
          .align(AlignX.FILL)
      }
      group(VcsBundle.message("add.issue.dialog.issue.example.border.title")) {
        row(VcsBundle.message("add.issue.dialog.issue.id.label")) {
          cell(exampleIssueIDTextField)
            .align(AlignX.FILL)
        }
        row(VcsBundle.message("add.issue.dialog.issue.link.label")) {
          cell(exampleIssueLinkTextField)
            .align(AlignX.FILL)
        }
      }
      row {
        cell(errorLabel)
      }
    }
  }

  override fun getHelpId(): String = "reference.settings.vcs.issue.navigation.add.link"

  override fun getPreferredFocusedComponent(): JComponent = issueIDTextField

  private fun updateFeedback() {
    errorLabel.text = " "
    try {
      if (issueIDTextField.text.isNotEmpty()) {
        val matches = ArrayList<IssueNavigationConfiguration.LinkMatch>()
        IssueNavigationConfiguration.findIssueLinkMatches(exampleIssueIDTextField.text, link, matches)
        val firstMatch = ContainerUtil.getFirstItem(matches)
        if (firstMatch != null) {
          exampleIssueLinkTextField.text = firstMatch.targetUrl
        }
        else {
          exampleIssueLinkTextField.text = VcsBundle.message("add.issue.dialog.issue.no.match")
        }
      }
    }
    catch (ex: Exception) {
      errorLabel.text = VcsBundle.message("add.issue.dialog.invalid.regular.expression", ex.message)
      exampleIssueLinkTextField.text = ""
    }
    isOKActionEnabled = errorLabel.text == " "
  }
}
