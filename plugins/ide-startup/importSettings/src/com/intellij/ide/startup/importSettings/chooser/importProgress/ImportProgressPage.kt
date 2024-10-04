// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.importProgress

import com.intellij.CommonBundle
import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.ImportSettingsController
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingPage
import com.intellij.ide.startup.importSettings.chooser.ui.ProgressCommentLabel
import com.intellij.ide.startup.importSettings.chooser.ui.ProgressLabel
import com.intellij.ide.startup.importSettings.data.DialogImportData
import com.intellij.ide.startup.importSettings.data.ImportFromProduct
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.NlsContexts.DialogTitle
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.lifetime.intersect
import java.awt.Component
import java.awt.Dimension
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.*

internal class ImportProgressPage(
  importFromProduct: DialogImportData,
  controller: ImportSettingsController,
  importTitleOverride:  @DialogTitle String?
) : OnboardingPage {

  override val stage = StartupWizardStage.ImportProgressPage

  private val lifetime = controller.lifetime.createNested().intersect(this.createLifetime())

  override fun confirmExit(parentComponent: Component?): Boolean {
    return MessageDialogBuilder.yesNo(ImportSettingsBundle.message("exit.confirm.title"),
                                      ImportSettingsBundle.message("exit.confirm.prompt"))
      .yesText(ImportSettingsBundle.message("stop.import"))
      .noText(CommonBundle.getCancelButtonText())
      .asWarning()
      .ask(parentComponent)
  }


  private val panel = JPanel(VerticalLayout(JBUI.scale(8))).apply {
    add(JPanel(VerticalLayout(JBUI.scale(8))).apply {
      add(JLabel(importTitleOverride ?: ImportSettingsBundle.message("import.settings.title")).apply {
        font = JBFont.h1()
        horizontalAlignment = SwingConstants.CENTER
      })

      importFromProduct.message?.let {
        add(JLabel(it).apply {
          horizontalAlignment = SwingConstants.CENTER
        })
      }

      isOpaque = false
      border = JBUI.Borders.empty(30, 0, 20, 0)
    })


    if (importFromProduct is ImportFromProduct) {
      val from = importFromProduct.from
      val to = importFromProduct.to

      add(JPanel(GridBagLayout()).apply {
        val cn = GridBagConstraints()
        cn.fill = GridBagConstraints.HORIZONTAL

        cn.weightx = 1.0
        cn.gridx = 0
        cn.gridy = 0
        add(JLabel(from.icon), cn)

        cn.gridx = 1
        cn.gridy = 0
        cn.weightx = 0.0
        add(JLabel(AllIcons.Chooser.Right), cn)

        cn.weightx = 1.0
        cn.gridx = 2
        cn.gridy = 0
        add(JLabel(to.icon), cn)

        cn.gridx = 0
        cn.gridy = 1
        add(ProgressLabel(from.item.name).label, cn)

        cn.gridx = 2
        cn.gridy = 1
        add(ProgressLabel(to.item.name).label, cn)

        border = JBUI.Borders.emptyBottom(18)

      })
    }

    add(JPanel(VerticalLayout(JBUI.scale(8)).apply {
      val hLabel = ProgressCommentLabel("")

      add(JProgressBar(0, 99).apply {
        importFromProduct.progress.progress.advise(lifetime) {
          this.value = it
        }
        importFromProduct.progress.progressMessage.advise(lifetime) {
          hLabel.text = if (it != null) "<center>$it</center>" else "&nbsp"
        }

        preferredSize = Dimension(JBUI.scale (280), preferredSize.height)
      })


      add(hLabel.label.apply {
        foreground = JBUI.CurrentTheme.ContextHelp.FOREGROUND
      })
    }).apply {
      isOpaque = false
      border = JBUI.Borders.emptyTop(20)
    })

    border = JBUI.Borders.empty()
  }

  private val contentPage = JPanel(GridBagLayout()).apply {
    preferredSize = JBDimension(640, 457)
    val gbc = GridBagConstraints()
    gbc.gridx = 0
    gbc.gridy = 0
    gbc.weightx = 1.0
    gbc.weighty = 1.0
    add(panel, gbc)
    border = JBUI.Borders.empty()
  }

  override val content: JComponent = contentPage
}