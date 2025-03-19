// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.wizard.keymapChooser

import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingPage
import com.intellij.ide.startup.importSettings.chooser.ui.UiUtils
import com.intellij.ide.startup.importSettings.chooser.ui.WizardController
import com.intellij.ide.startup.importSettings.chooser.ui.WizardPagePane
import com.intellij.ide.startup.importSettings.data.KeymapService
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Component
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*

internal class KeymapChooserPage(val controller: WizardController) : OnboardingPage {
  private val pages = mutableListOf<KeymapPane>()
  private var activeKeymap: KeymapPane
  private val keymapPane = JPanel(GridBagLayout())
  private val contentPage: JComponent
  private val buttonGroup = ButtonGroup()

  private val keymapService: KeymapService
    get() = controller.service.getKeymapService()

  init {
    val list = keymapService.keymaps
    assert(list.isNotEmpty())

    list.forEachIndexed {
      index, wk ->
      val shortcuts = keymapService.shortcuts.map { ShortcutValue(it.name, wk.getShortcutValue(it.id)) }.toList()
      val keymap = Keymap(wk.id, wk.name, shortcuts)
      val pane = KeymapPane(keymap).apply {
        pane.addMouseListener(object : MouseAdapter() {
          override fun mousePressed(e: MouseEvent?) {
            activePane(this@apply)
          }
        })
      }

      val gbc = GridBagConstraints()

      gbc.insets = JBUI.insetsRight(if(index < list.size - 1) 9 else 0)
      gbc.gridx = pages.size
      gbc.gridy = 0
      gbc.weightx = 1.0
      gbc.weighty = 1.0
      gbc.fill = GridBagConstraints.BOTH

      pages.add(pane)
      this.keymapPane.add(pane.pane, gbc)
      buttonGroup.add(pane.jRadioButton)
      pane.jRadioButton.addActionListener {
        activePane(pane)
      }
    }

    val centralPane = JPanel(BorderLayout(0, 0)).apply {
      add(JLabel(ImportSettingsBundle.message("choose.keymap.title")).apply {
        font = UiUtils.HEADER_FONT
        border = UiUtils.HEADER_BORDER

      }, BorderLayout.NORTH)
      add(keymapPane, BorderLayout.CENTER)

      border = UiUtils.CARD_BORDER
    }

    activeKeymap = pages[0]
    activePane(activeKeymap)

    val backAction = controller.createButton(ImportSettingsBundle.message("import.settings.back")) {
      controller.goToThemePage(isForwardDirection = false)
    }

    val continueAction = controller.createDefaultButton(ImportSettingsBundle.message("wizard.button.continue")) {
      keymapService.chosen(activeKeymap.keymap.id)
      controller.goToPluginPage()
    }

    val buttons: List<JButton> =
      if (SystemInfo.isMac) {
        listOf(backAction, continueAction)
      }
      else listOf(continueAction, backAction)

    contentPage = WizardPagePane(centralPane, buttons)

  }

  private fun activePane(keymap: KeymapPane)  {
    assert(pages.isNotEmpty() && pages.contains(keymap))

    activeKeymap.active = false
    activeKeymap = keymap
    activeKeymap.active = true
  }

  fun onEnter(isForwardDirection: Boolean) {
    keymapService.onStepEnter(isForwardDirection)
  }

  override fun dispose() {
    pages.clear()
    super.dispose()
  }

  override val content: JComponent = contentPage


  override val stage: StartupWizardStage = StartupWizardStage.WizardKeymapPage

  override fun confirmExit(parentComponent: Component?): Boolean {
    return true
  }
}

