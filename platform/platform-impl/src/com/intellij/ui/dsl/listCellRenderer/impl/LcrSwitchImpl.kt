// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.dsl.listCellRenderer.impl

import com.intellij.ui.components.OnOffButton
import com.intellij.ui.dsl.checkTrue
import com.intellij.ui.dsl.listCellRenderer.LcrSwitchInitParams
import com.intellij.ui.dsl.listCellRenderer.LcrRow
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent
import javax.swing.JList

@ApiStatus.Internal
internal class LcrSwitchImpl(initParams: LcrSwitchInitParams, baselineAlign: Boolean, beforeGap: LcrRow.Gap, val isOn: Boolean) :
  LcrCellBaseImpl<LcrSwitchInitParams>(initParams, baselineAlign, beforeGap) {

  override val type = Type.SWITCH

  override fun apply(component: JComponent, enabled: Boolean, list: JList<*>, isSelected: Boolean) {
    checkTrue(type.isInstance(component))

    component as OnOffButton
    component.isSelected = isOn
    component.isEnabled = enabled
    component.accessibleContext.accessibleName = initParams.accessibleName
    component.ipad = JBUI.insets(2, 1)
  }
}
