// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.ImportSettingsBundle
import com.intellij.ide.startup.importSettings.chooser.ui.ImportSettingsController
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Separator
import org.jetbrains.annotations.Nls
import javax.swing.JButton
import javax.swing.JComponent

class OtherOptions(private val controller: ImportSettingsController) : ProductChooserAction() {

  private val jbDataProvider = JBrActionsDataProvider.getInstance()
  private val syncDataProvider = SyncActionsDataProvider.getInstance()

  private var jb: List<AnAction>? = null
  private var sync: List<AnAction>? = null
  private val config = ConfigAction(controller)

  init {
    templatePresentation.text = ImportSettingsBundle.message("choose.product.other.options")
  }

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val jbProducts = jbDataProvider.other
    val syncProducts = if (syncDataProvider.settingsService.isLoggedIn()) syncDataProvider.other else emptyList()

    val arr = mutableListOf<AnAction>()
    if (jb == null && jbProducts != null) {
      jb = addActionList(jbProducts, jbDataProvider, ImportSettingsBundle.message("other.options.sub.title.installed"))
    }

    if (sync == null && syncProducts != null && syncDataProvider.settingsService.isSyncEnabled.value) {
      sync = addActionList(syncProducts, syncDataProvider, ImportSettingsBundle.message("other.options.sub.title.setting.sync"))
    }

    sync?.let {
      if (it.isNotEmpty()) {
        arr.addAll(it)
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
    return OnboardingDialogButtons.createLinkButton().apply {
      icon = AllIcons.General.LinkDropTriangle
    }
  }

  override fun wrapButton(button: JButton): JComponent {
    return button
  }
}


