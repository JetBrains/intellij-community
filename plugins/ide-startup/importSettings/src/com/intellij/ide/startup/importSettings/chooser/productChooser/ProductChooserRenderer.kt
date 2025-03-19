// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.startup.importSettings.chooser.settingChooser.SettingChooserItemAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import javax.accessibility.AccessibleContext
import javax.swing.*

class ProductChooserRenderer : ListCellRenderer<PopupFactoryImpl.ActionItem> {
  private val component = ProductComponent()
  private val withSeparator = ProductComponentWithSeparator()

  override fun getListCellRendererComponent(list: JList<out PopupFactoryImpl.ActionItem>,
                                            value: PopupFactoryImpl.ActionItem,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val model = list.model as? ListPopupModel<*>

    return if (model?.isSeparatorAboveOf(value) == true) {
      val separator = getSeparator(list, value)

      withSeparator.updateSeparator(separator?.text, index == 0)
      withSeparator.update(value, isSelected)
      withSeparator
    }
    else {
      component.update(value, isSelected)
      component
    }.getComponent()

  }


  private fun getSeparator(list: JList<out PopupFactoryImpl.ActionItem>?, value: PopupFactoryImpl.ActionItem?): ListSeparator? {
    val model = list?.model as? ListPopupModel<*> ?: return null
    val hasSeparator = model.isSeparatorAboveOf(value)
    if (!hasSeparator) {
      return null
    }
    return ListSeparator(model.getCaptionAboveOf(value))
  }

  private data class Obj(val action: AnAction) {
    var name: @Nls String? = null
    var comment: @Nls String? = null
    var icon: Icon? = null

    init {
      if (action is ConfigAction) {
        name = action.templatePresentation.text
        icon = action.templatePresentation.icon
      }
      else if (action is SettingChooserItemAction) {

        val product = action.product
        val provider = action.provider

        name = product.name
        comment = provider.getComment(product)
        icon = provider.getProductIcon(product.id)
      }
    }
  }

  private open class ProductComponent() {
    protected var result: SelectablePanel
    private lateinit var nameLbl: JEditorPane

    private lateinit var icon: JLabel
    private lateinit var path: JEditorPane

    open fun getComponent(): JComponent = result

    fun update(value: PopupFactoryImpl.ActionItem,
               isSelected: Boolean) {
      val action = Obj(value.action)
      icon.icon = action.icon
      icon.isVisible = action.icon != null

      nameLbl.text = action.name ?: ""
      nameLbl.foreground = if (isSelected) NamedColorUtil.getListSelectionForeground(true) else UIUtil.getLabelForeground()

      path.text = action.comment
      path.isVisible = action.comment != null

      result.selectionColor = if (isSelected) ListPluginComponent.SELECTION_COLOR else null
      result.accessibleContext.accessibleName = AccessibleContextUtil.combineAccessibleStrings(action.name, ", ", action.comment)
    }

    init {
      val content = panel {
        row {
          icon = label("").customize(UnscaledGaps(right = 8)).align(AlignY.TOP).component
          panel {
            row {
              nameLbl = text("")
                .align(AlignY.TOP)
                .customize(UnscaledGaps(right = 8, bottom = 0))
                .applyToComponent {
                  foreground = UIUtil.getListForeground()
                }.component
            }
            row {
              path = comment("")
                .align(AlignY.TOP)
                .customize(UnscaledGaps(top = 0))
                .component
            }
          }
        }
      }.apply {
        border = JBUI.Borders.empty(8, 0)
        isOpaque = false
      }

      result = SelectablePanel.wrap(content, JBUI.CurrentTheme.Popup.BACKGROUND)
      PopupUtil.configListRendererFlexibleHeight(result)
    }
  }

  private class ProductComponentWithSeparator : ProductComponent() {
    private val res = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())

    private val separator = JPanel(BorderLayout()).apply {
      border = JBUI.Borders.empty()
      isOpaque = true
      background = JBUI.CurrentTheme.Popup.BACKGROUND
      add(res)
    }

    fun updateSeparator(text: @NlsContexts.Separator String? = null, hideLine: Boolean = false) {
      res.caption = text
      res.setHideLine(hideLine)

      res.revalidate()
    }

    private val withSeparator = object : NonOpaquePanel(BorderLayout()) {
      override fun getAccessibleContext(): AccessibleContext {
        if (accessibleContext == null) {
          accessibleContext = object : AccessibleJPanel() {
            override fun getAccessibleName(): String? = result.accessibleContext.accessibleName
          }
        }
        return accessibleContext
      }
    }.apply {
      border = JBUI.Borders.empty()
      add(separator, BorderLayout.NORTH)
      add(result, BorderLayout.CENTER)
    }

    override fun getComponent(): JComponent {
      return withSeparator
    }
  }
}
