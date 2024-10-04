// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.pluginChooser

import com.intellij.CommonBundle
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingPage
import com.intellij.ide.startup.importSettings.chooser.ui.ProgressCommentLabel
import com.intellij.ide.startup.importSettings.chooser.ui.WizardController
import com.intellij.ide.startup.importSettings.data.PluginImportProgress
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.lifetime.intersect
import java.awt.*
import javax.swing.*

internal class WizardProgressPage(val progress: PluginImportProgress, val controller: WizardController) : OnboardingPage {
  override val stage: StartupWizardStage = StartupWizardStage.WizardProgressPage

  private val lifetime = controller.lifetime.createNested().intersect(this.createLifetime())

  private val icon = JLabel()
  private val msgLabel = ProgressCommentLabel("")
  private val jProgressBar = JProgressBar(0, 99).apply {
    preferredSize = Dimension(JBUI.scale (200), preferredSize.height)
  }

  private val pane = JPanel(VerticalLayout(JBUI.scale(28))).apply {
    add(JLabel(ImportSettingsBundle.message("install.plugins.page.title")).apply {
      font = Font(font.fontName, Font.PLAIN, JBUIScale.scaleFontSize(24f))
      horizontalAlignment = SwingConstants.CENTER
    })
    add(icon.apply {
      horizontalAlignment = SwingConstants.CENTER
    })
    add(JPanel(VerticalLayout(JBUIScale.scale(8))).apply {
      add(jProgressBar)
      add(msgLabel.label)
    })
  }

  override val content: JComponent = JPanel(GridBagLayout()).apply {
    preferredSize = JBDimension(640, 457)
    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.weightx = 1.0
    gbc.weighty = 1.0
    add(pane, gbc)
    border = JBUI.Borders.empty()
  }

  init {
    progress.progress.advise(lifetime) {
      jProgressBar.value = it
    }
    progress.progressMessage.advise(lifetime) {
      msgLabel.text = if (it != null) "<center>$it</center>" else "&nbsp"
    }

    progress.icon.advise(lifetime) {
      icon.icon = it
    }
  }

  override fun confirmExit(parentComponent: Component?): Boolean {
    return MessageDialogBuilder.yesNo(ImportSettingsBundle.message("exit.confirm.title"),
                                      ImportSettingsBundle.message("exit.confirm.prompt"))
      .yesText(ImportSettingsBundle.message("stop.import"))
      .noText(CommonBundle.getCancelButtonText())
      .asWarning()
      .ask(parentComponent)
  }
}