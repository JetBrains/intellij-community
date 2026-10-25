// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.icons.AllIcons
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.scale.JBUIScale
import javax.swing.Icon
import kotlin.math.roundToInt

/**
 * Dedicated size variants of SVG icons (`icon@20x20.svg` next to `icon.svg`). Legacy looks that enlarge icons (e.g. the main
 * toolbar's `HeaderToolbarButtonLook` → `loadIconCustomVersionOrScale`) load such a variant for a `CachedImageIcon`; an icons-API
 * `SwingIcon` is only scaled, so the same frame size can show different artwork. This row makes that visible.
 */
internal object SizeVariants {
  const val PROPERTY: String = "Size variant"

  /** `path@WxH.svg` for an SVG [path], else `null`. */
  fun variantPath(path: String, width: Int, height: Int): String? =
    if (path.endsWith(".svg")) "${path.removeSuffix(".svg")}@${width}x$height.svg" else null

  fun exists(path: String, classLoader: ClassLoader?): Boolean =
    classLoader != null && runCatching { classLoader.getResource(path.removePrefix("/")) != null }.getOrDefault(false)

  /** Unscaled (no user scale) logical size of a device-pixel frame, as used for variant file names. */
  fun variantSize(frameDevicePx: Int, screenScale: Float): Int = (frameDevicePx / screenScale / JBUIScale.scale(1f)).roundToInt()

  /** Row for a picked legacy icon painted with a frame of [paintedSize]×[paintedSize] (variant units), or `null`. */
  fun forLegacy(icon: Icon, paintedSize: Int?): InspectorProperty? {
    val cached = icon as? CachedImageIcon ?: return null
    val (path, classLoader) = runCatching { cached.getCoords() }.getOrNull() ?: return null
    if (paintedSize == null) return InspectorProperty(PROPERTY, "painted size unknown ($path)")
    val ownSize = (cached.iconWidth / JBUIScale.scale(1f)).roundToInt()
    if (paintedSize == ownSize) return InspectorProperty(PROPERTY, "not resized at paint time ($path)")
    val variant = variantPath(path, paintedSize, paintedSize)
    val value = if (variant != null && exists(variant, classLoader)) {
      "$variant (@${paintedSize}x$paintedSize variant of $path; loaded by looks that resize CachedImageIcons, e.g. " +
      "loadIconCustomVersionOrScale) — computed, the variant file exists"
    }
    else "none: $path scaled to ${paintedSize}×$paintedSize (no @${paintedSize}x$paintedSize variant)"
    return InspectorProperty(PROPERTY, value)
  }

  /** Row for an icons-API descriptor painted at [paintedSize] (variant units), or `null` if it has no image layers. */
  fun forDescriptor(descriptor: IconDescriptor, paintedSize: Int?): InspectorProperty? {
    val locations = imageLocations(descriptor)
    if (locations.isEmpty()) return null
    if (paintedSize == null) return InspectorProperty(PROPERTY, "painted size unknown (${locations.joinToString { it.path }})")
    val parts = locations.map { location ->
      val variant = variantPath(location.path, paintedSize, paintedSize)
      val classLoader = classLoaderOf(location)
      if (variant != null && classLoader == null) {
        "scaled from ${location.path} (variant lookup not checked for plugin ${location.pluginId})"
      }
      else if (variant != null && exists(variant, classLoader)) {
        "$variant (@${paintedSize}x$paintedSize variant of ${location.path}; the icons API image loader uses it when the " +
        "image is painted at exactly this design size, like legacy loadIconCustomVersion) — computed, the variant file exists"
      }
      else "scaled from ${location.path} (no @${paintedSize}x$paintedSize variant)"
    }
    return InspectorProperty(PROPERTY, parts.distinct().joinToString("; "))
  }

  fun imageLocations(descriptor: IconDescriptor): List<ModuleImageResourceLocation> {
    val result = ArrayList<ModuleImageResourceLocation>()
    fun visitLayer(layer: IconLayer, depth: Int) {
      if (depth > InspectorTreeBuilder.MAX_DEPTH) return
      when (layer) {
        is ImageIconLayer -> (layer.loader as? ModuleImageResourceLocation)?.let(result::add)
        is NestedIconLayer -> (layer.iconDescriptor as? DefaultLayeredIconDescriptor)?.layers?.forEach { visitLayer(it, depth + 1) }
        is LayoutIconLayer -> layer.nestedLayers.forEach { visitLayer(it, depth + 1) }
        else -> Unit
      }
    }
    (descriptor as? DefaultLayeredIconDescriptor)?.layers?.forEach { visitLayer(it, 0) }
    return result
  }

  /**
   * The class loader of a platform location (plugin `com.intellij`, no module), else `null`: resolving plugin class loaders
   * needs `PluginManagerCore` (core.impl), which this module does not depend on.
   */
  private fun classLoaderOf(location: ModuleImageResourceLocation): ClassLoader? =
    if (location.pluginId == PLATFORM_PLUGIN_ID && location.moduleId == null) AllIcons::class.java.classLoader else null

  private const val PLATFORM_PLUGIN_ID = "com.intellij"
}
