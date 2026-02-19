// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.ide.startup.importSettings.chooser.ui.ImportSettingsController
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.BaseService
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import javax.swing.Icon

internal open class MainChooserAction<T : BaseService>(val provider: ActionsDataProvider<T>, private val controller: ImportSettingsController) : ProductChooserAction() {

  override fun displayTextInToolbar(): Boolean {
    return true
  }

  override fun getChildren(e: AnActionEvent?): Array<AnAction> {
    val products = provider.main ?: emptyList()
    return productsToActions(products, provider, controller).toTypedArray()
  }

  override fun actionPerformed(event: AnActionEvent) {
    val children = getChildren(event)
    if (children.size == 1) {
      children.firstOrNull()?.actionPerformed(event)
      return
    }
    super.actionPerformed(event)
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isVisible = false

    val products = provider.main ?: return
    if (products.isEmpty()) return

    if (products.size == 1) {
      products.firstOrNull()?.let {
        e.presentation.icon = provider.getProductIcon(it.id)
        e.presentation.text = provider.getText(it)
        e.presentation.isVisible = true
        e.presentation.isPopupGroup = false
      }
      return
    }

    e.presentation.isVisible = true
    e.presentation.text = provider.title
    e.presentation.icon = getIcon(products)
    e.presentation.isPopupGroup = true
  }

  open fun getIcon(products: List<Product>): Icon? {
    return null
  }
}