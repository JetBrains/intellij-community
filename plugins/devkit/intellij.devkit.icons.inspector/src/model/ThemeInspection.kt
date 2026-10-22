// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.model.InspectorFormatting.className
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.safeToString
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.IntelliJThemeImageFilter
import com.intellij.platform.icons.impl.intellij.rendering.IntelliJThemeSvgPatcher
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.intellij.rendering.SwingLayerPaintingContext
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.findModifier
import com.intellij.platform.icons.impl.modifiers.DarkModifier
import com.intellij.platform.icons.impl.rendering.DefaultIconRenderer
import com.intellij.platform.icons.impl.rendering.DefaultImageModifiers
import com.intellij.platform.icons.impl.rendering.DefaultRenderingContext
import com.intellij.platform.icons.impl.rendering.darkState
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.impl.rendering.layers.generateImageModifiers
import com.intellij.platform.icons.impl.rendering.resolve
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.rendering.IconRendererManager
import com.intellij.platform.icons.rendering.ThemeContext
import com.intellij.platform.icons.rendering.ThemeImageFilter
import com.intellij.platform.icons.rendering.ThemeSvgPatcher
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.pathTransform
import com.intellij.util.SVGLoader
import java.awt.image.BufferedImage
import javax.swing.Icon

private val LOG = logger<ThemeInspection>()

/**
 * The theme state of an inspected icon, as rows of the group [GROUP_THEME]: the effective dark state and its source, the
 * color palette and whether it recolors the image, the filters and the theme digest. All functions are total: failures
 * become rows, never exceptions.
 */
internal object ThemeInspection {
  const val GROUP_THEME: String = "Theme"

  /** The rendering context of a position in the descriptor tree, and where its explicit dark state comes from. */
  class ThemeScope(val context: DefaultRenderingContext, val darkSource: String?) {
    /** The scope of the children of [layer], as the layer renderers adjust their rendering context. */
    fun adjustTo(layer: IconLayer, layerLabel: String): ThemeScope {
      val own = layer.findModifier<DarkModifier>()
      val source = if (own != null) "dark() modifier on $layerLabel (dark=${own.dark})" else darkSource
      return ThemeScope(context.adjustTo(layer), source)
    }
  }

  /**
   * The scope of the descriptor of [icon]: the rendering context of [renderer] if it is a captured icon renderer, else the
   * context that [icon] creates. A [SwingIcon] with a dark state (`getDarkIcon`) gives that dark state to its context.
   */
  fun rootScope(icon: Icon?, renderer: Any?): ThemeScope? = try {
    val swingDark = (icon as? SwingIcon)?.dark
    val captured = (renderer as? DefaultIconRenderer)?.renderingContext as? DefaultRenderingContext
    val context = captured
                  ?: IconRendererManager.createRenderingContext(IconRendererManager.createUpdateFlow(null),
                                                                swingDark?.let { DefaultImageModifiers(darkOverride = it) })
                    as? DefaultRenderingContext
    if (context == null) null
    else {
      val override = context.defaultImageModifiers?.darkOverride
      val source = when {
        override == null -> null
        swingDark == override -> "SwingIcon getDarkIcon override (dark=$override)"
        else -> "caller rendering context (dark=$override)"
      }
      ThemeScope(context, source)
    }
  }
  catch (t: Throwable) {
    LOG.debug(t)
    null
  }

  /** The theme inputs of [theme] and the dark override of the caller [context], for the rendering snapshot. */
  fun themeRows(theme: ThemeContext, context: DefaultRenderingContext?): List<InspectorProperty> {
    val rows = ArrayList<InspectorProperty>()
    rows.add(row("theme context", className(theme)))
    rows.add(row("theme digest", attempt { theme.digest() }))
    rows.add(row("theme dark", attempt { theme.darkState()?.toString() ?: "not supplied" }))
    rows.add(row("caller dark override", context?.defaultImageModifiers?.darkOverride?.toString() ?: "none (follows the theme)"))
    rows.add(row("color palette", attempt { patcher(theme.colorPalette()) }))
    rows.add(row("global filter", attempt { filter(theme.globalFilter()) }))
    rows.add(row("selection patcher", attempt { patcher(theme.selectionPatcher()) + " (not applied by the renderers)" }))
    rows.add(row("gray filter", attempt { filter(theme.grayFilter()) + " (not applied by the renderers)" }))
    return rows
  }

