// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.pluginChooser

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingPage
import com.intellij.ide.startup.importSettings.chooser.ui.WizardController
import com.intellij.ide.startup.importSettings.chooser.ui.WizardPagePane
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.*


class WizardPluginsPage(val controller: WizardController) : OnboardingPage {

  private val pluginService = controller.service.getPluginService()

  override val stage: StartupWizardStage = StartupWizardStage.WizardPluginPage

  private val pluginPanes = mutableListOf<WizardPluginPane>()

  private val contentPage: JComponent

  private val leftLabel = JLabel().apply {
    border = JBUI.Borders.emptyLeft(26)
    foreground = UIUtil.getContextHelpForeground()
  }
  override fun confirmExit(parentComponent: Component?): Boolean = true

  private fun getSelected(): List<WizardPluginPane> {
    return pluginPanes.filter { it.selected }.toList()
  }
  private fun changeHandler() {
    val selected = getSelected()
    when(selected.size) {
      0 -> {
        leftLabel.text = ImportSettingsBundle.message("plugins.page.choose.counter.no")
        continueAction.text = ImportSettingsBundle.message("plugins.page.ok.button.continue.without")
      }
      1 -> {
        leftLabel.text = ImportSettingsBundle.message("plugins.page.choose.counter.one")
        continueAction.text = ImportSettingsBundle.message("plugins.page.ok.button.install")
      }
      else -> {
        leftLabel.text = ImportSettingsBundle.message("plugins.page.choose.counter.multiple", selected.size)
        continueAction.text = ImportSettingsBundle.message("plugins.page.ok.button.install")
      }
    }
  }

  private val pane = BorderLayoutPanel().apply {
    isOpaque = false
    addToTop(JLabel(ImportSettingsBundle.message("plugins.page.title")).apply {
      font = JBFont.h1()
      border = JBUI.Borders.empty(18, 20)
    })

    val plugins = pluginService.plugins

    val listPane = JPanel(VerticalLayout(JBUI.scale(4))).apply {
      isOpaque = false
      plugins.forEach {
        val pl = WizardPluginPane(it) { changeHandler() }
        pluginPanes.add(pl)
        add(pl.pane)
      }
      border = JBUI.Borders.empty(10, 0)
    }
    addToCenter(
      JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(preferredSize.width, 0)

        add(JBScrollPane(listPane).apply {
          viewport.isOpaque = false
          isOpaque = true
          background = JBColor.namedColor("WelcomeScreen.Details.background", JBColor(Color.white, Color(0x313335)))
          horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
          border = JBUI.Borders.empty()
          minimumSize = Dimension(0, 0)
        })
      })
  }

  private val backAction = controller.createButton(ImportSettingsBundle.message("import.settings.back")) {
    controller.goToKeymapPage()
  }

  private val continueAction = controller.createDefaultButton(ImportSettingsBundle.message("plugins.page.ok.button.continue.without")) {
    val ids = getSelected().map { it.plugin.id }.toList()
    controller.goToInstallPluginPage(ids)
  }

  init {
    val buttons: List<JButton> = if (SystemInfo.isMac) {
      listOf(backAction, continueAction)
    }
    else listOf(continueAction, backAction)


    contentPage = WizardPagePane(pane, buttons, leftLabel)

    changeHandler()
  }

  override val content: JComponent = contentPage
}




