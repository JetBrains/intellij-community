// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.options.ConfigurableUi
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.ui.components.JBCheckBox
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI.PanelFactory.grid
import com.intellij.util.ui.UI.PanelFactory.panel
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.api.GithubApiTaskExecutor
import org.jetbrains.plugins.github.authentication.accounts.GithubAccountInformationProvider
import org.jetbrains.plugins.github.authentication.ui.GithubAccountsPanel
import org.jetbrains.plugins.github.util.GithubSettings
import java.awt.Component.LEFT_ALIGNMENT
import java.awt.GridLayout
import java.text.NumberFormat
import javax.swing.*
import javax.swing.text.NumberFormatter

class GithubSettingsPanel(project: Project,
                          apiTaskExecutor: GithubApiTaskExecutor,
                          accountInformationProvider: GithubAccountInformationProvider)
  : ConfigurableUi<GithubSettingsConfigurable.GithubSettingsHolder>, Disposable {
  private val accountsPanel = GithubAccountsPanel(project, apiTaskExecutor, accountInformationProvider)
  private val timeoutField = JFormattedTextField(NumberFormatter(NumberFormat.getIntegerInstance()).apply {
    minimum = 0
    maximum = 60
  }).apply {
    columns = 2
    UIUtil.fixFormattedField(this)
  }
  private val cloneUsingSshCheckBox = JBCheckBox("Clone git repositories using ssh")

  override fun reset(settings: GithubSettingsConfigurable.GithubSettingsHolder) {
    accountsPanel.setAccounts(settings.applicationAccounts.accounts, settings.projectAccount.account)
    accountsPanel.clearNewTokens()
    accountsPanel.loadExistingAccountsDetails()
    timeoutField.value = settings.application.getConnectionTimeoutSeconds()
    cloneUsingSshCheckBox.isSelected = settings.application.isCloneGitUsingSsh
  }

  override fun isModified(settings: GithubSettingsConfigurable.GithubSettingsHolder): Boolean {
    return timeoutField.value != settings.application.getConnectionTimeoutSeconds() ||
           cloneUsingSshCheckBox.isSelected != settings.application.isCloneGitUsingSsh ||
           accountsPanel.isModified(settings.applicationAccounts.accounts, settings.projectAccount.account)
  }

  override fun apply(settings: GithubSettingsConfigurable.GithubSettingsHolder) {
    val (accountsTokenMap, defaultAccount) = accountsPanel.getAccounts()
    settings.applicationAccounts.accounts = accountsTokenMap.keys
    accountsTokenMap.filterValues { it != null }.forEach(settings.applicationAccounts::updateAccountToken)
    settings.projectAccount.account = defaultAccount
    accountsPanel.clearNewTokens()
    settings.application.setConnectionTimeoutSeconds(timeoutField.value as Int)
    settings.application.isCloneGitUsingSsh = cloneUsingSshCheckBox.isSelected
  }

  private fun GithubSettings.getConnectionTimeoutSeconds(): Int {
    return connectionTimeout / 1000
  }

  private fun GithubSettings.setConnectionTimeoutSeconds(timeout: Int) {
    connectionTimeout = timeout * 1000
  }

  override fun getComponent(): JComponent {
    val timeoutPanel = JPanel().apply {
      layout = BoxLayout(this, BoxLayout.X_AXIS)
      add(JLabel("Connection timeout:"))
      add(Box.createRigidArea(JBDimension(UIUtil.DEFAULT_HGAP, 0)))
      add(timeoutField)
      add(Box.createRigidArea(JBDimension(UIUtil.DEFAULT_HGAP, 0)))
      add(JLabel("seconds"))
      alignmentX = LEFT_ALIGNMENT
    }

    val settingsPanel = grid()
      .add(panel(cloneUsingSshCheckBox.apply { alignmentX = LEFT_ALIGNMENT }))
      .add(panel(timeoutPanel).resizeX(false))
      .createPanel()
      .apply {
        border = JBUI.Borders.empty(UIUtil.DEFAULT_VGAP, UIUtil.DEFAULT_HGAP)
      }

    return JPanel().apply {
      layout = GridLayout(2, 1)
      add(accountsPanel)
      add(settingsPanel)
    }
  }

  override fun dispose() {
    Disposer.dispose(accountsPanel)
  }
}
