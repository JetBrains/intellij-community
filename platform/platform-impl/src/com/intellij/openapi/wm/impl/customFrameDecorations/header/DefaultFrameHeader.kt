// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.wm.impl.customFrameDecorations.header

import com.intellij.ui.awt.RelativeRectangle
import net.miginfocom.swing.MigLayout
import java.awt.Dimension
import java.awt.Rectangle
import java.util.ArrayList
import javax.swing.JFrame
import javax.swing.JLabel
import javax.swing.UIManager

class DefaultFrameHeader(frame: JFrame) : FrameHeader(frame){
  private val titleLabel = JLabel()

  init {
    layout = MigLayout("novisualpadding, ins 0, fillx, gap 0", "$H_GAP[min!]$H_GAP[][pref!]", "")
    titleLabel.text = frame.title

    add(productIcon)
    add(titleLabel, "wmin 0, left, hmin $MIN_HEIGHT")
    add(buttonPanes.getView(), "top, wmin pref")

  }

  override fun updateActive() {
    titleLabel.foreground = if (myActive) UIManager.getColor("Panel.foreground") else UIManager.getColor("Label.disabledForeground")
    super.updateActive()
  }

  override fun addNotify() {
    super.addNotify()
    titleLabel.text = frame.title
  }

  override fun getHitTestSpots(): ArrayList<RelativeRectangle> {
    val hitTestSpots = ArrayList<RelativeRectangle>()

    hitTestSpots.add(RelativeRectangle(productIcon))
    hitTestSpots.add(RelativeRectangle(buttonPanes.getView()))

    return hitTestSpots
  }
}