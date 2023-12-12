// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.ui

import com.intellij.ide.startup.importSettings.chooser.importProgress.ImportProgressPage
import com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserPage
import com.intellij.ide.startup.importSettings.chooser.settingChooser.SettingChooserPage
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.DialogImportData
import com.intellij.ide.startup.importSettings.data.SettingsContributor
import com.intellij.ide.startup.importSettings.data.SettingsService
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBUI
import com.jetbrains.rd.util.lifetime.Lifetime
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel

class ImportSettingsDialog(val cancelCallback: () -> Unit) : DialogWrapper(null, null, true, IdeModalityType.IDE,
                                                                           false), ImportSettingsController {
  companion object {
    fun show(callback: () -> Unit = {},
             isModal: Boolean = true,
             @NlsContexts.DialogTitle title: String? = null) {
      val dialog = ImportSettingsDialog(callback).apply {
        this.isResizable = false
        this.title = title
        this.isModal = isModal
      }

      dialog.show()
    }
  }

  private var currentPage: ImportSettingsPage = EmptyImportSettingsPage()
  override val lifetime: Lifetime = disposable.createLifetime()
  private val tracker = WizardPageTracker()

  private val pane = JPanel(BorderLayout()).apply {
    border = JBUI.Borders.empty()
    preferredSize = JBDimension(640, 457)
  }

  override fun goToSettingsPage(provider: ActionsDataProvider<*>, product: SettingsContributor) {
    val page = SettingChooserPage.createPage(provider, product, this)
    changePage(page)
  }

  override fun goToProductChooserPage() {
    val page = ProductChooserPage(this)
    changePage(page)
  }

  override fun goToImportPage(importFromProduct: DialogImportData) {
    val page = ImportProgressPage(importFromProduct, this)
    changePage(page)
  }

  override fun doCancelAction() {
    val shouldExit = currentPage.confirmExit(peer.contentPane)

    if (shouldExit != false) {
      super.doCancelAction()
      tracker.onLeave()
      cancelCallback()
    }

  }

  override fun skipImport() {
    tracker.onLeave()
    close(CANCEL_EXIT_CODE)
  }

  private fun changePage(page: ImportSettingsPage) {
    overlay.clearNotifications()
    pane.remove(currentPage.content)
    tracker.onLeave()

    val content = page.content
    pane.add(content)

    currentPage = page
    tracker.onEnter(page.stage)
  }

  override fun getStyle(): DialogStyle {
    return DialogStyle.COMPACT
  }

  private val overlay: BannerOverlay

  init {
    overlay = BannerOverlay()
    goToProductChooserPage()

    val settService = SettingsService.getInstance()
    settService.doClose.advise(lifetime) {
      skipImport()
    }

    settService.error.advise(lifetime) {
      overlay.showError(it)
    }

    init()
  }

  override fun createCenterPanel(): JComponent {
    return overlay.wrapComponent(pane)
  }

  override fun createDefaultButton(name: @Nls String, handler: () -> Unit): JButton {
    val action = createAction(name, handler).apply {
      putValue(DEFAULT_ACTION, true)
      putValue(FOCUSED_ACTION, true)
    }

    return createJButtonForAction(action)
  }

  override fun createButton(name: @Nls String, handler: () -> Unit): JButton {
    val action = createAction(name, handler)
    return createJButtonForAction(action)
  }

  private fun createAction(name: @Nls String, handler: () -> Unit): Action {
    return object : DialogWrapperAction(name) {
      override fun doAction(e: ActionEvent?) {
        handler()
      }
    }
  }
}

interface ImportSettingsPage {
  val content: JComponent
  val stage: StartupWizardStage?

  fun confirmExit(parentComponent: Component?): Boolean?
}

class EmptyImportSettingsPage : ImportSettingsPage {
  override val content: JComponent = JPanel()
  override val stage: StartupWizardStage = StartupWizardStage.InitialStart
  override fun confirmExit(parentComponent: Component?): Boolean = true
}