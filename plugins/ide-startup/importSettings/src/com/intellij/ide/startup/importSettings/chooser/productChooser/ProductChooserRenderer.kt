// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("DuplicatedCode")

package com.intellij.ide.startup.importSettings.chooser.productChooser

import com.intellij.ide.plugins.newui.ListPluginComponent
import com.intellij.ide.startup.importSettings.chooser.settingChooser.SettingChooserItemAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.ui.popup.ListSeparator
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.ui.GroupHeaderSeparator
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.EmptySpacingConfiguration
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBFont
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.NamedColorUtil
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Component
import javax.swing.*

@Suppress("DuplicatedCode")
class ProductChooserRenderer : ListCellRenderer<PopupFactoryImpl.ActionItem> {
  override fun getListCellRendererComponent(list: JList<out PopupFactoryImpl.ActionItem>,
                                            value: PopupFactoryImpl.ActionItem,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    return createRecentProjectPane(value, isSelected, getSeparator(list, value), index == 0)
  }

  private fun getSeparator(list: JList<out PopupFactoryImpl.ActionItem>?, value: PopupFactoryImpl.ActionItem?): ListSeparator? {
    val model = list?.model as? ListPopupModel<*> ?: return null
    val hasSeparator = model.isSeparatorAboveOf(value)
    if (!hasSeparator) {
      return null
    }
    return ListSeparator(model.getCaptionAboveOf(value))
  }

  data class Obj(val action: AnAction) {
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

  private fun createRecentProjectPane(value: PopupFactoryImpl.ActionItem,
                                      isSelected: Boolean,
                                      separator: ListSeparator?,
                                      hideLine: Boolean): JComponent {
    val action = Obj(value.action)

    lateinit var nameLbl: JLabel
    var pathLbl: JLabel? = null

    val content = panel {
      customizeSpacingConfiguration(EmptySpacingConfiguration()) {
        row {
          action.icon?.let {
            icon(it)
              .align(AlignY.TOP)
              .customize(UnscaledGaps(right = 8))
          }

          panel {
            row {
              nameLbl = label(action.name ?: "")
                .applyToComponent {
                  foreground = if (isSelected) NamedColorUtil.getListSelectionForeground(true) else UIUtil.getListForeground()
                }.component
            }
            action.comment?.let { txt ->
              row {
                pathLbl = label(txt)
                  .customize(UnscaledGaps(top = 4))
                  .applyToComponent {
                    font = JBFont.smallOrNewUiMedium()
                    foreground = UIUtil.getLabelInfoForeground()
                  }.component

              }
            }
          }.align(AlignY.CENTER).customize(UnscaledGaps(top = 1))
        }
      }
    }.apply {
      border = JBUI.Borders.empty(8, 0)
      isOpaque = false
    }

    val result = SelectablePanel.wrap(content, JBUI.CurrentTheme.Popup.BACKGROUND)
    PopupUtil.configListRendererFlexibleHeight(result)
    if (isSelected) {
      result.selectionColor = ListPluginComponent.SELECTION_COLOR
    }

    pathLbl?.let {
      AccessibleContextUtil.setCombinedName(result, nameLbl, " - ", it)
      AccessibleContextUtil.setCombinedDescription(result, nameLbl, " - ", it)
    }

    if (separator == null) {
      return result
    }

    val res = NonOpaquePanel(BorderLayout())
    res.border = JBUI.Borders.empty()
    res.add(createSeparator(separator, hideLine), BorderLayout.NORTH)
    res.add(result, BorderLayout.CENTER)
    return res
  }
}

private fun createSeparator(separator: ListSeparator, hideLine: Boolean): JComponent {
  val res = GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
  res.caption = separator.text
  res.setHideLine(hideLine)

  val panel = JPanel(BorderLayout())
  panel.border = JBUI.Borders.empty()
  panel.isOpaque = true
  panel.background = JBUI.CurrentTheme.Popup.BACKGROUND
  panel.add(res)

  return panel
}