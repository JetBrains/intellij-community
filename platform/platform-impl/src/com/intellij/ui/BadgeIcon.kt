// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui

import org.jetbrains.annotations.ApiStatus
import java.awt.Graphics2D
import java.awt.Paint
import java.awt.Shape
import javax.swing.Icon
import javax.swing.UIManager

@ApiStatus.Internal
@ApiStatus.Experimental
abstract class BadgeIcon(icon: Icon, private val paint: Paint) : HoledIcon(icon) {

  protected abstract fun createShape(width: Int, height: Int, hole: Boolean): Shape?

  protected open fun getBorder() = getDouble("IconBadge.borderWidth", 1.0) / 20

  protected fun getDouble(key: String, default: Double) = when (val value = UIManager.get(key)) {
    is String -> value.toDoubleOrNull() ?: default
    is Number -> value.toDouble()
    else -> default
  }

  override fun createHole(width: Int, height: Int) = createShape(width, height, true)
  override fun paintHole(g: Graphics2D, width: Int, height: Int) {
    val shape = createShape(width, height, false) ?: return
    g.paint = paint
    g.fill(shape)
  }
}
