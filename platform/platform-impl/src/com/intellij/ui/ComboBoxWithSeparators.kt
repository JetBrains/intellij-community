// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import com.intellij.ide.ui.laf.darcula.ui.DarculaJBPopupComboPopup
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.popup.ListItemDescriptor
import com.intellij.openapi.ui.popup.util.GroupedValue
import com.intellij.openapi.ui.popup.util.PopupUtil
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.popup.list.GroupedItemsListRenderer
import com.intellij.ui.popup.list.SelectablePanel
import com.intellij.util.ui.JBUI
import javax.swing.DefaultComboBoxModel
import javax.swing.Icon
import javax.swing.JLabel
import javax.swing.JList

/**
 * Simple [ComboBox] extension supporting separators.
 *
 * - Use `addSeparator(caption)` to add a separator automatically before the next element.
 * - Use `addValue(value)` to populate the combo.
 */
abstract class ComboBoxWithSeparators<T> : ComboBox<ComboBoxGroupedEntry<T>>() {

  init {
    model = MyComboBoxModel()
    renderer = ComboBoxGroupedElementsRenderer()
    isSwingPopup = false
  }

  fun addSeparator(@NlsContexts.Separator text: String) {
    (model as MyComboBoxModel).addSeparator(text)
  }

  fun addValue(value: T) {
    (model as MyComboBoxModel).addEntry(value)
  }

  abstract fun getPresentableText(value: T): @NlsContexts.ListItem String
  open fun getSecondaryText(value: T): @NlsContexts.ListItem String = ""
  open fun getIcon(value: T): Icon? = null

  private inner class MyComboBoxModel: DefaultComboBoxModel<ComboBoxGroupedEntry<T>>() {
    private var nextSeparator: @NlsContexts.Separator String? = null

    fun addSeparator(@NlsContexts.Separator text: String) {
      nextSeparator = text
    }

    fun addEntry(value: T) {
      addElement(ComboBoxGroupedEntry(value, nextSeparator))
      nextSeparator = null
    }
  }

  private fun hasSeparator(value: ComboBoxGroupedEntry<T>): Boolean {
    return (popup as? DarculaJBPopupComboPopup<*>)?.popup?.isSeparatorAboveOf(value) == true
  }

  @NlsContexts.Separator
  private fun getSeparatorCaption(value: ComboBoxGroupedEntry<T>): String? {
    return (popup as? DarculaJBPopupComboPopup<*>)?.popup?.getCaptionAboveOf(value)
  }

  fun getSelectedValue(): T? {
    @Suppress("UNCHECKED_CAST")
    return when (selectedItem) {
      is ComboBoxGroupedEntry<*> -> (selectedItem as ComboBoxGroupedEntry<*>).myItem as T?
      else -> null
    }
  }

  private inner class ComboBoxGroupedElementsRenderer : GroupedItemsListRenderer<ComboBoxGroupedEntry<T>>(object : ListItemDescriptor<ComboBoxGroupedEntry<T>> {
    override fun getTextFor(value: ComboBoxGroupedEntry<T>): String? {
      return getPresentableText(value.myItem)
    }

    override fun getTooltipFor(value: ComboBoxGroupedEntry<T>): String? {
      return null
    }

    override fun getIconFor(value: ComboBoxGroupedEntry<T>): Icon? {
      return getIcon(value.myItem)
    }

    override fun hasSeparatorAboveOf(value: ComboBoxGroupedEntry<T>): Boolean {
      return hasSeparator(value)
    }

    override fun getCaptionAboveOf(value: ComboBoxGroupedEntry<T>): String? {
      return getSeparatorCaption(value)
    }
  }) {

    override fun customizeComponent(list: JList<out ComboBoxGroupedEntry<T>>,
                                    value: ComboBoxGroupedEntry<T>,
                                    isSelected: Boolean) {
      if (mySeparatorComponent.isVisible && mySeparatorComponent is GroupHeaderSeparator) {
        (mySeparatorComponent as GroupHeaderSeparator).setHideLine(myCurrentIndex == 0)
      }

      if (myCurrentIndex == -1) {
        mySeparatorComponent.isVisible = false
        myComponent.border = JBUI.Borders.empty()
      }
      else {
        if (ExperimentalUI.isNewUI() && myComponent is SelectablePanel) {
          PopupUtil.configListRendererFlexibleHeight(myComponent as SelectablePanel)
        } else {
          myComponent.border = defaultItemComponentBorder
        }
      }
    }

    override fun createSeparator(): SeparatorWithText {
      val labelInsets = if (ExperimentalUI.isNewUI()) JBUI.CurrentTheme.Popup.separatorLabelInsets()
      else defaultItemComponentBorder.getBorderInsets(JLabel())
      return GroupHeaderSeparator(labelInsets)
    }
  }
}

class ComboBoxGroupedEntry<T>(val myItem: T, @NlsContexts.Separator private val mySeparatorText: String?): GroupedValue {
  override fun getSeparatorText(): String? = mySeparatorText
}