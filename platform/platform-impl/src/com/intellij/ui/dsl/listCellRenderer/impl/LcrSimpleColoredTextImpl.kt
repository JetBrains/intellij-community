// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.dsl.checkTrue
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.ui.UIUtil
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JList

@ApiStatus.Internal
internal class LcrSimpleColoredTextImpl(
  initParams: LcrTextInitParams, baselineAlign: Boolean, beforeGap: LcrRow.Gap,
  private val text: @Nls String,
  private val selected: Boolean,
  private val rowForeground: Color,
) :
  LcrCellBaseImpl<LcrTextInitParams>(initParams, baselineAlign, beforeGap) {

  override val type = Type.SIMPLE_COLORED_TEXT

  override fun apply(component: JComponent, enabled: Boolean, list: JList<*>, isSelected: Boolean) {
    checkTrue(type.isInstance(component))

    component as PatchedSimpleColoredComponent
    component.clear()
    component.font = initParams.font
    val baseAttributes = initParams.attributes ?: SimpleTextAttributes(SimpleTextAttributes.STYLE_PLAIN, initParams.foreground)
    val attributes = when {
      !enabled -> SimpleTextAttributes(baseAttributes.style, UIUtil.getLabelDisabledForeground())
      selected -> SimpleTextAttributes(baseAttributes.style, rowForeground)
      else -> baseAttributes
    }

    component.append(text, attributes)
    component.accessibleContext.accessibleName = initParams.accessibleName
    component.renderingHints = initParams.renderingHints

    if (initParams.speedSearchHighlighting) {
      SpeedSearchUtil.applySpeedSearchHighlighting(list, component, true, isSelected)
    }
  }
}
