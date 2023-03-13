// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaJBPopupComboPopup
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.ListSeparator
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

abstract class GroupedComboBoxRenderer<T>(val combo: ComboBox<T>) : GroupedElementsRenderer(), ListCellRenderer<T> {

  open fun getText(item: T): @NlsContexts.ListItem String = ""
  open fun getSecondaryText(item: T): @Nls String? = null
  open fun getIcon(item: T): Icon? = null

  private lateinit var coloredComponent: SimpleColoredComponent

  open val maxWidth: Int = -1

  override fun layout() {
    myRendererComponent.add(mySeparatorComponent, BorderLayout.NORTH)
    val centerComponent: JComponent = object : NonOpaquePanel(itemComponent) {
      override fun getPreferredSize(): Dimension {
        return super.getPreferredSize().let {
          if (maxWidth > 0) it.width = maxWidth
          it.height = JBUI.CurrentTheme.List.rowHeight()
          if (!ExperimentalUI.isNewUI()) {
            val insets = ComboBoxPopup.COMBO_ITEM_BORDER.borderInsets
            it.height += insets.bottom + insets.top
          }
          UIUtil.updateListRowHeight(it)
        }
      }
    }
    myRendererComponent.add(centerComponent, BorderLayout.CENTER)
  }

  open fun customize(item: SimpleColoredComponent, value: T, index: Int) {
    val text = getText(value)
    item.append(text)

    val secondaryText = getSecondaryText(value)
    if (secondaryText != null) {
      item.append(" $secondaryText", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }

    item.icon = getIcon(value)
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

  // TODO: remove when old UI is not supported
  @Suppress("UNNECESSARY_SAFE_CALL")
  private val enabled: Boolean
    get() = combo?.isEnabled == true

  override fun getBackground(): Color = if (enabled) UIUtil.getListBackground(false, false) else UIUtil.getComboBoxDisabledBackground()
  override fun getForeground(): Color = if (enabled) UIUtil.getListForeground(false, false) else UIUtil.getComboBoxDisabledForeground()
  override fun getSelectionBackground(): Color = UIUtil.getListSelectionBackground(true)
  override fun getSelectionForeground(): Color = UIUtil.getListSelectionForeground(true)

  abstract fun separatorFor(value: T): ListSeparator?

  override fun getListCellRendererComponent(list: JList<out T>?,
                                            value: T,
                                            index: Int,
                                            isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val popup = (combo.popup as? DarculaJBPopupComboPopup<*>)?.popup

    coloredComponent.apply {
      clear()
      customize(this, value, index)
    }

    mySeparatorComponent.apply {
      isVisible = popup?.isSeparatorAboveOf(value) == true
      if (isVisible) {
        caption = popup!!.getCaptionAboveOf(value)
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