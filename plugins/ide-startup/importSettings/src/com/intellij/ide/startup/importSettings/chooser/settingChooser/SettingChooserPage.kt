// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.CommonBundle
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.ImportSettingsController
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingPage
import com.intellij.ide.startup.importSettings.chooser.ui.ScrollSnapToFocused
import com.intellij.ide.startup.importSettings.chooser.ui.WizardPagePane
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.startup.importSettings.statistics.ImportSettingsEventsCollector
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.ui.JBColor
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.*

internal sealed class SettingChooserPage(
  private val provider: ActionsDataProvider<*>,
  val product: SettingsContributor,
  controller: ImportSettingsController,
) : OnboardingPage {
  companion object {
    internal fun createPage(
      provider: ActionsDataProvider<*>,
      product: Product,
      controller: ImportSettingsController,
    ): OnboardingPage {
      if (provider is SyncActionsDataProvider && provider.productService.baseProduct(product.id)) {
        return SyncSettingChooserPage(provider, product, controller)
      }
      return ConfigurableSettingChooserPage(provider, product, controller)
    }
  }

  open val configurable = true
  protected val settingPanes = mutableListOf<BaseSettingPane>()

  protected val backAction = controller.createButton(ImportSettingsBundle.message("import.settings.back")) {
    ImportSettingsEventsCollector.configurePageBack()
    controller.goToProductChooserPage()
  }

  abstract val buttons: List<JButton>

  open fun changeHandler() {}

  override fun confirmExit(parentComponent: Component?): Boolean {
    return MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"),
                                      ApplicationBundle.message("exit.confirm.prompt"))
      .yesText(ApplicationBundle.message("command.exit"))
      .noText(CommonBundle.getCancelButtonText())
      .asWarning()
      .ask(parentComponent)
  }

  private val pane = JPanel(BorderLayout()).apply {
    add(panel {
      row {
        @Suppress("DialogTitleCapitalization")
        text(ImportSettingsBundle.message("choose.settings.title")).apply {
          this.component.font = JBFont.h1()
        }.align(AlignY.TOP).customize(UnscaledGaps(0, 0, 17, 0))
      }
      panel {
        row {
          provider.getProductIcon(product.id, IconProductSize.MIDDLE)?.let { icn ->
            icon(icn).align(AlignY.TOP).customize(UnscaledGaps(0, 0, 0, 8))
          }
          panel {
            row {
              text(provider.getText(product)).customize(UnscaledGaps(0, 0, 0, 0))
            }

            provider.getComment(product)?.let { addTxt ->
              row {
                comment(addTxt).customize(
                  UnscaledGaps(top = 3))
              }
            }
          }
        }
      }.align(AlignY.TOP)
    }.apply {
      preferredSize = Dimension(JBUI.scale(200), preferredSize.height)

      border = JBUI.Borders.empty(20, 20, 0, 0)
    }, BorderLayout.WEST)

    val productService = provider.productService
    val listPane = JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      productService.getSettings(product.id).forEach {
        val st = createSettingPane(it, it.isConfigurable && configurable, { changeHandler() }, this@SettingChooserPage)
        settingPanes.add(st)
        add(st.component())
      }
    }
    add(
      JPanel().apply {
        layout = BoxLayout(this, BoxLayout.Y_AXIS)
        preferredSize = Dimension(preferredSize.width, 0)
        border = JBUI.Borders.empty(16, 0, 12, 0)
        background = JBColor.namedColor("WelcomeScreen.Details.background", JBColor(Color.white, Color(0x313335)))

        add(ScrollSnapToFocused(listPane, this@SettingChooserPage).apply {
          viewport.isOpaque = false
          background = JBColor.namedColor("WelcomeScreen.Details.background", JBColor(Color.white, Color(0x313335)))
          accessibleContext.accessibleName = ImportSettingsBundle.message("choose.settings.title.accessible.name", provider.getText(product))

          SwingUtilities.invokeLater {
            this.requestFocus()
          }
        })

      }, BorderLayout.CENTER)
  }

  private var contentPage: JComponent? = null

  override val content: JComponent
    get() {
      val page = contentPage ?: WizardPagePane(pane, buttons)

      return page
    }
}

private class ConfigurableSettingChooserPage<T : BaseService>(
  val provider: ActionsDataProvider<T>,
  product: Product,
  controller: ImportSettingsController
) : SettingChooserPage(provider, product,
                       controller) {

  override val stage = StartupWizardStage.SettingsToImportPage

  private val importButton = controller.createDefaultButton(
    if (controller.canShowFeaturedPluginsPage(product.origin)) {
      ImportSettingsBundle.message("import.next")
    }
    else {
      ImportSettingsBundle.message("import.settings.ok")
    }
  ) {
    val productService = provider.productService
    val dataForSaves = prepareDataForSave()
    ImportSettingsEventsCollector.configurePageImportSettingsClicked()
    if (controller.canShowFeaturedPluginsPage(product.origin)
        && controller.shouldShowFeaturedPluginsPage(product.id, dataForSaves, productService)) {
      controller.goToFeaturedPluginsPage(provider, productService, product, dataForSaves)
    } else {
      val dataToApply = DataToApply(dataForSaves, emptyList())
      val importSettings = productService.importSettings(product.id, dataToApply)
      controller.goToProgressPage(importSettings, dataToApply)
    }
  }

  override val buttons: List<JButton>
    get() = if (SystemInfo.isMac) {
      listOf(backAction, importButton)
    }
    else listOf(importButton, backAction)

  override fun changeHandler() {
    val dataForSaves = prepareDataForSave()
    importButton.isEnabled = dataForSaves.isNotEmpty()
  }

  private fun prepareDataForSave(): List<DataForSave> {
    return settingPanes.map { it.item }.filter { it.selected }.map { settingItem ->
      if (settingItem.childItems == null) {
        DataForSave(settingItem.setting.id)
      } else {
        val (selectedItems, unselectedItems) = settingItem
          .childItems
          .partition { childItem -> childItem.selected }

        DataForSave(settingItem.setting.id,
                    selectedItems.map { it.child.id },
                    unselectedItems.map { it.child.id }
        )
      }
      /*
      val selectedChildren = it.childItems?.filter { item -> item.selected }?.map { item -> item.child.id }?.toList() ?: emptyList()
      val unselectedChidren = it.childItems?.filter { item -> item.selected }?.map { item -> item.child.id }?.toList() ?: emptyList()
      */
    }
  }
}

private class SyncSettingChooserPage(val provider: SyncActionsDataProvider,
                             product: SettingsContributor,
                             controller: ImportSettingsController) : SettingChooserPage(provider, product, controller) {

  override val stage = StartupWizardStage.SettingsToSyncPage

  override val configurable = false

  private val importOnceButton = controller.createButton(ImportSettingsBundle.message("import.settings.sync.import.once")) {
    val syncSettings = provider.productService.importSyncSettings()
    controller.goToProgressPage(syncSettings, DataToApply(emptyList(), emptyList()))
  }
  private val syncButton = controller.createDefaultButton(ImportSettingsBundle.message("import.settings.sync.ok")) {
    controller.goToProgressPage(provider.productService.syncSettings(), DataToApply(emptyList(), emptyList()))
  }

  override val buttons: List<JButton>
    get() = if (SystemInfo.isMac) {
      listOf(backAction, importOnceButton, syncButton)
    }
    else listOf(syncButton, importOnceButton, backAction)
}
