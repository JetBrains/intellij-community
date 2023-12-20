// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("HardCodedStringLiteral")

package com.intellij.ide.startup.importSettings

import com.intellij.ide.startup.importSettings.controllers.TransferSettingsListener
import com.intellij.ide.startup.importSettings.models.IdeVersion
import com.intellij.ide.startup.importSettings.models.Settings
import com.intellij.ide.startup.importSettings.models.TransferSettingsModel
import com.intellij.ide.startup.importSettings.providers.TransferSettingsPerformContext
import com.intellij.ide.startup.importSettings.providers.testProvider.TestTransferSettingsProvider
import com.intellij.ide.startup.importSettings.providers.vscode.VSCodeTransferSettingsProvider
import com.intellij.ide.startup.importSettings.providers.vsmac.VSMacTransferSettingsProvider
import com.intellij.ide.startup.importSettings.ui.TransferSettingsProgressIndicatorBase
import com.intellij.ide.startup.importSettings.ui.TransferSettingsView
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import javax.swing.*

/**
 * Internal demo action
 */
class TransferSettingsDemoAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    TransferSettingsDemoDialog(e.project!!).show()
  }
}

private class TransferSettingsDemoDialog(private val project: Project) : DialogWrapper(project) {
  private val config = DefaultTransferSettingsConfiguration(TransferSettingsDataProvider(TestTransferSettingsProvider(), VSCodeTransferSettingsProvider(), VSMacTransferSettingsProvider()), false)
  private val model: TransferSettingsModel = TransferSettingsModel(config, true)
  private val pnl = TransferSettingsView(config, model)

  init {
    init()
    setSize(640, 480)
  }

  override fun createCenterPanel(): JComponent {
    return JPanel().apply {
      layout = BoxLayout(this, BoxLayout.PAGE_AXIS)
      val status = JLabel("No yet status")
      val progressBar = JProgressBar(0, 100)
      val successOrFailureLabel = JLabel().apply { isVisible = false }
      val progressBase = TransferSettingsProgressIndicatorBase(progressBar, status, this)
      val btn = JButton("Import").apply {
        addActionListener {
          val selectedIde = pnl.selectedIde as? IdeVersion ?: error("Selected ide is null or not IdeVersion")
          config.controller.performImport(project, selectedIde, progressBase)
        }
      }

      config.controller.addListener(object : TransferSettingsListener {
        override fun importStarted(ideVersion: IdeVersion, settings: Settings) {
          close(0)
        }

        override fun importFailed(ideVersion: IdeVersion, settings: Settings, throwable: Throwable) {
          successOrFailureLabel.isVisible = true
          successOrFailureLabel.text = "Failed"
          btn.isEnabled = true
        }

        override fun importPerformed(ideVersion: IdeVersion, settings: Settings, context: TransferSettingsPerformContext) {
          successOrFailureLabel.isVisible = true
          successOrFailureLabel.text = "Success"
          progressBar.isVisible = false
        }
      })

      add(pnl.panel)
      add(status)
      add(successOrFailureLabel)
      add(progressBar)
      add(btn)
    }
  }
}