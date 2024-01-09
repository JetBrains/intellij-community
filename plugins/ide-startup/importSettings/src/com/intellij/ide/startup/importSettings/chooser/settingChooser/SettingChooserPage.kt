// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.CommonBundle
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.ImportSettingsController
import com.intellij.ide.startup.importSettings.chooser.ui.OnboardingPage
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.util.SystemInfo
import com.intellij.platform.ide.bootstrap.StartupWizardStage
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SeparatorOrientation
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.*
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

abstract class SettingChooserPage(private val provider: ActionsDataProvider<*>,
                                  val product: SettingsContributor,
                                  controller: ImportSettingsController) : OnboardingPage {
  companion object {
    fun createPage(provider: ActionsDataProvider<*>,
                   product: SettingsContributor,
                   controller: ImportSettingsController): OnboardingPage {
      if (provider is SyncActionsDataProvider && provider.productService.baseProduct(product.id)) {
        return SyncSettingChooserPage(provider, product, controller)
      }
      return ConfigurableSettingChooserPage(provider, product, controller)
    }
  }

  open val configurable = true
  protected val settingPanes = mutableListOf<BaseSettingPane>()

  protected val backAction = controller.createButton(ImportSettingsBundle.message("import.settings.back")) {
    controller.goToProductChooserPage()
  }

  abstract val buttons: List<JButton>

  open fun changeHandler() {}

  override fun confirmExit(parentComponent: Component?): Boolean {
    return MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"),
                               ApplicationBundle.message("exit.confirm.prompt"))
      .yesText(ApplicationBundle.message("command.exit"))
      .noText(CommonBundle.getCancelButtonText())
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
                @Suppress("HardCodedStringLiteral") // IDEA-255051
                comment(addTxt).customize(
                  UnscaledGaps(top = 3))
              }
            }
          }
        }
      }.align(AlignY.TOP)
    }.apply {
      preferredWidth = JBUI.scale(200)
      border = JBUI.Borders.empty(20, 20, 0, 0)
    }, BorderLayout.WEST)

    val productService = provider.productService
    val listPane = JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      productService.getSettings(product.id).forEach {
        val st = createSettingPane(it, configurable) { changeHandler() }
        settingPanes.add(st)
        add(st.component())
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
  }.apply {
//    maximumSize = preferredSize
    minimumSize = Dimension(0, 0)
  }

  private var contentPage: JComponent? = null

  override val content: JComponent
    get() {
      val page = contentPage ?: JPanel(GridBagLayout()).apply {
        val gbc = GridBagConstraints()
        gbc.gridx = 0
        gbc.gridy = 0
        gbc.weightx = 1.0
        gbc.weighty = 0.0
        gbc.fill = GridBagConstraints.HORIZONTAL
        isOpaque = false

        add(SeparatorComponent(JBColor.namedColor("Borders.color", JBColor.BLACK), SeparatorOrientation.HORIZONTAL), gbc)

        gbc.fill = GridBagConstraints.BOTH
        gbc.gridy = 1
        gbc.weighty = 3.0
        add(pane, gbc)

        gbc.fill = GridBagConstraints.HORIZONTAL
        gbc.gridy = 2
        gbc.weighty = 0.0
        add(SeparatorComponent(JBColor.namedColor("Borders.color", JBColor.BLACK), SeparatorOrientation.HORIZONTAL), gbc)
        gbc.gridy = 3
        gbc.weighty = 0.0
        val buttonPane = JPanel(FlowLayout(FlowLayout.RIGHT)).apply {
          add(DialogWrapper.layoutButtonsPanel(buttons))
          border = JBUI.Borders.empty(3, 0, 3, 15)
        }
        add(buttonPane, gbc)
        border = JBUI.Borders.empty()
        contentPage = this
      }

      return page
    }
}

class ConfigurableSettingChooserPage<T : BaseService>(val provider: ActionsDataProvider<T>,
                                                      product: SettingsContributor,
                                                      controller: ImportSettingsController) : SettingChooserPage(provider, product,
                                                                                                                 controller) {

  override val stage = StartupWizardStage.SettingsToImportPage

  private val importButton = controller.createDefaultButton(ImportSettingsBundle.message("import.settings.ok")) {
    val productService = provider.productService
    val dataForSaves = prepareDataForSave()
    val importSettings = productService.importSettings(product.id, dataForSaves)

    controller.goToImportPage(importSettings)
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
    return settingPanes.map { it.item }.filter { it.selected }.map {
      val chs = it.childItems?.filter { item -> item.selected }?.map { item -> item.child.id }?.toList() ?: emptyList()
      DataForSave(it.setting.id, chs)
    }
  }
}

class SyncSettingChooserPage(val provider: SyncActionsDataProvider,
                             product: SettingsContributor,
                             controller: ImportSettingsController) : SettingChooserPage(provider, product, controller) {

  override val stage = StartupWizardStage.SettingsToSyncPage

  override val configurable = false

  private val importOnceButton = controller.createButton(ImportSettingsBundle.message("import.settings.sync.import.once")) {
    val syncSettings = provider.productService.importSyncSettings()
    controller.goToImportPage(syncSettings)
  }
  private val syncButton = controller.createDefaultButton(ImportSettingsBundle.message("import.settings.sync.ok")) {
    controller.goToImportPage(provider.productService.syncSettings())
  }

  override val buttons: List<JButton>
    get() = if (SystemInfo.isMac) {
      listOf(backAction, importOnceButton, syncButton)
    }
    else listOf(syncButton, importOnceButton, backAction)
}