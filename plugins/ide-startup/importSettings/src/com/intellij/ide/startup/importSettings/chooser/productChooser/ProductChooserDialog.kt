// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.BannerOverlay
import com.intellij.ide.startup.importSettings.chooser.ui.PageProvider
import com.intellij.ide.startup.importSettings.chooser.ui.UiUtils
import com.intellij.ide.startup.importSettings.chooser.ui.WizardPageTracker
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.impl.ActionToolbarImpl
import com.intellij.openapi.rd.createLifetime
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.util.preferredHeight
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.*

class ProductChooserDialog : PageProvider() {

  private val accountLabel = JLabel("user.name").apply {
    icon = AllIcons.General.User
  }

  private val pane = JPanel(VerticalLayout(JBUI.scale(26), SwingConstants.CENTER)).apply {
    add(JLabel(ImportSettingsBundle.message("choose.product.title")).apply {
      font = Font(font.fontName, Font.PLAIN, JBUIScale.scaleFontSize(24f))
    })
  }

  private val callback: (PageProvider) -> Unit = {
    nextStep(it, OK_EXIT_CODE)
  }

  private val doClose = {
    doClose()
  }

  private val overlay = BannerOverlay()

  init {
    val group = DefaultActionGroup()
    group.isPopup = false

    group.add(SyncStateAction())
    group.add(SyncChooserAction(callback))
    group.add(JbChooserAction(callback))
    group.add(ExpChooserAction(callback))
    group.add(SkipImportAction(doClose))

    val settService = SettingsService.getInstance()
    val lifetime = disposable.createLifetime()

    settService.doClose.advise(lifetime) {
      doClose()
    }

    settService.error.advise(lifetime) {
      overlay.showError(it)
    }

    settService.jbAccount.advise(lifetime) {
      accountLabel.isVisible = it != null
      if (!accountLabel.isVisible) {
        return@advise
      }

      accountLabel.text = it?.loginName
    }

    val act = ActionManager.getInstance().createActionToolbar(ActionPlaces.IMPORT_SETTINGS_DIALOG, group, false).apply {
      if (this is ActionToolbarImpl) {

        setMinimumButtonSize {
          JBUI.size(UiUtils.DEFAULT_BUTTON_WIDTH, UiUtils.DEFAULT_BUTTON_HEIGHT)
        }
        setMiniMode(false)
      }
    }
    act.targetComponent = pane

    pane.add(act.component)
  }

  override fun createContent(): JComponent {
    val comp = JPanel(GridBagLayout()).apply {
      preferredSize = JBDimension(640, 410)
      val gbc = GridBagConstraints()
      gbc.gridx = 0
      gbc.gridy = 0
      gbc.weightx = 1.0
      gbc.weighty = 1.0
      gbc.fill = GridBagConstraints.NONE
      add(pane, gbc)
    }

    return overlay.wrapComponent(comp)
  }

  override fun createActions(): Array<Action> {
    return emptyArray()
  }

  private val south = JPanel(BorderLayout()).apply {
    val group = DefaultActionGroup()
    group.add(OtherOptions(callback))

    val at = object : ActionToolbarImpl(ActionPlaces.IMPORT_SETTINGS_DIALOG, group, true) {

      override fun getPreferredSize(): Dimension {
        val dm = super.getPreferredSize()
        dm.width -= 15
        return dm
      }
    }

    at.targetComponent = pane
    add(accountLabel, BorderLayout.WEST)
    add(at.component, BorderLayout.EAST)

    border = JBUI.Borders.empty(0, 20, 10, 0)
    preferredHeight = 47
  }

  override fun createSouthPanel(leftSideButtons: MutableList<out JButton>,
                                rightSideButtons: MutableList<out JButton>,
                                addHelpToLeftSide: Boolean): JPanel {
    return south
  }

  override val tracker = WizardPageTracker(StartupWizardStage.ProductChoicePage)
}
