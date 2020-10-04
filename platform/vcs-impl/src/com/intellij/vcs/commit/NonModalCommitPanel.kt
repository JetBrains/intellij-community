// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.ComponentContainer
import com.intellij.openapi.vcs.VcsBundle.message
import com.intellij.openapi.vcs.checkin.CheckinHandler
import com.intellij.openapi.vcs.ui.CommitMessage
import com.intellij.util.ui.JBUI.Borders.emptyLeft
import com.intellij.util.ui.components.BorderLayoutPanel
import javax.swing.JComponent

abstract class NonModalCommitPanel(
  val project: Project,
  val commitActionsPanel: CommitActionsPanel = CommitActionsPanel()
) : BorderLayoutPanel(),
    NonModalCommitWorkflowUi,
    CommitActionsUi by commitActionsPanel,
    ComponentContainer {

  private val dataProviders = mutableListOf<DataProvider>()

  val commitMessage = CommitMessage(project, false, false, true).apply {
    editorField.addSettingsProvider { it.setBorder(emptyLeft(6)) }
    editorField.setPlaceholder(message("commit.message.placeholder"))
  }

  override val commitMessageUi: CommitMessageUi get() = commitMessage

  override fun getComponent(): JComponent = this
  override fun getPreferredFocusableComponent(): JComponent = commitMessage.editorField

  override fun getData(dataId: String) = getDataFromProviders(dataId) ?: commitMessage.getData(dataId)
  fun getDataFromProviders(dataId: String) = dataProviders.asSequence().mapNotNull { it.getData(dataId) }.firstOrNull()

  override fun addDataProvider(provider: DataProvider) {
    dataProviders += provider
  }

  override fun startBeforeCommitChecks() = Unit
  override fun endBeforeCommitChecks(result: CheckinHandler.ReturnResult) = Unit

  override fun dispose() = Unit
}