// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationTitle
import com.intellij.util.ui.JBUI
import net.miginfocom.swing.MigLayout
import java.awt.Frame
import javax.swing.JFrame

internal class DefaultFrameHeader(frame: JFrame, isForDockContainerProvider: Boolean) : FrameHeader(frame) {
  private val customDecorationTitle = CustomDecorationTitle(frame, isForDockContainerProvider = isForDockContainerProvider)

  init {
    layout = MigLayout("novisualpadding, ins 0, fillx, gap 0", "[min!][][pref!]")

    updateCustomTitleBar()

    productIcon.border = JBUI.Borders.empty(V, H, V, H)
    customDecorationTitle.view.border = JBUI.Borders.empty(V, 0, V, H)

    add(productIcon)
    add(customDecorationTitle.view, "wmin 0, left, growx, center")

    setCustomFrameTopBorder(isTopNeeded = { myState != Frame.MAXIMIZED_VERT && myState != Frame.MAXIMIZED_BOTH })
  }

  override fun updateActive() {
    customDecorationTitle.setActive(myActive)
    super.updateActive()
  }
}
