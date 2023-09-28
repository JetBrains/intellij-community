// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.importSettings.chooser.ui

import com.intellij.icons.AllIcons
import com.intellij.importSettings.chooser.actions.ConfigAction
import com.intellij.importSettings.chooser.actions.SettingChooserItemAction
import com.intellij.ui.SeparatorComponent
import com.intellij.ui.dsl.builder.AlignY
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.popup.PopupFactoryImpl
import com.intellij.ui.popup.list.ListPopupModel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import java.awt.Component
import javax.swing.*

class ProductChooserRenderer_ : ListCellRenderer<PopupFactoryImpl.ActionItem> {
  private val title = JLabel()
  private lateinit var icn: JLabel
  private lateinit var txt: JLabel
  private lateinit var addTxt: JEditorPane

  private val line = panel {
    row {
      icn = icon(AllIcons.Chooser.Bottom).align(AlignY.TOP).component
      panel {
        row {
          txt = label("").component
        }
        row {
          addTxt = comment("").component
        }
      }
    }
  }

  private val separator = SeparatorComponent(5, JBUI.CurrentTheme.Popup.separatorColor(), null)

  private val separatorPane = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(separator)
    add(title)

    isOpaque = false
  }

  private val pane = JPanel().apply {
    layout = BoxLayout(this, BoxLayout.Y_AXIS)
    add(separatorPane)
    add(line)
  }

  private val nth = JLabel("Nothing to show")

  override fun getListCellRendererComponent(list: JList<out PopupFactoryImpl.ActionItem>,
                                            value: PopupFactoryImpl.ActionItem,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val model = list.model as? ListPopupModel<*>

    val caption = model?.getCaptionAboveOf(value)
    separatorPane.isVisible = caption?.let {
      title.text = it
      true
    } ?: false

    separator.isVisible = separatorPane.isVisible && index != 0

    pane.background = list.background
    line.background = if (isSelected) UIUtil.getListSelectionBackground(true) else list.background
    line.foreground = if (isSelected) UIUtil.getListSelectionForeground(true) else list.foreground

    if(value.action is ConfigAction) {
      val action = (value.action as ConfigAction)
      val config = action.config

      txt.text = config.name
      addTxt.text = config.path
      icn.icon  = action.service.getProductIcon(config.id)

      return pane
    }

    if(value.action is SettingChooserItemAction) {
      val action = value.action as SettingChooserItemAction
      val product = action.product
      val provider = action.provider

      txt.text = product.name
      addTxt.text = product.lastUsage.toString()
      icn.icon  = provider.getProductIcon(product.id)

      return pane
    }

    return nth
  }
}
