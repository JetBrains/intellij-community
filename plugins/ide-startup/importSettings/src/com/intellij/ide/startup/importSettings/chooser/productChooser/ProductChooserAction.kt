// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.ide.startup.importSettings.chooser.settingChooser.SettingChooserItemAction
import com.intellij.ide.startup.importSettings.chooser.ui.ImportSettingsController
import com.intellij.ide.startup.importSettings.chooser.ui.UiUtils
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.util.ui.JBUI
import java.awt.Dimension
import java.awt.Point
import javax.swing.JComponent
import javax.swing.ListCellRenderer

internal sealed class ProductChooserAction : ChooseProductActionButton(null) {
  private val actionGroup = object : DefaultActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return this@ProductChooserAction.getChildren(e)
    }
  }

  abstract fun getChildren(e: AnActionEvent?): Array<AnAction>

  protected fun productsToActions(products: List<Product>, provider: ActionsDataProvider<*>, controller: ImportSettingsController): List<AnAction> {
    return products.map { pr -> SettingChooserItemAction(pr, provider, controller) }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val comp: JComponent = event.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return

    val step = createStep(actionGroup, event.dataContext, comp)
    createPopup(step).show(RelativePoint(comp, Point(0, comp.height + JBUI.scale(4))))
  }

  override fun update(e: AnActionEvent) {
    val ch = getChildren(e)
    e.presentation.putClientProperty(UiUtils.POPUP, ch.size != 1)

    if (ch.size == 1) {
      e.presentation.text = null
      e.presentation.icon = null
      e.presentation.description = null
      ch.firstOrNull()?.let {
        it.update(e)
        e.presentation.text = e.presentation.text ?: it.templateText
        e.presentation.icon = e.presentation.icon ?: it.templatePresentation.icon
        e.presentation.getClientProperty(UiUtils.DESCRIPTION)?.let { descr ->
          e.presentation.description = descr
        }
      }
      return
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  private val renderer = ProductChooserRenderer()

  private fun createPopup(step: ListPopupStep<Any>): ListPopup {

   /* JBPopupFactory.getInstance().createListPopup(step)*/

    val result = object : ListPopupImpl(null, step) {
      override fun getListElementRenderer(): ListCellRenderer<*> {
        return renderer
      }

      override fun createPopupComponent(content: JComponent?): JComponent {
        val popupComponent = super.createPopupComponent(content)
        popupComponent.preferredSize = Dimension(JBUI.scale(UiUtils.DEFAULT_BUTTON_WIDTH).coerceAtLeast(popupComponent.preferredSize.width), popupComponent.preferredSize.height)

        return popupComponent
      }

    }
    return result
  }

  private fun createStep(actionGroup: ActionGroup, context: DataContext, widget: JComponent?): ListPopupStep<Any> {
    return JBPopupFactory.getInstance().createActionsStep(actionGroup, context, ActionPlaces.PROJECT_WIDGET_POPUP, false, false,
                                                          null, widget, false, 0, false)
  }

}