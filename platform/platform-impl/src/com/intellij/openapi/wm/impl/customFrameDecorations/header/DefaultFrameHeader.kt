// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.openapi.wm.impl.customFrameDecorations.header.titleLabel.CustomDecorationTitle
import com.intellij.ui.awt.RelativeRectangle
import com.intellij.ui.scale.ScaleContext
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBImageIcon
import net.miginfocom.swing.MigLayout
import java.util.*
import javax.swing.Icon
import javax.swing.JFrame

class DefaultFrameHeader(frame: JFrame) : FrameHeader(frame){
  private val customDecorationTitle: CustomDecorationTitle = CustomDecorationTitle(frame) {updateCustomDecorationHitTestSpots()}

  init {
    layout = MigLayout("novisualpadding, ins 0, fillx, gap 0, hmin $MIN_HEIGHT", "$H_GAP[min!]$H_GAP[][pref!]")

    add(productIcon)
    add(customDecorationTitle.getView(), "wmin 0, left, growx, center")
    add(buttonPanes.getView(), "top, wmin pref")
  }

  override fun updateActive() {
    customDecorationTitle.setActive(myActive)
    super.updateActive()
  }

  override fun getHitTestSpots(): ArrayList<RelativeRectangle> {
    val hitTestSpots = ArrayList<RelativeRectangle>()

    hitTestSpots.add(RelativeRectangle(productIcon))
    hitTestSpots.add(RelativeRectangle(buttonPanes.getView()))
    hitTestSpots.addAll(customDecorationTitle.getListenerBounds())

    return hitTestSpots
  }

  override fun getFrameIcon(ctx: ScaleContext): Icon {
    val image = ImageUtil.ensureHiDPI(frame.iconImage, ctx) ?: return super.getFrameIcon(ctx)
    return JBImageIcon(ImageUtil.scaleImage(image, iconSize, iconSize))
  }
}