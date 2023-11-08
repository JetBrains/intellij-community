// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.icons

import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.util.ModificationTracker
import com.intellij.openapi.util.fakeComponent
import com.intellij.ui.Gray
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.scale.JBUIScale
import com.intellij.ui.scale.ScaleContextSupport
import com.intellij.ui.scale.ScaleType
import com.intellij.util.RetinaImage
import com.intellij.util.ui.EmptyIcon
import com.intellij.util.ui.JBImageIcon
import com.intellij.util.ui.StartupUiUtil
import org.jetbrains.annotations.ApiStatus.Internal
import java.awt.Component
import java.awt.Graphics
import java.awt.GraphicsConfiguration
import java.awt.Toolkit
import java.awt.image.BufferedImage
import java.awt.image.FilteredImageSource
import javax.swing.Icon

@Internal
class FilteredIcon(private val baseIcon: Icon, private val filterSupplier: RgbImageFilterSupplier) : ReplaceableIcon {
  private var modificationCount: Long = -1

  // IconLoader.CachedImageIcon uses ScaledIconCache to support several scales simultaneously. Not sure, it is needed here.
  private var iconToPaint: Icon? = null
  private var currentScale = 1.0

  init {
    if (baseIcon is EmptyIcon) {
      thisLogger().warn("Do not create FilteredIcon for EmptyIcon")
    }
  }

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

/**
 * Returns [ScaleContextSupport] which best represents this icon taking into account its compound structure, or null when not applicable.
 */
private fun getScaleContextSupport(icon: Icon): ScaleContextSupport? {
  return when (icon) {
    is ScaleContextSupport -> icon
    is RetrievableIcon -> getScaleContextSupport(icon.retrieveIcon())
    is CompositeIcon -> {
      if (icon.iconCount == 0) {
        return null
      }
      getScaleContextSupport(icon.getIcon(0) ?: return null)
    }
    else -> null
  }
}

private fun getScaleToRenderIcon(icon: Icon, ancestor: Component?): Float {
  val ctxSupport = getScaleContextSupport(icon)
  val scale = if (ctxSupport == null) {
    (if (JreHiDpiUtil.isJreHiDPI(null as GraphicsConfiguration?)) JBUIScale.sysScale(ancestor) else 1.0f)
  }
  else {
    if (JreHiDpiUtil.isJreHiDPI(null as GraphicsConfiguration?)) ctxSupport.getScale(ScaleType.SYS_SCALE).toFloat() else 1.0f
  }
  return scale
}

private fun renderFilteredIcon(icon: Icon,
                               scale: Double,
                               filterSupplier: RgbImageFilterSupplier,
                               ancestor: Component?): JBImageIcon {
  @Suppress("UndesirableClassUsage")
  val image = BufferedImage((scale * icon.iconWidth).toInt(), (scale * icon.iconHeight).toInt(), BufferedImage.TYPE_INT_ARGB)
  val graphics = image.createGraphics()
  graphics.color = Gray.TRANSPARENT
  graphics.fillRect(0, 0, icon.iconWidth, icon.iconHeight)
  graphics.scale(scale, scale)
  // We want to paint here on the fake component:
  // painting on the real component will have other coordinates at least.
  // Also, it may be significant if the icon contains updatable icon (e.g., DeferredIcon), and it will schedule incorrect repaint
  icon.paintIcon(fakeComponent, graphics, 0, 0)
  graphics.dispose()

  var img = Toolkit.getDefaultToolkit().createImage(FilteredImageSource(image.source, filterSupplier.getFilter()))
  if (StartupUiUtil.isJreHiDPI(ancestor)) {
    img = RetinaImage.createFrom(img!!, scale, null)
  }
  return JBImageIcon(img!!)
}