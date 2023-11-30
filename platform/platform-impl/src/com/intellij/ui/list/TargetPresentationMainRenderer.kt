// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ui.list

import com.intellij.navigation.LocationPresentation
import com.intellij.platform.backend.presentation.TargetPresentation
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.SimpleTextAttributes.*
import com.intellij.util.ui.UIUtil
import java.util.function.Function
import javax.swing.JList

@Deprecated("Use GotoTargetRendererNew.createTargetPresentationRenderer instead")
internal class TargetPresentationMainRenderer<T>(
  private val presentationProvider: Function<in T, out TargetPresentation>
) : ColoredListCellRenderer<T>() {

  override fun customizeCellRenderer(list: JList<out T>, value: T, index: Int, selected: Boolean, hasFocus: Boolean) {
    val presentation = presentationProvider.apply(value)
    background = if (selected) {
      UIUtil.getListSelectionBackground(hasFocus)
    }
    else {
      presentation.backgroundColor ?: UIUtil.getListBackground()
    }

    icon = presentation.icon

    val presentableAttributes = presentation.presentableTextAttributes?.let(::fromTextAttributes)
                                ?: SimpleTextAttributes(STYLE_PLAIN, UIUtil.getListForeground(selected, hasFocus))
    append(presentation.presentableText, presentableAttributes)

    val containerText = presentation.containerText
    if (containerText != null) {
      val containerTextAttributes = presentation.containerTextAttributes?.let {
        merge(fromTextAttributes(it), GRAYED_ATTRIBUTES)
      } ?: GRAYED_ATTRIBUTES
      append(LocationPresentation.DEFAULT_LOCATION_PREFIX, GRAYED_ATTRIBUTES)
      append(containerText, containerTextAttributes)
      append(LocationPresentation.DEFAULT_LOCATION_SUFFIX, GRAYED_ATTRIBUTES)
    }
  }
}
