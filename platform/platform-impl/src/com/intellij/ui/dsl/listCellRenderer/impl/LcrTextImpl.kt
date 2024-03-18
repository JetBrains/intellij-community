// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.checkTrue
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.ui.dsl.listCellRenderer.LcrTextInitParams
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.Color
import javax.swing.JComponent
import javax.swing.JLabel

@ApiStatus.Internal
internal class LcrTextImpl(initParams: LcrTextInitParams, baselineAlign: Boolean, beforeGap: LcrRow.Gap,
                           private val text: @Nls String,
                           private val selected: Boolean,
                           private val rowForeground: Color) :
  LcrCellBaseImpl<LcrTextInitParams>(initParams, baselineAlign, beforeGap) {

  override val type = Type.TEXT

  override fun apply(component: JComponent) {
    checkTrue(type.isInstance(component))

    component as JLabel
    component.text = text
    component.font = initParams.font
    component.foreground = if (selected) rowForeground else initParams.foreground
    component.accessibleContext.accessibleName = initParams.accessibleName
  }
}
