// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaJBPopupComboPopup
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.ui.popup.list.ComboBoxPopup
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import com.intellij.util.ui.accessibility.AccessibleContextUtil
import org.jetbrains.annotations.Nls
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Component
import java.awt.Dimension
import javax.swing.*
import javax.swing.border.CompoundBorder

open class GroupedComboBoxRenderer(val combo: ComboBox<out Item>) : GroupedElementsRenderer(), ListCellRenderer<GroupedComboBoxRenderer.Item> {

  interface Item {
    fun getText(): @NlsContexts.ListItem String = ""
    fun getSecondaryText(): @Nls String? = null
    fun getIcon(): Icon? = null

    fun isSeparator(): Boolean = false
    fun getSeparatorText(): @NlsContexts.Separator String = ""
  }

  private lateinit var coloredComponent: SimpleColoredComponent

  open val maxWidth: Int = -1

  override fun layout() {
    myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH)
    val centerComponent: JComponent = object : NonOpaquePanel(itemComponent) {
      override fun getPreferredSize(): Dimension {
        val dimension = UIUtil.updateListRowHeight(super.getPreferredSize())
        return when (maxWidth) {
          -1 -> dimension
          else -> Dimension(maxWidth, dimension.height)
        }
      }
    }
    myRendererComponent.add(centerComponent, BorderLayout.CENTER)
  }

  open fun customize(item: SimpleColoredComponent, value: Item, index: Int) {
    val text = value.getText()
    item.append(text)

    val secondaryText = value.getSecondaryText()
    if (secondaryText != null) {
      item.append(" $secondaryText", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    item.icon = value.getIcon()
  }

  override fun createSeparator(): SeparatorWithText = when {
    ExperimentalUI.isNewUI() -> GroupHeaderSeparator(JBUI.CurrentTheme.Popup.separatorLabelInsets())
    else -> GroupHeaderSeparator(defaultItemComponentBorder.getBorderInsets(JLabel())).apply {
      border = ComboBoxPopup.COMBO_ITEM_BORDER
    }
  }

  override fun createItemComponent(): JComponent {
    coloredComponent = SimpleColoredComponent()
    return layoutComponent(coloredComponent)
  }

  private fun layoutComponent(component: JComponent): JComponent = when {
    ExperimentalUI.isNewUI() -> SelectablePanel.wrap(component)
    else -> JBUI.Panels.simplePanel(component).apply {
      border = JBUI.Borders.empty(20, 16)
    }
  }

  fun getPopup(): ComboBoxPopup<out Any>? {
    return (combo.popup as? DarculaJBPopupComboPopup<*>)?.popup
  }
  override fun getBackground(): Color = UIUtil.getListBackground(false, false)
  override fun getForeground(): Color = UIUtil.getListForeground(false, false)
  override fun getSelectionBackground(): Color = UIUtil.getListSelectionBackground(true)
  override fun getSelectionForeground(): Color = UIUtil.getListSelectionForeground(true)

  override fun getListCellRendererComponent(list: JList<out Item>?,
                                            value: Item,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    coloredComponent.apply {
      clear()
      customize(this, value, index)
    }

    mySeparatorComponent.apply {
      isVisible = getPopup()?.isSeparatorAboveOf(value) == true
      if (isVisible) {
        caption = getPopup()!!.getCaptionAboveOf(value)
        (this as GroupHeaderSeparator).setHideLine(index == 0)
      }
    }

    if (index == -1) {
      // Element shown in the combo: no separator & no border
      mySeparatorComponent.isVisible = false
      itemComponent.border = JBUI.Borders.empty()
    } else {
      if (ExperimentalUI.isNewUI() && itemComponent is SelectablePanel) {
        PopupUtil.configListRendererFlexibleHeight(itemComponent as SelectablePanel)
      } else {
        itemComponent.border = CompoundBorder(defaultItemComponentBorder, ComboBoxPopup.COMBO_ITEM_BORDER)
      }
    }

    AccessibleContextUtil.setName(myRendererComponent, coloredComponent)
    AccessibleContextUtil.setDescription(myRendererComponent, coloredComponent)

    list?.let { myRendererComponent.background = it.background }
    updateSelection(isSelected, itemComponent, coloredComponent)

    if (ExperimentalUI.isNewUI() && itemComponent is SelectablePanel) {
      (itemComponent as SelectablePanel).selectionColor = when (isSelected) {
        true -> JBUI.CurrentTheme.List.background(true, true)
        false -> null
      }
    }

    return myRendererComponent
  }
}