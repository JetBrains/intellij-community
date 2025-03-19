// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.pluginChooser

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.*
import com.intellij.ide.startup.importSettings.data.PluginService
import com.intellij.openapi.util.NlsActions.ActionText
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.components.BorderLayoutPanel
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

internal class WizardPluginsPage(
  val controller: BaseController,
  private val pluginService: PluginService,
  goBackAction: () -> Unit,
  goForwardAction: (List<String>) -> Unit,
  private val continueButtonTextOverride: @ActionText String?
) : OnboardingPage {

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
        continueAction.text = continueButtonTextOverride ?: ImportSettingsBundle.message("plugins.page.ok.button.continue.without")
      }
      1 -> {
        leftLabel.text = ImportSettingsBundle.message("plugins.page.choose.counter.one")
        continueAction.text = continueButtonTextOverride ?: ImportSettingsBundle.message("plugins.page.ok.button.install")
      }
      else -> {
        leftLabel.text = ImportSettingsBundle.message("plugins.page.choose.counter.multiple", selected.size)
        continueAction.text = continueButtonTextOverride ?: ImportSettingsBundle.message("plugins.page.ok.button.install")
      }
    }
  }

  private val pane = BorderLayoutPanel().apply {
    isOpaque = false
    addToTop(JLabel(ImportSettingsBundle.message("plugins.page.title")).apply {
      font = UiUtils.HEADER_FONT
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

        add(ScrollSnapToFocused(listPane, this@WizardPluginsPage).apply {
          viewport.isOpaque = false
          isOpaque = true
          background = JBColor.namedColor("WelcomeScreen.Details.background", JBColor(Color.white, Color(0x313335)))

          SwingUtilities.invokeLater {
            this.requestFocus()
          }
        })
      })
  }

  private val backAction = controller.createButton(ImportSettingsBundle.message("import.settings.back"), goBackAction)

  private val continueAction = controller.createDefaultButton(continueButtonTextOverride ?: ImportSettingsBundle.message("plugins.page.ok.button.continue.without")) {
    val ids = getSelected().map { it.plugin.id }.toList()
    goForwardAction(ids)
  }

  init {
    val buttons: List<JButton> = if (SystemInfo.isMac) {
      listOf(backAction, continueAction)
    }
    else listOf(continueAction, backAction)


    contentPage = WizardPagePane(pane, buttons, leftLabel)

    changeHandler()

    continueAction.requestFocus()
  }

  override val content: JComponent = contentPage

  fun onEnter() {
    pluginService.onStepEnter()
  }
}
