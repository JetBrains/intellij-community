// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.actions

import com.intellij.icons.AllIcons
import com.intellij.ide.startup.importSettings.data.*
import com.intellij.openapi.actionSystem.AnActionEvent
import java.awt.Component
import java.awt.Graphics
import javax.swing.Icon

class JbChooserAction(callback: (Int) -> Unit) : MainChooserAction<JbService>(JBrActionsDataProvider.getInstance(), callback) {
  override fun getIcon(products: List<Product>): Icon? {
    return ImportJbIcon(products) { provider.getProductIcon(it) }
  }
}

class ExpChooserAction(callback: (Int) -> Unit) : MainChooserAction<ExternalService>(ExtActionsDataProvider.getInstance(), callback)

class SyncChooserAction(callback: (Int) -> Unit) : MainChooserAction<SyncService>(SyncActionsDataProvider.getInstance(), callback) {
  private val service = SettingsService.getInstance().getSyncService()

  override fun getIcon(products: List<Product>): Icon? {
    return AllIcons.Actions.Refresh
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isVisible = false
    if(service.syncState != SyncService.SYNC_STATE.LOGGED) {
      return
    }
    super.update(e)
  }
}

private class ImportJbIcon(list: List<Product>, converter: (String) -> Icon?) : Icon {
  val icons = list.take(3).map { converter(it.id) }
  override fun paintIcon(c: Component?, g: Graphics?, x: Int, y: Int) {
    var width = 0
    icons.filterNotNull().forEach {
      it.paintIcon(c, g, x + width, y)
      width += it.iconWidth
    }
  }

  override fun getIconWidth(): Int {
    return icons.sumOf { it?.iconWidth ?: 0 }
  }

  override fun getIconHeight(): Int {
    return icons.maxOf { it?.iconHeight ?: 0 }
  }
}