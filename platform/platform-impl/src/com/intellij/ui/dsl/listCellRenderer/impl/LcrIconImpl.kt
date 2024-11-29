// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.dsl.checkTrue
import com.intellij.ui.dsl.listCellRenderer.LcrIconInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.util.IconUtil
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JList

@ApiStatus.Internal
internal class LcrIconImpl(initParams: LcrIconInitParams, baselineAlign: Boolean, beforeGap: LcrRow.Gap, val icon: Icon) :
  LcrCellBaseImpl<LcrIconInitParams>(initParams, baselineAlign, beforeGap) {

  override val type = Type.ICON

  override fun apply(component: JComponent, enabled: Boolean, list: JList<*>, isSelected: Boolean) {
    checkTrue(type.isInstance(component))

    component as JLabel
    component.icon = if (enabled) icon else IconUtil.desaturate(icon)
    component.accessibleContext.accessibleName = initParams.accessibleName
  }
}
