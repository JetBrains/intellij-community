// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.actions

import com.intellij.ide.startup.importSettings.chooser.ui.JButtonAction_
import com.intellij.ide.startup.importSettings.chooser.ui.ProductChooserRenderer
import com.intellij.ide.startup.importSettings.chooser.ui.UiUtils
import com.intellij.ide.startup.importSettings.data.ActionsDataProvider
import com.intellij.ide.startup.importSettings.data.Product
import com.intellij.ide.ui.laf.darcula.ui.OnboardingDialogButtons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.ui.popup.ListPopup
import com.intellij.openapi.ui.popup.ListPopupStep
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.popup.list.ListPopupImpl
import com.intellij.ui.util.preferredWidth
import com.intellij.util.ui.JBUI
import java.awt.Point
import javax.swing.JButton
import javax.swing.JComponent
import javax.swing.ListCellRenderer

abstract class ProductChooserAction : JButtonAction_(null) {
  private val actionGroup = object : DefaultActionGroup() {
    override fun getChildren(e: AnActionEvent?): Array<AnAction> {
      return this@ProductChooserAction.getChildren(e)
    }
  }

  abstract fun getChildren(e: AnActionEvent?): Array<AnAction>

  protected fun productsToActions(products: List<Product>, provider: ActionsDataProvider<*>, callback: (Int) -> Unit): List<AnAction> {
    return products.map { pr -> SettingChooserItemAction(pr, provider, callback) }
  }

  override fun actionPerformed(event: AnActionEvent) {
    val comp: JComponent = event.presentation.getClientProperty(CustomComponentAction.COMPONENT_KEY) ?: return

    val children = actionGroup.getChildren(event)
    if(children.size == 1) {
      children.firstOrNull()?.actionPerformed(event)
      return
    }

    val step = createStep(actionGroup, event.dataContext, comp)
    createPopup(step).show(RelativePoint(comp, Point(0, comp.height + JBUI.scale(4))))
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
        popupComponent.preferredWidth = JBUI.scale(UiUtils.DEFAULT_BUTTON_WIDTH)

        return popupComponent
      }

    }
    result.setRequestFocus(false)
    return result
  }

  private fun createStep(actionGroup: ActionGroup, context: DataContext, widget: JComponent?): ListPopupStep<Any> {
    return JBPopupFactory.getInstance().createActionsStep(actionGroup, context, ActionPlaces.PROJECT_WIDGET_POPUP, false, false,
                                                          null, widget, false, 0, false)
  }

  override fun createButton(): JButton {
    return OnboardingDialogButtons.createButton(false)
  }
}