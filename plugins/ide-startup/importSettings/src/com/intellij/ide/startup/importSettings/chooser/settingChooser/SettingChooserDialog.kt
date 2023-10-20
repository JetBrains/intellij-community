// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.settingChooser

import com.intellij.CommonBundle
import com.intellij.ide.startup.importSettings.chooser.productChooser.ProductChooserDialog
import com.intellij.ide.startup.importSettings.chooser.ui.PageProvider
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.IconProductSize
import com.intellij.ide.startup.importSettings.data.SettingsContributor
import com.intellij.openapi.application.ApplicationBundle
import com.intellij.openapi.rd.createLifetime
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.ui.JBColor
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.SeparatorOrientation
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.panels.VerticalLayout
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Dimension
import java.awt.event.ActionEvent
import javax.swing.Action
import javax.swing.BoxLayout
import javax.swing.JComponent
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER

open class SettingChooserDialog(private val provider: ActionsDataProvider<*>, val product: SettingsContributor) : PageProvider() {
  open val configurable = true
  protected val settingPanes = mutableListOf<BaseSettingPane>()

  init {
    val lifetime = disposable.createLifetime()
    provider.settingsService.doClose.advise(lifetime) {
      doClose()
    }
  }

  override fun showExit(): MessageDialogBuilder.YesNo? = MessageDialogBuilder.yesNo(ApplicationBundle.message("exit.confirm.title"),
                                                                                    ApplicationBundle.message("exit.confirm.prompt"))
    .yesText(ApplicationBundle.message("command.exit"))
    .noText(CommonBundle.getCancelButtonText())


  private val pane = JPanel(BorderLayout()).apply {
    add(panel {
      row {
        text("Import<br>Settings From").apply {
          this.component.font = JBFont.h1()
        }.align(AlignY.TOP).customize(UnscaledGaps(0, 0, 17, 0))
      }
      panel {
        provider.getProductIcon(product.id, IconProductSize.MIDDLE)?.let { icn ->
          row {
            icon(icn).align(AlignY.TOP).customize(UnscaledGaps(0, 0, 0, 8))
            panel {
              row {
                text(provider.getText(product)).customize(UnscaledGaps(0, 0, 2, 0))
              }

              provider.getComment(product)?.let { addTxt ->
                row {
                  comment(addTxt).customize(
                    UnscaledGaps(0))
                }
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

    val listPane = JPanel().apply {
      isOpaque = false
      layout = BoxLayout(this, BoxLayout.Y_AXIS)
      productService.getSettings(product.id).forEach {
        val st = createSettingPane(it, configurable)
        settingPanes.add(st)
        add(st.component())
      }
    }
    add(
      JBScrollPane(listPane).apply {
        isOpaque = false
        viewport.isOpaque = false
        isOpaque = true
        background = JBColor.namedColor("WelcomeScreen.Details.background", JBColor(Color.white, Color(0x313335)))
        horizontalScrollBarPolicy = HORIZONTAL_SCROLLBAR_NEVER
        border = JBUI.Borders.empty(16, 0, 12, 0)
      }, BorderLayout.CENTER
    )
  }.apply {
    preferredSize = JBDimension(640, 410)
    maximumSize = preferredSize
    minimumSize = Dimension(0, 0)
  }

  override fun createContent(): JComponent? {
    return JPanel(VerticalLayout(0)).apply {
      isOpaque = false
      add(SeparatorComponent(JBColor.namedColor("Borders.color", JBColor.BLACK), SeparatorOrientation.HORIZONTAL))
      add(pane)
      border = JBUI.Borders.empty()
    }
  }

  protected fun getBackAction(): Action {
    return object : DialogWrapperAction("Back") {

      override fun doAction(e: ActionEvent?) {
        val dialog = ProductChooserDialog()
        nextStep(dialog, CANCEL_EXIT_CODE)
      }
    }
  }

  override fun getOKAction(): Action {
    return super.getOKAction().apply {
      putValue(Action.NAME, "Import Settings")
    }
  }
}