  /** The theme rows of a new-API image [layer] in [scope]. [paletteCheck] renders it with and without the palette. */
  fun imageLayerRows(layer: ImageIconLayer, scope: ThemeScope, paletteCheck: Boolean = true): List<InspectorProperty> = try {
    val rows = darkRows(layer, scope)
    val modifiers = layer.generateImageModifiers(scope.context)
    val theme = scope.context.theme
    val palette = theme.colorPalette()
    rows.add(row("color palette", patcher(palette)))
    rows.add(row("palette recolors this image", when {
      palette == null -> "no: the theme has no palette"
      modifiers.stroke != null -> "no: a stroked image does not take the palette"
      palette !is IntelliJThemeSvgPatcher -> "no: the IntelliJ image loaders do not apply ${className(palette)}"
      !paletteCheck -> "not checked"
      else -> paletteEffect(layer, scope)
    }))
    rows.add(row("global filter", filter(theme.globalFilter())))
    rows.add(row("color filter modifier", modifiers.colorFilter?.let(::safeToString) ?: "none"))
    rows.add(row("theme digest", attempt { theme.digest() }))
    rows
  }
  catch (t: Throwable) {
    LOG.debug(t)
    listOf(row("error", t.toString()))
  }

  /** The theme rows of a swing layer: its dark state. The legacy icon applies its own palette and global filter. */
  fun swingLayerRows(layer: IconLayer, scope: ThemeScope): List<InspectorProperty> = try {
    val rows = darkRows(layer, scope)
    rows.add(row("palette and global filter", "applied by the legacy icon itself, from the global theme state"))
    rows
  }
  catch (t: Throwable) {
    LOG.debug(t)
    listOf(row("error", t.toString()))
  }

  /**
   * The theme state of a legacy [icon], read from the icon: its dark state (an explicit one from `getDarkIcon`, else
   * the global one), its stroke state, its color patcher and the global filter.
   */
  fun legacyRows(icon: CachedImageIcon): List<InspectorProperty> {
    val rows = ArrayList<InspectorProperty>()
    val flags = LegacyIcons.Reflect.get<Int>(icon, "attributes")
    val globalDark = pathTransform.get().isDark
    if (flags == null) {
      rows.add(row("effective dark", "$globalDark (the icon attributes are not readable)"))
    }
    else {
      val isDark = flags and 0b01 != 0
      val isDarkSet = flags and 0b10 != 0
      val stroke = flags and 0b100 != 0
      rows.add(row("effective dark", (if (isDarkSet) isDark else globalDark).toString()))
      rows.add(row("dark source", if (isDarkSet) "getDarkIcon override (dark=$isDark)" else "theme (IconLoader.setUseDarkIcons)"))
      rows.add(row("stroke", stroke.toString()))
    }
    val patcher = icon.svgColorPatcher
    rows.add(row("color patcher", when {
      patcher == null -> "none"
      patcher === SVGLoader.colorPatcherProvider -> "theme palette: ${legacyPatcher(patcher)}"
      else -> "own patcher (createWithPatcher): ${legacyPatcher(patcher)}"
    }))
    rows.add(row("global filter", pathTransform.get().filter?.let { filter(IntelliJThemeImageFilter(it)) } ?: "none"))
    return rows
  }

