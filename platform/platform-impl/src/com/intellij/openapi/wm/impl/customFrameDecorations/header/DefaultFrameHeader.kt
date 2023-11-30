// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationTitle
import com.intellij.util.ui.GridBag
import com.intellij.util.ui.JBUI
import java.awt.Frame
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import javax.swing.JFrame

internal class DefaultFrameHeader(frame: JFrame, isForDockContainerProvider: Boolean) : FrameHeader(frame) {
  private val customDecorationTitle = CustomDecorationTitle(frame, isForDockContainerProvider = isForDockContainerProvider)

  init {
    layout = GridBagLayout()

    updateCustomTitleBar()

    productIcon.border = JBUI.Borders.empty(V, H, V, H)
    customDecorationTitle.view.border = JBUI.Borders.empty(V, 0, V, H)

    val gb = GridBag().setDefaultFill(GridBagConstraints.VERTICAL).setDefaultAnchor(GridBagConstraints.WEST)
    add(productIcon, gb.next())
    add(customDecorationTitle.view, gb.next().fillCell().weightx(1.0))
    buttonPanes?.let { add(it.getView(), gb.next().anchor(GridBagConstraints.EAST)) }

    setCustomFrameTopBorder(isTopNeeded = { state != Frame.MAXIMIZED_VERT && state != Frame.MAXIMIZED_BOTH })
  }

  override fun updateActive() {
    customDecorationTitle.setActive(isActive)
    super.updateActive()
  }
}
