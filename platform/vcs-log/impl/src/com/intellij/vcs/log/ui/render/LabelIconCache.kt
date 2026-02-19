// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.render

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.ui.scale.JBUIScale
import org.jetbrains.annotations.ApiStatus
import java.awt.Color
import javax.swing.JComponent

@ApiStatus.Internal
class LabelIconCache {
  private val cache = Caffeine.newBuilder().maximumSize(40).build<LabelIconId, LabelIcon>()

  fun getIcon(component: JComponent, height: Int, bgColor: Color, colors: List<Color>): LabelIcon {
    val id = LabelIconId(scale = JBUIScale.sysScale(component.graphicsConfiguration),
                         height = height,
                         bgColor = bgColor.rgb,
                         colors = colors.map { it.rgb })
    return cache.get(id) { LabelIcon(component, it.height, bgColor, colors) }
  }
}

private data class LabelIconId(@JvmField val scale: Float,
                               @JvmField val height: Int,
                               @JvmField val bgColor: Int,
                               @JvmField val colors: List<Int>)
