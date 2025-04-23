// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.checkTrue
import com.intellij.ui.dsl.listCellRenderer.LcrSwitcherInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JList

@ApiStatus.Internal
internal class LcrSwitcherImpl(initParams: LcrSwitcherInitParams, baselineAlign: Boolean, beforeGap: LcrRow.Gap, val isOn: Boolean) :
  LcrCellBaseImpl<LcrSwitcherInitParams>(initParams, baselineAlign, beforeGap) {

  override val type = Type.SWITCHER

  override fun apply(component: JComponent, enabled: Boolean, list: JList<*>, isSelected: Boolean) {
    checkTrue(type.isInstance(component))

    component as OnOffButton
    component.isSelected = isOn
    component.isEnabled = enabled
    component.accessibleContext.accessibleName = initParams.accessibleName
  }
}
