package com.intellij.grazie.ide.ui.components.utils

import java.awt.Component
import javax.swing.DefaultListCellRenderer
import javax.swing.JList

internal class ConfigurableListCellRenderer<T>(val configure: (DefaultListCellRenderer, T) -> Unit) : DefaultListCellRenderer() {
  override fun getListCellRendererComponent(list: JList<*>?, value: Any?, index: Int, isSelected: Boolean,
                                            cellHasFocus: Boolean): Component {
    val component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus) as DefaultListCellRenderer
    configure(component, value as T)
    return component
  }
}