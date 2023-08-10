// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.util.IconLoader.getScaleToRenderIcon
import com.intellij.openapi.util.IconLoader.renderFilteredIcon
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.fakeComponent
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Graphics
import java.awt.image.RGBImageFilter
import java.util.function.Supplier
import javax.swing.Icon

@Internal
class FilteredIcon(private val baseIcon: Icon, private val filterSupplier: Supplier<RGBImageFilter?>) : ReplaceableIcon {
  private var modificationCount: Long = -1

  // IconLoader.CachedImageIcon uses ScaledIconCache to support several scales simultaneously. Not sure, it is needed here.
  private var iconToPaint: Icon? = null
  private var currentScale = 1.0

  override fun paintIcon(c: Component?, g: Graphics, x: Int, y: Int) {
    val scale = getScaleToRenderIcon(icon = baseIcon, ancestor = c).toDouble()
    var toPaint = iconToPaint
    if (toPaint == null || modificationCount != -1L) {
      val currentModificationCount = calculateModificationCount()
      if (currentModificationCount != -1L && currentModificationCount != modificationCount) {
        modificationCount = currentModificationCount
        toPaint = null
      }
    }
    if (scale != currentScale) {
      toPaint = null
    }

    // try to postpone rendering until it is really needed
    if (toPaint == null) {
      toPaint = renderFilteredIcon(icon = baseIcon, scale = scale, filterSupplier = filterSupplier, ancestor = c)
      currentScale = scale
      iconToPaint = toPaint
    }
    if (c != null) {
      PaintNotifier(c = c, x = x, y = y).replaceIcon(baseIcon)
    }
    toPaint.paintIcon(c ?: fakeComponent, g, x, y)
  }

  override fun replaceBy(replacer: IconReplacer): Icon {
    return FilteredIcon(replacer.replaceIcon(baseIcon), filterSupplier)
  }

  private fun calculateModificationCount(): Long {
    val searcher = TimestampSearcher()
    searcher.replaceIcon(baseIcon)
    return searcher.modificationCount
  }

  override fun getIconWidth(): Int = baseIcon.iconWidth

  override fun getIconHeight(): Int = baseIcon.iconHeight

  // this replacer plays a visitor role
  private class TimestampSearcher : IconReplacer {
    var modificationCount: Long = 0
    override fun replaceIcon(icon: Icon): Icon {
      if (icon is ModificationTracker) {
        if (modificationCount == -1L) {
          modificationCount = icon.modificationCount
        }
        else {
          modificationCount += icon.modificationCount
        }
      }
      (icon as? ReplaceableIcon)?.replaceBy(this)
      return super.replaceIcon(icon)
    }
  }
}

// this replacer plays a visitor role
private class PaintNotifier(val c: Component, val x: Int, val y: Int) : IconReplacer {
  override fun replaceIcon(icon: Icon): Icon {
    (icon as? UpdatableIcon)?.notifyPaint(c, x, y)
    (icon as? ReplaceableIcon)?.replaceBy(this)
    return super.replaceIcon(icon)
  }
}
