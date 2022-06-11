// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.wm.impl.customFrameDecorations.ResizableCustomFrameTitleButtons
import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationTitle
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.util.ui.JBUI
import com.jetbrains.CustomWindowDecoration.*
import net.miginfocom.swing.MigLayout
import java.awt.Frame
import javax.swing.JFrame

internal class DefaultFrameHeader(frame: JFrame) : FrameHeader(frame) {
  private val customDecorationTitle = CustomDecorationTitle(frame)

  init {
    layout = MigLayout("novisualpadding, ins 0, fillx, gap 0", "[min!][][pref!]")

    updateCustomDecorationHitTestSpots()

    productIcon.border = JBUI.Borders.empty(V, H, V, H)
    customDecorationTitle.view.border = JBUI.Borders.empty(V, 0, V, H)

    add(productIcon)
    add(customDecorationTitle.view, "wmin 0, left, growx, center")
    add(buttonPanes.getView(), "top, wmin pref")

    setCustomFrameTopBorder({ myState != Frame.MAXIMIZED_VERT && myState != Frame.MAXIMIZED_BOTH })
  }

  override fun updateActive() {
    customDecorationTitle.setActive(myActive)
    super.updateActive()
  }

  override fun getHitTestSpots(): List<Pair<RelativeRectangle, Int>> {
    val buttons = buttonPanes as ResizableCustomFrameTitleButtons
    val hitTestSpots = ArrayList<Pair<RelativeRectangle, Int>>()
    hitTestSpots.add(Pair(RelativeRectangle(productIcon), OTHER_HIT_SPOT))
    hitTestSpots.add(Pair(RelativeRectangle(buttons.minimizeButton), MINIMIZE_BUTTON))
    hitTestSpots.add(Pair(RelativeRectangle(buttons.maximizeButton), MAXIMIZE_BUTTON))
    hitTestSpots.add(Pair(RelativeRectangle(buttons.restoreButton), MAXIMIZE_BUTTON))
    hitTestSpots.add(Pair(RelativeRectangle(buttons.closeButton), CLOSE_BUTTON))
    hitTestSpots.addAll(customDecorationTitle.getBoundList().map { Pair(it, OTHER_HIT_SPOT) })
    return hitTestSpots
  }
}
