// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.checkTrue
import com.intellij.ui.dsl.listCellRenderer.LcrOnOffButtonInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JList

@ApiStatus.Internal
internal class LcrOnOffButtonImpl(initParams: LcrOnOffButtonInitParams, baselineAlign: Boolean, beforeGap: LcrRow.Gap) :
  LcrCellBaseImpl<LcrOnOffButtonInitParams>(initParams, baselineAlign, beforeGap) {

  override val type = Type.ON_OFF_BUTTON

  override fun apply(component: JComponent, enabled: Boolean, list: JList<*>, isSelected: Boolean) {
    checkTrue(type.isInstance(component))

    component as OnOffButton
    component.accessibleContext.accessibleName = initParams.accessibleName
    component.isSelected = initParams.isSelected
  }
}
