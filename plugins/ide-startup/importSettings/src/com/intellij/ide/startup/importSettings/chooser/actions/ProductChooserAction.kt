// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.actions

import com.intellij.ide.startup.importSettings.chooser.ui.ProductChooserRenderer
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.openapi.wm.impl.ToolbarComboButton
import com.intellij.ui.popup.list.ListPopupImpl
import javax.swing.JComponent
import javax.swing.ListCellRenderer

open class ProductChooserAction() : DefaultActionGroup() {
  protected fun productsToActions(products: List<Product>, provider: ActionsDataProvider<*>, callback: (Int) -> Unit): List<AnAction> {
    return products.map { pr -> SettingChooserItemAction(pr, provider, callback) }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val widget = event.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? ToolbarComboButton?
    val step = createStep(this, event.dataContext, widget)
    createPopup(step)
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private val renderer = ProductChooserRenderer()



  private fun createPopup(step: ListPopupStep<Any>): ListPopup {

   /* JBPopupFactory.getInstance().createListPopup(step)*/


    val result = object : ListPopupImpl(null, step) {
      override fun getListElementRenderer(): ListCellRenderer<*> {
        return renderer
      }

    }
    result.setRequestFocus(false)
    return result
  }

  private fun createStep(actionGroup: ActionGroup, context: DataContext, widget: JComponent?): ListPopupStep<Any> {
    return JBPopupFactory.getInstance().createActionsStep(actionGroup, context, ActionPlaces.PROJECT_WIDGET_POPUP, false, false,
                                                          null, widget, false, 0, false)
  }

}