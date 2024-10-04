// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.ImportSettingsController
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.JBrActionsDataProvider
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.ide.startup.importSettings.data.SyncActionsDataProvider
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import com.intellij.ui.components.ActionLink
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.SwingConstants

internal class OtherOptions(private val controller: ImportSettingsController, private val syncDataProvider: SyncActionsDataProvider) : ProductChooserAction() {

  private val jbDataProvider = JBrActionsDataProvider.getInstance()

  private var jb: List<AnAction>? = null
  private val config = ConfigAction(controller)

  init {
    templatePresentation.text = ImportSettingsBundle.message("choose.product.other.options")
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val jbProducts = jbDataProvider.other

    val arr = mutableListOf<AnAction>()
    if (jb == null && jbProducts != null) {
      jb = addActionList(jbProducts, jbDataProvider, ImportSettingsBundle.message("other.options.sub.title.installed"))
    }

    if (syncDataProvider.settingsService.isSyncEnabled && syncDataProvider.settingsService.hasDataToSync.value) {
      syncDataProvider.other?.let { products ->
        addActionList(products, syncDataProvider, ImportSettingsBundle.message("other.options.sub.title.setting.sync")).let {
          if (it.isNotEmpty()) {
            arr.addAll(it)
          }
        }
      }
    }

    jb?.let {
      if (it.isNotEmpty()) {
        arr.addAll(it)
      }
    }

    if (arr.isNotEmpty()) {
      arr.add(Separator())
    }
    arr.add(config)

    return arr.toTypedArray()
  }

  private fun addActionList(products: List<Product>, provider: ActionsDataProvider<*>, title: @Nls String? = null): MutableList<AnAction> {
    val list = mutableListOf<AnAction>()
    if (products.isNotEmpty()) {
      title?.let {
        list.add(Separator(it))
      }
      list.addAll(productsToActions(products, provider, controller))
    }
    return list
  }

  init {
    templatePresentation.isPopupGroup = true
    templatePresentation.text = ImportSettingsBundle.message("choose.product.other.options")
    templatePresentation.icon = AllIcons.General.LinkDropTriangle
  }

  override fun update(e: AnActionEvent) {
    val ch = getChildren(e)

    e.presentation.isVisible = ch.isNotEmpty()
  }

  override fun createButton(): JButton {
    return ActionLink().apply {
      setHorizontalTextPosition(SwingConstants.LEFT)
      setForeground(JBUI.CurrentTheme.Link.Foreground.ENABLED)
      iconTextGap = 0
    }
  }

  override fun wrapButton(button: JButton): JComponent {
    return button
  }
}


