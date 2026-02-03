// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.themeChooser

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.WizardLookAndFeelUtil
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingPage
import com.intellij.ide.startup.importSettings.chooser.ui.UiUtils
import com.intellij.ide.startup.importSettings.chooser.ui.WizardController
import com.intellij.ide.startup.importSettings.chooser.ui.WizardPagePane
import com.intellij.ide.startup.importSettings.data.ThemeService
import com.intellij.ide.ui.LafManager
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.panel
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.ButtonGroup
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

internal class ThemeChooserPage(val controller: WizardController) : OnboardingPage {
  private val service = controller.service.getThemeService()

  private val pages = mutableListOf<SchemePane>()
  private lateinit var segmentedButton: SegmentedButton<ThemeService.Theme>
  val schemesPaneGridBagLayout = GridBagLayout()
  private val schemesPane = JPanel(schemesPaneGridBagLayout)
  private val contentPage: JComponent

  private var activeScheme: SchemePane

  private val buttonGroup = ButtonGroup()

  init {

    val list = service.schemesList
    assert(list.isNotEmpty())
    assert(service.themeList.isNotEmpty())

    list.forEachIndexed { index, sc ->
      val pane = SchemePane(sc).apply {
        this.pane.addMouseListener(object : MouseAdapter() {
          override fun mousePressed(e: MouseEvent?) {
            activePane(this@apply)
          }
        })
      }
      val gbc = GridBagConstraints()

      gbc.insets = JBUI.insetsRight(if (index < list.size - 1) 9 else 0)
      gbc.gridx = pages.size
      gbc.gridy = 0
      gbc.weightx = 1.0
      gbc.weighty = 1.0
      gbc.fill = GridBagConstraints.BOTH

      pages.add(pane)
      schemesPane.add(pane.pane, gbc)
      buttonGroup.add(pane.jRadioButton)
      pane.jRadioButton.addActionListener {
        activePane(pane)
      }
    }


    val centralPane = JPanel(BorderLayout(0, 0)).apply parentPanel@{
      val pane = panel {
        row {
          @Suppress("DialogTitleCapitalization")
          label(ImportSettingsBundle.message("theme.page.title")).applyToComponent {
            font = UiUtils.HEADER_FONT
            border = UiUtils.HEADER_BORDER
          }.resizableColumn()
          segmentedButton = segmentedButton(service.themeList) { text = it.themeName }
            .whenItemSelected {
              service.currentTheme = it
              val lafManager = LafManager.getInstance()
              val laf = if (it.isDark)
                lafManager.defaultDarkLaf
              else lafManager.defaultLightLaf

              if (laf != null) {
                WizardLookAndFeelUtil.applyLookAndFeelToWizardWindow(laf, this@parentPanel)
              }
            }.apply {
              selectedItem = service.currentTheme
            }
        }
      }

      add(pane, BorderLayout.NORTH)
      add(schemesPane, BorderLayout.CENTER)

      border = UiUtils.CARD_BORDER
    }

    activeScheme = service.initialSchemeId?.let { initialId ->
      pages.firstOrNull { it.scheme.id == initialId }
    } ?: pages[0]
    activePane(activeScheme)

    val backAction = controller.goBackAction?.let {
      controller.createButton(ImportSettingsBundle.message("import.settings.back")) {
        it.invoke()
      }
    }

    val continueAction = controller.createDefaultButton(ImportSettingsBundle.message("wizard.button.continue")) {
      controller.goToKeymapPage(isForwardDirection = true)
    }

    val buttons: List<JButton> = backAction?.let {
      if (SystemInfo.isMac) {
        listOf(backAction, continueAction)
      }
      else listOf(continueAction, backAction)
    } ?: listOf(continueAction)

    contentPage = WizardPagePane(centralPane, buttons)
  }

  fun activePane(schemePane: SchemePane) {
    assert(pages.isNotEmpty() && pages.contains(schemePane))

    activeScheme.active = false

    var constraints = schemesPaneGridBagLayout.getConstraints(activeScheme.pane)
    constraints.weightx = 1.0
    schemesPaneGridBagLayout.setConstraints(activeScheme.pane, constraints)

    activeScheme = schemePane
    activeScheme.active = true

    constraints = schemesPaneGridBagLayout.getConstraints(schemePane.pane)
    constraints.weightx = 2.0
    schemesPaneGridBagLayout.setConstraints(schemePane.pane, constraints)

    schemesPane.revalidate()
    schemesPane.repaint()

    service.updateScheme(schemePane.scheme.id)
  }

  fun onEnter(isForwardDirection: Boolean) {
    service.onStepEnter(isForwardDirection)
  }

  override val content: JComponent = contentPage
  override val stage: StartupWizardStage = StartupWizardStage.WizardThemePage

  override fun confirmExit(parentComponent: Component?): Boolean {
    return true
  }
}
