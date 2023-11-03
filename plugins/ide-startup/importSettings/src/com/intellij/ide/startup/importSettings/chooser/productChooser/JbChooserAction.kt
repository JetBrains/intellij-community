// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.chooser.ui.PageProvider
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.ui.scale.JBUIScale
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class JbChooserAction(callback: (PageProvider) -> Unit) : MainChooserAction<JbService>(JBrActionsDataProvider.getInstance(), callback) {
  override fun getIcon(products: List<Product>): Icon {
    return ImportJbIcon(products) { provider.getProductIcon(it) }
  }
}

class ExpChooserAction(callback: (PageProvider) -> Unit) : MainChooserAction<ExternalService>(ExtActionsDataProvider.getInstance(),
                                                                                              callback)

class SyncChooserAction(callback: (PageProvider) -> Unit) : MainChooserAction<SyncService>(SyncActionsDataProvider.getInstance(),
                                                                                           callback) {
  private val service = SettingsService.getInstance()

  override fun getIcon(products: List<Product>): Icon {
    return AllIcons.Actions.Refresh
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = false
    if (!service.isSyncEnabled.value) {
      return
    }
    super.update(e)
  }
}

private class ImportJbIcon(list: List<Product>, converter: (String) -> Icon?) : Icon {
  private val gap = JBUIScale.scale(2)
  private val gapAfter = JBUIScale.scale(1)
  val icons = list.take(3).map { converter(it.id) }
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    var width = 0
    icons.filterNotNull().forEachIndexed { i, it ->
      if (i > 0) width += gap
      it.paintIcon(c, g, x + width, y)
      width += it.iconWidth
    }
  }

  override fun getIconWidth(): Int {
    val list = icons.filterNotNull()
    return list.sumOf { it.iconWidth } + ((list.size - 1) * gap) + gapAfter
  }

  override fun getIconHeight(): Int {
    return icons.maxOf { it?.iconHeight ?: 0 }
  }
}