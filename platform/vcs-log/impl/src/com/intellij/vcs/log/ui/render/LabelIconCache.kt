// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.log.ui.render

import com.intellij.util.containers.SLRUMap
import com.intellij.util.ui.JBUI
import java.awt.Color
import javax.swing.JComponent

class LabelIconCache {
  private val cache = SLRUMap<LabelIconId, LabelIcon>(40, 20)

  fun getIcon(component: JComponent, height: Int, bgColor: Color, colors: List<Color>): LabelIcon {
    val id = LabelIconId(JBUI.sysScale(component.graphicsConfiguration), height, bgColor, colors)
    var icon = cache.get(id)
    if (icon == null) {
      icon = LabelIcon(component, height, bgColor, colors)
      cache.put(id, icon)
    }
    return icon
  }

  private data class LabelIconId(val scale: Float, val height: Int, val bgColor: Color, val colors: List<Color>)
}