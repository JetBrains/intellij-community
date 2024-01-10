// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.pluginChooser

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.settingChooser.BaseSettingPane
import com.intellij.ide.startup.importSettings.chooser.settingChooser.createSettingPane
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingPage
import com.intellij.ide.startup.importSettings.chooser.ui.WizardController
import com.intellij.ide.startup.importSettings.chooser.ui.WizardPagePane
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SeparatorOrientation
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.lifetime.intersect
import java.awt.*
import javax.swing.*

class PluginChooserPage(val controller: WizardController) : OnboardingPage {

  override val stage: StartupWizardStage = StartupWizardStage.WizardPluginPage

  private val lifetime = controller.lifetime.createNested().intersect(this.createLifetime())

  private val pluginPanes = mutableListOf<WizardPluginPane>()

  private val contentPage: JComponent
  override fun confirmExit(parentComponent: Component?): Boolean = true

  private val pane = JPanel(BorderLayout(0, 0)).apply {
    add(JLabel(ImportSettingsBundle.message("choose.keymap.title")).apply {
      font = JBFont.h1()
      border = JBUI.Borders.empty(18, 0)

    }, BorderLayout.NORTH)

    val plugins = controller.service.getPluginService().plugins

    val listPane = JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      plugins.forEach {
        val pl = WizardPluginPane(it, lifetime)
        pluginPanes.add(pl)
        add(pl.pane)
      }
    }
    add(
      JBScrollPane(listPane).apply {
        viewport.isOpaque = false
        isOpaque = true
        background = JBColor.namedColor("WelcomeScreen.Details.background", JBColor(Color.white, Color(0x313335)))
        horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
        border = JBUI.Borders.empty(16, 0, 12, 0)
      }, BorderLayout.CENTER
    )
  }

  init {
    val backAction = controller.createButton(ImportSettingsBundle.message("import.settings.back")) {
      controller.goToKeymapPage()
    }


    val continueAction = controller.createDefaultButton(ImportSettingsBundle.message("wizard.button.continue")) {
      //controller.goToPluginPage()
    }

    val buttons: List<JButton> = backAction?.let {
      if (SystemInfo.isMac) {
        listOf(backAction, continueAction)
      }
      else listOf(continueAction, backAction)
    } ?: listOf(continueAction)

    contentPage = WizardPagePane(pane, buttons)
  }

  override val content: JComponent = contentPage

}