  private fun darkRows(layer: IconLayer, scope: ThemeScope): ArrayList<InspectorProperty> {
    val modifiers = layer.generateImageModifiers(scope.context)
    val rows = ArrayList<InspectorProperty>()
    val stroke = modifiers.stroke != null
    rows.add(row("effective dark", if (stroke) "${modifiers.isDark} (a stroked image loads the light artwork)" else modifiers.isDark.toString()))
    val own = layer.findModifier<DarkModifier>()
    val themeDark = scope.context.theme.darkState()
    rows.add(row("dark source", when {
      own != null -> "dark() modifier on this layer (dark=${own.dark})"
      modifiers.darkOverride != null -> scope.darkSource ?: "inherited dark override (dark=${modifiers.darkOverride})"
      themeDark != null -> "theme (dark=$themeDark)"
      else -> "rendering context default (the theme supplies no dark state)"
    }))
    return rows
  }

  /** Renders [layer] in [scope] with and without the theme palette and compares the pixels. */
  private fun paletteEffect(layer: ImageIconLayer, scope: ThemeScope): String {
    val withPalette = render(layer, scope.context)
    val withoutPalette = render(layer, scope.context.withTheme(WithoutPalette(scope.context.theme)))
    if (withPalette == null || withoutPalette == null) return "unknown: the image does not render"
    if (withPalette.size != withoutPalette.size) return "yes: the size changes"
    val changed = withPalette.indices.count { withPalette[it] != withoutPalette[it] }
    return if (changed == 0) "no: the image has no palette colors" else "yes: $changed of ${withPalette.size} pixels at 2×"
  }

  private fun render(layer: ImageIconLayer, context: DefaultRenderingContext): IntArray? {
    val renderer = IconRendererManager.getInstance().createRenderer(DefaultLayeredIconDescriptor(listOf(layer)), context)
    val scaling = renderer.resolve(2f, null)
    val width = scaling.finalDimensions.width
    val height = scaling.finalDimensions.height
    if (width <= 0 || height <= 0 || width > RenderingSnapshots.MAX_PREVIEW_SIZE || height > RenderingSnapshots.MAX_PREVIEW_SIZE) return null
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val graphics = image.createGraphics()
    try {
      IconRenderDebugger.untraced { renderer.render(SwingLayerPaintingContext(null, graphics, 0, 0, scaling = scaling.context)) }
    }
    finally {
      graphics.dispose()
    }
    return image.getRGB(0, 0, width, height, null, 0, width)
  }

  private fun DefaultRenderingContext.withTheme(theme: ThemeContext): DefaultRenderingContext =
    DefaultRenderingContext(updateFlow, defaultImageModifiers, theme, imageResourceProvider)

  private fun patcher(patcher: ThemeSvgPatcher?): String = when (patcher) {
    null -> "none"
    is IntelliJThemeSvgPatcher -> legacyPatcher(patcher.provider)
    else -> "${className(patcher)} (digest ${attempt { patcher.digest() }})"
  }

  private fun legacyPatcher(provider: SVGLoader.SvgElementColorPatcherProvider): String =
    "${className(provider)} (digest ${attempt { provider.digest().joinToString(",") }})"

  private fun filter(filter: ThemeImageFilter?): String = when (filter) {
    null -> "none"
    is IntelliJThemeImageFilter -> "${className(filter.filter)} (${attempt { filter.digest() }})"
    else -> "${className(filter)} (digest ${attempt { filter.digest() }})"
  }

  private fun row(name: String, value: String?): InspectorProperty = InspectorProperty(name, value, GROUP_THEME)

  private inline fun attempt(block: () -> String): String = runCatching(block).getOrElse { "failed: $it" }

  /** [theme] without its color palette, to see what the palette changes. */
  private class WithoutPalette(private val theme: ThemeContext) : ThemeContext {
    override fun digest(): String = theme.digest() + ";no-palette"

    override fun isDark(): Boolean? = theme.darkState()

    override fun globalFilter(): ThemeImageFilter? = theme.globalFilter()

    override fun selectionPatcher(): ThemeSvgPatcher? = theme.selectionPatcher()

    override fun grayFilter(): ThemeImageFilter? = theme.grayFilter()
  }
}
