// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.render

import com.intellij.ui.ExperimentalUI
import org.jetbrains.annotations.ApiStatus
import java.awt.Component
import javax.swing.JList
import javax.swing.ListCellRenderer

/**
 * Combines multiple internal renderers into a single composite renderer.
 * The internal renderer is selected by [selectRenderer] based on the provided value.
 * For performance reasons, the internal renderers must be created once outside
 * this method.
 */
@ApiStatus.Internal
abstract class CompositeRenderer<T> : ListCellRenderer<T?>, ExperimentalUI.NewUIComboBoxRenderer {

  // todo final
  override fun getListCellRendererComponent(
    list: JList<out T?>,
    value: T?,
    index: Int,
    isSelected: Boolean,
    cellHasFocus: Boolean,
  ): Component {
    @Suppress("UNCHECKED_CAST")
    val renderer = selectRenderer(value) as ListCellRenderer<T?>
    return renderer.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)
  }

  abstract fun selectRenderer(value: T?): ListCellRenderer<*>
}
