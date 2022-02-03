// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Paint
import javax.swing.Icon

@ApiStatus.Internal
@ApiStatus.Experimental
class BadgeIcon(icon: Icon, private val paint: Paint, private val provider: BadgeShapeProvider) : HoledIcon(icon) {
  constructor(icon: Icon, paint: Paint) : this(icon, paint, BadgeDotProvider())

  override fun copyWith(icon: Icon) = BadgeIcon(icon, paint, provider)

  override fun createHole(width: Int, height: Int) = provider.createShape(width, height, true)
  override fun paintHole(g: Graphics2D, width: Int, height: Int) {
    val shape = provider.createShape(width, height, false) ?: return
    g.paint = paint
    g.fill(shape)
  }
}
