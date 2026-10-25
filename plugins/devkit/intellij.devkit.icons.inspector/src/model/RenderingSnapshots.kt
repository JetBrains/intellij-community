// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.model.InspectorFormatting.className
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.number
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.safeToString
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.scale.IconScale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.swing.createSwingIcon
import java.awt.image.BufferedImage
import java.text.SimpleDateFormat
import java.util.Date
import javax.swing.Icon

private val LOG = logger<RenderingSnapshots>()

/** Rendering snapshot rows and previews. All functions are total: failures become rows, never exceptions. */
internal object RenderingSnapshots {
  private const val GROUP_SWING = "Swing icon"
  private const val GROUP_RESOLVED = "Resolved (fresh renderer)"
  private const val GROUP_CAPTURED = "Captured paint"
  private const val GROUP_THEME = "Theme"
  private const val GROUP_PAINT = "Last paint"

  const val MAX_PREVIEW_SIZE: Int = 256

  fun snapshot(icon: Icon?, descriptor: IconDescriptor?, capture: RenderCapture?): RenderingSnapshot =
    RenderingSnapshot(properties(icon, descriptor, capture), lastPaint(capture))

  fun properties(icon: Icon?, descriptor: IconDescriptor?, capture: RenderCapture?): List<InspectorProperty> {
    val rows = ArrayList<InspectorProperty>()
    if (icon != null) {
      rows.add(InspectorProperty("class", className(icon), GROUP_SWING))
      rows.add(InspectorProperty("size (logical)", InspectorFormatting.iconSize(icon), GROUP_SWING))
      if (icon is SwingIcon) rows.add(InspectorProperty("icon scale", InspectorFormatting.scale(icon.scale), GROUP_SWING))
    }

    if (capture != null) {
      val root = capture.root
      if (root != null) {
        rows.add(InspectorProperty("display density", number(root.scaling.displayDensity), GROUP_CAPTURED))
        rows.add(InspectorProperty("context scale", number(root.scaling.contextScale), GROUP_CAPTURED))
        rows.add(InspectorProperty("used dimensions (device px)", InspectorFormatting.dimensions(root.dimensions), GROUP_CAPTURED))
      }
      val info = capture.paintInfo
      rows.add(InspectorProperty("final size (device px)", InspectorFormatting.size(info.finalWidth, info.finalHeight), GROUP_CAPTURED))
      rows.add(InspectorProperty("paint scale", info.scale ?: "none", GROUP_CAPTURED))
      rows.add(InspectorProperty("top-level renderer", InspectorFormatting.identity(capture.iconRenderer), GROUP_CAPTURED))
    }

    if (descriptor != null) {
      try {
        val context = IconRendererManager.createRenderingContext()
        val renderer = IconRendererManager.getInstance().createRenderer(descriptor, context)
        val scale: IconScale? = (icon as? SwingIcon)?.scale
        val densities = linkedSetOf(1f, 2f)
        capture?.paintInfo?.density?.let { densities.add(it) }
        for (density in densities) {
          val resolved = renderer.resolve(density, scale)
          rows.add(InspectorProperty("final dimensions @${number(density)}", InspectorFormatting.dimensions(resolved.finalDimensions),
                                     GROUP_RESOLVED))
          rows.add(InspectorProperty("scale factor @${number(density)}", number(resolved.context.contextScale), GROUP_RESOLVED))
        }
        rows.add(InspectorProperty("renderer", InspectorFormatting.identity(renderer), GROUP_RESOLVED))
        val theme = context.theme
        rows.add(InspectorProperty("theme context", className(theme), GROUP_THEME))
        rows.add(InspectorProperty("theme digest", runCatching { theme.digest() }.getOrElse { "failed: $it" }, GROUP_THEME))
        rows.add(InspectorProperty("theme image modifiers", runCatching { safeToString(theme.imageModifiers()) }.getOrElse { "failed: $it" },
                                   GROUP_THEME))
        rows.add(InspectorProperty("default image modifiers", safeToString(context.defaultImageModifiers), GROUP_THEME))
      }
      catch (t: Throwable) {
        LOG.debug(t)
        rows.add(InspectorProperty("error", t.toString(), GROUP_RESOLVED))
      }
    }
    return rows
  }

  fun lastPaint(capture: RenderCapture?): List<InspectorProperty> {
    if (capture == null) return emptyList()
    val info = capture.paintInfo
    return listOf(
      InspectorProperty("component", info.componentClass, GROUP_PAINT),
      InspectorProperty("x, y", "${info.x}, ${info.y}", GROUP_PAINT),
      InspectorProperty("density", number(info.density), GROUP_PAINT),
      InspectorProperty("scale", info.scale ?: "none", GROUP_PAINT),
      InspectorProperty("final size", InspectorFormatting.size(info.finalWidth, info.finalHeight), GROUP_PAINT),
      InspectorProperty("thread", capture.threadName, GROUP_PAINT),
      InspectorProperty("time", SimpleDateFormat("HH:mm:ss.SSS").format(Date()), GROUP_PAINT),
    )
  }

  /**
   * Renders [descriptor] at 1×, 1.25× and 2× via fresh Swing icons (fresh renderer instances, painted without a component, so
   * they are never captured and never hit flags). Call on EDT: the icons API loads images synchronously.
   */
  fun previews(descriptor: IconDescriptor?): List<PreviewImage> {
    if (descriptor == null) return emptyList()
    val base = try {
      descriptor.createSwingIcon()
    }
    catch (t: Throwable) {
      LOG.debug(t)
      return emptyList()
    }
    val result = ArrayList<PreviewImage>()
    for ((label, factor) in listOf("1×" to 1f, "1.25×" to 1.25f, "2×" to 2f)) {
      try {
        val icon: Icon = if (factor == 1f) base else base.scaled(factor(factor))
        render(label, icon)?.let(result::add)
      }
      catch (t: Throwable) {
        LOG.debug("Cannot render preview $label", t)
      }
    }
    return result
  }

  private fun render(label: String, icon: Icon): PreviewImage? {
    val width = icon.iconWidth
    val height = icon.iconHeight
    if (width <= 0 || height <= 0 || width > MAX_PREVIEW_SIZE || height > MAX_PREVIEW_SIZE) return null
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      // A legacy pick previews through its bridged descriptor, which paints nested SwingIcons with their real (possibly
      // flagged) renderers; keep the inspector's own painting from counting as hits or stopping the debugger.
      IconRenderDebugger.untraced { icon.paintIcon(null, graphics, 0, 0) }
    }
    finally {
      graphics.dispose()
    }
    val pixels = image.getRGB(0, 0, width, height, null, 0, width)
    return PreviewImage(label, width, height, pixels)
  }
}
