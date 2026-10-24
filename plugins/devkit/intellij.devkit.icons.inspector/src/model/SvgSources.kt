// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.model.InspectorFormatting.number
import com.intellij.icons.AllIcons
import com.intellij.openapi.extensions.PluginDescriptor
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.actionSystem.impl.ActionButton
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.IconLoader
import com.intellij.openapi.util.ScalableIcon
import com.intellij.openapi.wm.impl.customFrameDecorations.header.toolbar.HeaderToolbarButtonLook
import com.intellij.openapi.wm.impl.headertoolbar.isDarkHeader
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.layers.findModifier
import com.intellij.platform.icons.impl.modifiers.SizeVariantModifier
import com.intellij.platform.icons.impl.modifiers.StrokeModifier
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.modifiers.sizeVariants
import com.intellij.platform.icons.modifiers.then
import com.intellij.ui.icons.findIconLoaderByPath
import com.intellij.ui.icons.pathTransform
import kotlin.math.ceil
import kotlin.math.floor
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.ui.RetrievableIcon
import com.intellij.platform.icons.imageIconDescriptor
import com.intellij.platform.icons.impl.intellij.ModuleImageResourceLocation
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.rendering.debug.IconRenderDebugger
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.swing.createSwingIcon
import com.intellij.ui.JBColor
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.loadIconCustomVersionOrScale
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.JBUI
import java.awt.Component
import java.awt.Rectangle
import java.awt.image.BufferedImage
import javax.swing.Icon
import kotlin.math.abs
import kotlin.math.roundToInt

private val LOG = logger<SvgSources>()

/**
 * For the picked icon, the SVG file each pipeline really rasterizes at the painted size ([ResolvedSvgSource]), without
 * platform hooks:
 * - LEGACY: reproduces the resizing look's decision (`HeaderToolbarButtonLook.scaleAndAdjustIcon` →
 *   `loadIconCustomVersionOrScale`), then theme mapping / `@WxH` size variant / `_dark` resolution of the resulting icon.
 * - NEW_ICONS_API: renders the descriptor through the real new pipeline the way the look scales it and determines the file
 *   empirically, by pixel-comparing against renderings of every candidate file that exists.
 *
 * EDT only. Total: failures become entries with a null [ResolvedSvgSource.finalPath] and an explanation.
 */
internal object SvgSources {
  private const val MAX_RENDER = 256
  private const val MATCH_THRESHOLD = 99.0
  private const val CHANNEL_TOLERANCE = 8

  /** The decision context of the component the icon was picked from. */
  internal class LookContext(
    /** Unscaled logical size the look resizes icons to; `null` if no resizing look was detected. */
    val resizeTo: Int?,
    /** Dark flag the look passes (`isDarkHeader()` for the main toolbar); `null` = theme default. */
    val dark: Boolean?,
    val description: String,
  )

  fun compute(picked: PickedIcon, sizing: SizingContext): List<ResolvedSvgSource> {
    val density = sizing.screenScale ?: 1f
    val look = lookOf(picked.component)
    val origin = originOf(picked)
    val paintedSize = paintedSize(picked, sizing, look)
    return listOf(
      safely(IconPipeline.LEGACY) { legacy(origin, look, paintedSize, density) },
      safely(IconPipeline.NEW_ICONS_API) { newApi(picked, origin, look, paintedSize, density) },
    )
  }

  private fun safely(pipeline: IconPipeline, block: () -> ResolvedSvgSource): ResolvedSvgSource = try {
    IconRenderDebugger.untraced(block)
  }
  catch (t: Throwable) {
    LOG.debug(t)
    ResolvedSvgSource(pipeline, null, emptyList(), null, "failed: $t", null, rendering = null)
  }

  // ---- inputs ----

  /** How the new-API descriptor gets its image. */
  internal enum class DescriptorKind { NATIVE, BRIDGED, NONE }

  /**
   * Where the picked icon's artwork comes from.
   *
   * @property legacyIcon the legacy icon of the same artwork: the picked `CachedImageIcon` itself, the one wrapped by a bridged
   *   descriptor's `SwingIconLayer`, or (native descriptors) the `CachedImageIcon` IconLoader returns for the image path
   * @property path the effective (theme-mapped) resource path; [declaredPath] is the path before theme mapping, if different
   */
  internal class Origin(
    val legacyIcon: CachedImageIcon?,
    val legacyIconOrigin: String,
    val descriptor: IconDescriptor?,
    val descriptorKind: DescriptorKind,
    val path: String?,
    val declaredPath: String?,
    val classLoader: ClassLoader?,
    val note: String?,
  )

  /** Effective coordinates of a legacy icon after the current path transform (theme mapping) is applied. */
  fun effectiveCoords(icon: CachedImageIcon): Pair<String, ClassLoader>? {
    // the path transform (new UI mapping) is applied lazily, on the first size/paint request
    runCatching { icon.iconWidth }
    return icon.getCoords()
  }

  fun originOf(picked: PickedIcon): Origin {
    val icon = picked.icon
    if (icon is CachedImageIcon) {
      val coords = effectiveCoords(icon)
      return Origin(icon, "the picked CachedImageIcon (as held by the component)", null, DescriptorKind.NONE, coords?.first,
                    icon.originalPath?.takeIf { it != coords?.first }, coords?.second, null)
    }
    val descriptor = when {
      icon is SwingIcon -> icon.iconDescriptor
      picked.compose != null -> picked.compose.capture?.descriptor
      else -> null
    } ?: return Origin(null, "none", null, DescriptorKind.NONE, null, null, null, "no single image resource behind the picked icon")

    val location = SizeVariants.imageLocations(descriptor).firstOrNull()
    if (location != null) {
      val classLoader = classLoaderOf(location)
      val legacy = classLoader?.let { IconLoader.findIcon(location.path, it) as? CachedImageIcon }
      val coords = legacy?.let(::effectiveCoords)
      return Origin(legacy, "IconLoader.findIcon(${location.path}) — the legacy icon of the same file", descriptor, DescriptorKind.NATIVE,
                    coords?.first ?: location.path, null, classLoader,
                    if (classLoader == null) "class loader of plugin ${location.pluginId} not resolvable from the inspector" else null)
    }
    val bridged = swingLayerIcons(descriptor).firstNotNullOfOrNull(::asCachedImageIcon)
    if (bridged != null) {
      val coords = effectiveCoords(bridged)
      return Origin(bridged, "the CachedImageIcon in the bridged descriptor's SwingIconLayer", descriptor, DescriptorKind.BRIDGED,
                    coords?.first, bridged.originalPath?.takeIf { it != coords?.first }, coords?.second, null)
    }
    val kind = if (swingLayerIcons(descriptor).isNotEmpty()) DescriptorKind.BRIDGED else DescriptorKind.NONE
    return Origin(null, "none", descriptor, kind, null, null, null,
                  if (kind == DescriptorKind.BRIDGED) "bridged descriptor wraps a legacy icon that is not a CachedImageIcon"
                  else "no image layer in the descriptor")
  }

  private fun asCachedImageIcon(icon: Icon): CachedImageIcon? = when (icon) {
    is CachedImageIcon -> icon
    is RetrievableIcon -> runCatching { icon.retrieveIcon() }.getOrNull() as? CachedImageIcon
    else -> null
  }

  /** Legacy icons wrapped by `SwingIconLayer`s of [descriptor], in tree order. */
  fun swingLayerIcons(descriptor: IconDescriptor): List<Icon> {
    val result = ArrayList<Icon>()
    fun visit(layer: IconLayer, depth: Int) {
      if (depth > InspectorTreeBuilder.MAX_DEPTH) return
      when (layer) {
        is SwingIconLayer -> result.add(layer.legacyIcon)
        is NestedIconLayer -> (layer.iconDescriptor as? DefaultLayeredIconDescriptor)?.layers?.forEach { visit(it, depth + 1) }
        is LayoutIconLayer -> layer.nestedLayers.forEach { visit(it, depth + 1) }
        else -> Unit
      }
    }
    (descriptor as? DefaultLayeredIconDescriptor)?.layers?.forEach { visit(it, 0) }
    return result
  }

  /**
   * The class loader [location] is loaded from: the platform loader for `com.intellij`, else the plugin's loader via
   * `PluginManagerCore` (reflectively; this module does not depend on core.impl). Content modules use the plugin's main
   * loader here, which may not see module-private resources.
   */
  private fun classLoaderOf(location: ModuleImageResourceLocation): ClassLoader? {
    if (location.pluginId == "com.intellij" && location.moduleId == null) return AllIcons::class.java.classLoader
    return runCatching {
      val pluginId = PluginId.getId(location.pluginId)
      val manager = Class.forName("com.intellij.ide.plugins.PluginManagerCore")
      val instance = manager.getField("INSTANCE").get(null)
      val plugin = manager.getMethod("findPlugin", PluginId::class.java).invoke(instance, pluginId) as? PluginDescriptor
      plugin?.pluginClassLoader
    }.getOrNull()
  }

  fun lookOf(component: Component): LookContext {
    if (component is ActionButton) {
      val look = buttonLook(component)
      if (look is HeaderToolbarButtonLook) {
        return LookContext(JBUI.CurrentTheme.Toolbar.experimentalToolbarButtonIconSize(), isDarkHeader(),
                           "HeaderToolbarButtonLook (main toolbar)")
      }
      return LookContext(null, null, "no resizing look detected (${look?.javaClass?.simpleName ?: "unknown look"})")
    }
    return LookContext(null, null, "no resizing look detected")
  }

  private fun buttonLook(button: ActionButton): Any? {
    // ActionButton.getButtonLook() is protected
    var clazz: Class<*>? = button.javaClass
    while (clazz != null) {
      val value = runCatching { clazz.getDeclaredField("myLook").also { it.isAccessible = true }.get(button) }.getOrNull()
      if (value != null) return value
      clazz = clazz.superclass
    }
    return null
  }

  /** Painted logical size in unscaled units (as used by `@WxH` variant names). */
  private fun paintedSize(picked: PickedIcon, sizing: SizingContext, look: LookContext): Int? {
    val frame = sizing.rootPaintedFrame
    val scale = sizing.screenScale
    if (frame != null && scale != null) return SizeVariants.variantSize(frame.width, scale)
    picked.compose?.capture?.composePaintInfo?.let { return (it.finalWidth / it.density).roundToInt() }
    look.resizeTo?.let { return it }
    val icon = picked.icon ?: return null
    return (icon.iconWidth / JBUIScale.scale(1f)).roundToInt()
  }

  private fun themeMappingStep(origin: Origin): String? =
    origin.declaredPath?.let { "theme mapping: $it → ${origin.path}" }

  // ---- legacy ----

  private fun legacy(origin: Origin, look: LookContext, size: Int?, density: Float): ResolvedSvgSource {
    val icon = origin.legacyIcon
    val basePath = origin.path
    if (icon == null || basePath == null) {
      return ResolvedSvgSource(IconPipeline.LEGACY, basePath, emptyList(), null, origin.note ?: "no legacy CachedImageIcon for the icon",
                               null, rendering = null)
    }
    val classLoader = origin.classLoader
    val steps = ArrayList<String>()
    themeMappingStep(origin)?.let(steps::add)

    val final: Icon
    val how: String
    if (look.resizeTo != null) {
      final = loadIconCustomVersionOrScale(icon = icon, size = look.resizeTo, isDark = look.dark.takeIf { it == true },
                                           isDarkForScale = look.dark)
      how = "reproduced ${look.description} decision on ${origin.legacyIconOrigin}: loadIconCustomVersionOrScale(size = " +
            "${look.resizeTo}, isDark = ${look.dark})"
    }
    else {
      final = icon
      how = "${origin.legacyIconOrigin}, as is (${look.description})"
    }
    val sizedPath = (final as? CachedImageIcon)?.let(::effectiveCoords)?.first ?: basePath
    when {
      sizedPath != basePath -> steps.add("size variant @${look.resizeTo}x${look.resizeTo} → $sizedPath")
      look.resizeTo != null && final.iconWidth != icon.iconWidth ->
        steps.add("no size variant: $basePath scaled ${number(final.iconWidth.toFloat() / icon.iconWidth)}×")
      size != null && size != (icon.iconWidth / JBUIScale.scale(1f)).roundToInt() ->
        steps.add("painted at $size px without a look substitution")
    }
    val darkInEffect = look.dark ?: !JBColor.isBright()
    val darkPath = darkPath(sizedPath)
    var finalPath = sizedPath
    val patches = ArrayList<String>()
    if (darkInEffect) {
      if (darkPath != null && SizeVariants.exists(darkPath, classLoader)) {
        finalPath = darkPath
        steps.add("dark → $darkPath")
      }
      else steps.add("dark in effect, but no ${darkPath ?: "_dark"} variant: $sizedPath is read as is")
      if (look.dark == true) patches.add("dark header: light-on-dark variant requested by the look (isDark = true)")
    }
    val rendering = render("legacy", final, density)
    return ResolvedSvgSource(IconPipeline.LEGACY, basePath, steps, finalPath, how, svgText(finalPath, classLoader), patches, rendering,
                             rendering?.let(::contentBounds))
  }

  // ---- new icons API ----

  /** The layer the new pipeline rasterizes for the icon, with what applies to it on the way down. */
  private class ImageTarget(
    val layer: IconLayer,
    /** Classes from the root descriptor down to [layer], e.g. "DefaultLayeredIconDescriptor → NestedIconLayer → …". */
    val chain: List<String>,
    val policy: SizeVariantPolicy,
    /** Where [policy] comes from. */
    val policySource: String,
    val stroked: Boolean,
  )

  /** The first image or swing layer of [descriptor] (depth first), with the innermost size-variant policy / stroke on the path. */
  private fun findTarget(descriptor: IconDescriptor): ImageTarget? {
    fun visit(current: IconDescriptor, chain: List<String>, policy: Pair<SizeVariantPolicy, String>?, stroked: Boolean, depth: Int): ImageTarget? {
      if (depth > InspectorTreeBuilder.MAX_DEPTH) return null
      val layers = (current as? DefaultLayeredIconDescriptor)?.layers ?: return null
      val here = chain + InspectorFormatting.simpleClassName(current)
      fun visitLayer(layer: IconLayer, path: List<String>, inherited: Pair<SizeVariantPolicy, String>?, inheritedStroke: Boolean): ImageTarget? {
        val own = layer.findModifier<SizeVariantModifier>()?.let { it.policy to "${InspectorFormatting.simpleClassName(layer)} modifier" }
        val effective = own ?: inherited
        val stroke = inheritedStroke || layer.findModifier<StrokeModifier>() != null
        val layerPath = path + InspectorFormatting.simpleClassName(layer)
        return when (layer) {
          is ImageIconLayer, is SwingIconLayer ->
            ImageTarget(layer, layerPath, effective?.first ?: SizeVariantPolicy.Exact, effective?.second ?: "default (no sizeVariants modifier)", stroke)
          is NestedIconLayer -> visit(layer.iconDescriptor, layerPath, effective, stroke, depth + 1)
          is LayoutIconLayer -> layer.nestedLayers.firstNotNullOfOrNull { visitLayer(it, layerPath, effective, stroke) }
          else -> null
        }
      }
      return layers.firstNotNullOfOrNull { visitLayer(it, here, policy, stroked) }
    }
    return visit(descriptor, emptyList(), null, false, 0)
  }

  /** [descriptor] with its first image / swing layer replaced by [replacement] (structure and other modifiers kept). */
  private fun replaceTarget(descriptor: IconDescriptor, replacement: (IconLayer) -> IconLayer?): IconDescriptor? {
    var replaced = false
    fun replaceIn(current: IconDescriptor): IconDescriptor {
      val layered = current as? DefaultLayeredIconDescriptor ?: return current
      fun replaceLayer(layer: IconLayer): IconLayer? {
        if (replaced) return layer
        return when (layer) {
          is ImageIconLayer, is SwingIconLayer -> replacement(layer)?.also { replaced = true }
          is NestedIconLayer -> NestedIconLayer(replaceIn(layer.iconDescriptor), layer.modifier)
          is LayoutIconLayer -> LayoutIconLayer(layer.nestedLayers.map { replaceLayer(it) ?: return null }, layer.direction, layer.modifier)
          else -> layer
        }
      }
      return DefaultLayeredIconDescriptor(layered.layers.map { replaceLayer(it) ?: return current })
    }
    val result = replaceIn(descriptor)
    return if (replaced) result else null
  }

  private fun newApi(picked: PickedIcon, origin: Origin, look: LookContext, size: Int?, density: Float): ResolvedSvgSource {
    val pickedIcon = picked.icon
    val hypothetical = pickedIcon is CachedImageIcon
    // what the new pipeline really paints: the picked SwingIcon, a Compose descriptor, or (legacy pick) the same file as a descriptor
    val descriptor: IconDescriptor? = when {
      pickedIcon is SwingIcon -> pickedIcon.iconDescriptor
      origin.descriptor != null -> origin.descriptor
      origin.path != null && origin.classLoader != null -> imageIconDescriptor(origin.path, origin.classLoader)
      else -> null
    }
    val steps = ArrayList<String>()
    if (hypothetical) steps.add("hypothetical: the pick is legacy; shows the same file through the icons API")
    val target = descriptor?.let(::findTarget)
    if (descriptor == null || target == null) {
      return ResolvedSvgSource(IconPipeline.NEW_ICONS_API, origin.path, steps, null,
                               origin.note ?: "no icons-API descriptor with an image or swing layer", null, rendering = null)
    }
    val layer = target.layer
    val chain = (if (pickedIcon is SwingIcon) listOf("SwingIcon") else emptyList()) + target.chain
    val location = (layer as? ImageIconLayer)?.loader as? ModuleImageResourceLocation
    val held: CachedImageIcon? = (layer as? SwingIconLayer)?.legacyIcon?.let(::asCachedImageIcon)
    val bridged = layer is SwingIconLayer

    // paths
    val declaredPath: String?
    val classLoader: ClassLoader?
    val effectivePath: String?
    if (bridged) {
      steps.add("bridged: ${chain.joinToString(" → ")}(${(layer as SwingIconLayer).legacyIcon.javaClass.simpleName}) → " +
                "LegacyIconImageResourceHolder → IconLoader.toImage")
      val coords = held?.let(::effectiveCoords)
      declaredPath = held?.originalPath ?: coords?.first
      classLoader = coords?.second
      effectivePath = coords?.first
    }
    else {
      steps.add("native: ${chain.joinToString(" → ")}(${location?.path ?: (layer as ImageIconLayer).loader}) → DataLoaderImageResourceHolder")
      declaredPath = location?.path
      classLoader = location?.let(::classLoaderOf)
      effectivePath = if (location != null && classLoader != null) {
        runCatching { findIconLoaderByPath(location.path, classLoader).getCoords()?.first }.getOrNull() ?: location.path
      }
      else location?.path
    }
    steps.add("base path (as named): ${declaredPath ?: "unknown"}")
    steps.add(if (effectivePath != null && effectivePath != declaredPath) "theme mapping: $declaredPath → $effectivePath"
              else "effective path (loader coords): ${effectivePath ?: "unknown"}")
    steps.add("size-variant policy: ${target.policy} (${target.policySource})")

    val swing: Icon = pickedIcon as? SwingIcon ?: descriptor.createSwingIcon()
    val scaled = scaleLike(swing, look, size)
    val rendering = render("new icons API", scaled, density)
    if (effectivePath == null || classLoader == null || rendering == null) {
      val why = when {
        effectivePath == null -> "the image path is unknown"
        classLoader == null -> origin.note ?: "class loader of ${location?.pluginId ?: "the icon"} not resolvable from the inspector"
        else -> "nothing rendered"
      }
      return ResolvedSvgSource(IconPipeline.NEW_ICONS_API, effectivePath ?: declaredPath, steps, null, "undetermined: $why", null,
                               rendering = rendering, contentBounds = rendering?.let(::contentBounds))
    }

    // design size, as the image holders compute it (logical painted size L contains the IDE zoom)
    val zoom = JBUIScale.scale(1f)
    val logical = scaled.iconWidth
    val design = designFor(logical)
    steps.add("design size: device ${rendering.width} px ÷ density ${number(density)} = L $logical (zoom ${number(zoom)}); " +
              (if (design != null) "JBUIScale.scale($design) == $logical ✓ → design ${design}×${scaled.iconHeight.let(::designFor) ?: "?"}"
               else "no integer W with JBUIScale.scale(W) == $logical → no exact design size"))

    // variant decision per policy (prediction)
    val baseDesign = (swing.iconWidth / zoom).roundToInt()
    var predictedBase = effectivePath
    when {
      target.policy == SizeVariantPolicy.None -> steps.add("variant lookup: none (policy None)")
      logical == swing.iconWidth -> steps.add("variant lookup: none (painted at its own size)")
      target.policy == SizeVariantPolicy.Exact -> {
        val variant = design?.let { SizeVariants.variantPath(effectivePath, it, it) }
        if (variant == null) steps.add("variant lookup: none (no exact design size)")
        else {
          val exists = SizeVariants.exists(variant, classLoader)
          val accepted = exists && acceptedVariant(variant, classLoader, design)
          steps.add("variant probe $variant: exists=$exists, accepted=$accepted")
          if (accepted) predictedBase = variant
        }
      }
      else -> {
        // Fitting: largest existing variant between base + 1 and the design size
        val top = floor(logical / zoom.toDouble() + 0.01).toInt()
        val probed = ArrayList<String>()
        var found: String? = null
        var width = top
        while (width > baseDesign && probed.size < 16 && found == null) {
          val variant = SizeVariants.variantPath(effectivePath, width, width)
          if (variant != null) {
            val exists = SizeVariants.exists(variant, classLoader)
            probed.add("@${width}x$width=${if (exists) "exists" else "-"}")
            if (exists) found = variant
          }
          width--
        }
        steps.add("variant probes (Fitting, design $top down to ${baseDesign + 1}): ${probed.joinToString().ifEmpty { "none" }}")
        if (found != null) predictedBase = found
      }
    }
    val dark = isDarkInEffect(held, target)
    val darkCandidate = darkPath(predictedBase)
    val predicted = if (dark.first && darkCandidate != null && SizeVariants.exists(darkCandidate, classLoader)) darkCandidate else predictedBase
    steps.add("dark: ${dark.second}" + if (predicted != predictedBase) " → $predicted" else "")
    steps.add("predicted (step logic): $predicted")

    // candidates: every existing variant file, rendered through the same pipeline and modifiers, with size variants disabled,
    // so that each renders exactly its own file
    val variant = design?.let { SizeVariants.variantPath(effectivePath, it, it) }
    val candidates = listOfNotNull(effectivePath, darkPath(effectivePath), variant, variant?.let(::darkPath), predictedBase, predicted)
      .distinct()
      .filter { SizeVariants.exists(it, classLoader) }
    val scores = candidates.mapNotNull { candidate ->
      val candidateDescriptor = replaceTarget(descriptor) { original ->
        val noVariants = original.modifier then IconModifier.sizeVariants(SizeVariantPolicy.None)
        when (original) {
          is ImageIconLayer -> ImageIconLayer(ModuleImageResourceLocation(candidate, location?.pluginId ?: "com.intellij", location?.moduleId),
                                              noVariants)
          is SwingIconLayer -> {
            val candidateIcon = IconLoader.findIcon(candidate, classLoader) as? CachedImageIcon ?: return@replaceTarget null
            SwingIconLayer(if (held != null) candidateIcon.withAnotherIconModifications(held) else candidateIcon, noVariants)
          }
          else -> null
        }
      } ?: return@mapNotNull null
      val candidateSwing = SwingIcon(candidateDescriptor)
      val candidateScaled: Icon = if (candidateSwing.iconWidth == scaled.iconWidth || candidateSwing.iconWidth <= 0) candidateSwing
      else candidateSwing.scaled(factor(scaled.iconWidth.toFloat() / candidateSwing.iconWidth)) as Icon
      val image = render(candidate, candidateScaled, density) ?: return@mapNotNull null
      candidate to compare(rendering, image)
    }
    val ranking = scores.sortedByDescending { it.second }.joinToString { "${it.first}: ${number(it.second)}%" }
    val bestScore = scores.maxOfOrNull { it.second }
    if (bestScore == null || bestScore < MATCH_THRESHOLD) {
      val resolution = "undetermined: no candidate matched ≥ ${number(MATCH_THRESHOLD)}% " +
                       "(${ranking.ifEmpty { "no candidates rendered" }}); predicted: $predicted"
      return ResolvedSvgSource(IconPipeline.NEW_ICONS_API, effectivePath, steps, null, resolution, null,
                               rendering = rendering, contentBounds = contentBounds(rendering))
    }
    // pixel-identical candidates (e.g. a base and its _dark file under a dark theme) cannot be told apart by pixels
    val ties = scores.filter { bestScore - it.second < 0.01 }.map { it.first }
    val matched = if (predicted in ties) predicted else ties.first()
    val tieNote = if (ties.size > 1) "; pixel-identical: ${ties.joinToString()}, chosen by the step logic" else ""
    val agreement = if (matched == predicted) "matches the prediction" else "differs from the prediction $predicted"
    val matchedVariant = variant != null && matched.startsWith(variant.removeSuffix(".svg"))
    steps.add(if (matchedVariant) "size variant @${design}x$design → $variant (matched)"
              else "no size variant: rasterized base artwork " +
                   (if (scaled === swing) "unscaled" else "scaled ${number(scaled.iconWidth.toFloat() / swing.iconWidth)}×") + " (matched)")
    if (matched.endsWith("_dark.svg")) steps.add("dark → $matched (matched)")
    return ResolvedSvgSource(IconPipeline.NEW_ICONS_API, effectivePath, steps, matched,
                             "pixel match ${number(bestScore)}% vs $matched ($agreement$tieNote; $ranking)", svgText(matched, classLoader),
                             listOfNotNull(if (target.stroked) "stroke modifier (stroke variant / palette)" else null),
                             rendering, contentBounds(rendering))
  }

  /** How the look scales [icon] (main toolbar → `loadIconCustomVersionOrScale`), else to the painted [size]. */
  private fun scaleLike(icon: Icon, look: LookContext, size: Int?): Icon {
    val native = icon.iconWidth
    return when {
      look.resizeTo != null && icon is ScalableIcon -> loadIconCustomVersionOrScale(icon, look.resizeTo)
      size != null && native > 0 && icon is SwingIcon && JBUIScale.scale(size.toFloat()).roundToInt() != native ->
        icon.scaled(factor(JBUIScale.scale(size.toFloat()) / native)) as Icon
      else -> icon
    }
  }

  /** The integer design size W with `JBUIScale.scale(W) == logical`, as the image holders' exact rule. */
  private fun designFor(logical: Int): Int? {
    val exact = logical / JBUIScale.scale(1f).toDouble()
    return listOf(floor(exact).toInt(), ceil(exact).toInt()).distinct().filter { it > 0 && JBUIScale.scale(it) == logical }
      .minByOrNull { abs(it - exact) }
  }

  /** The legacy acceptance check: the variant file really is drawn for W×W. */
  private fun acceptedVariant(path: String, classLoader: ClassLoader, design: Int): Boolean =
    (IconLoader.findIcon(path, classLoader) as? CachedImageIcon)?.iconWidth == JBUIScale.scale(design)

  /** Whether the `_dark` file is read, and why. */
  private fun isDarkInEffect(held: CachedImageIcon?, target: ImageTarget): Pair<Boolean, String> {
    if (target.stroked) return false to "stroke modifier: light artwork is loaded and recolored"
    val flags = held?.let { LegacyIcons.Reflect.get<Int>(it, "attributes") }
    if (flags != null && (flags and 0b10) != 0) {
      val dark = (flags and 0b01) != 0
      return dark to "the bridged legacy icon is explicitly ${if (dark) "dark" else "light"} (e.g. HeaderIconUpdater)"
    }
    val themeDark = runCatching { pathTransform.get().isDark }.getOrDefault(!JBColor.isBright())
    return themeDark to "theme is ${if (themeDark) "dark" else "light"} (rendering context default)"
  }

  // ---- rendering & comparison ----

  /** Paints [icon] at [density] into a transparent image of its device size (capped). */
  fun render(label: String, icon: Icon, density: Float): PreviewImage? {
    val width = (icon.iconWidth * density).roundToInt()
    val height = (icon.iconHeight * density).roundToInt()
    if (width <= 0 || height <= 0 || width > MAX_RENDER || height > MAX_RENDER) return null
    @Suppress("UndesirableClassUsage")
    val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g = image.createGraphics()
    try {
      g.scale(density.toDouble(), density.toDouble())
      IconRenderDebugger.untraced { icon.paintIcon(null, g, 0, 0) }
    }
    finally {
      g.dispose()
    }
    return PreviewImage(label, width, height, image.getRGB(0, 0, width, height, null, 0, width))
  }

  /**
   * Percentage of the pixels covered by either image (alpha > 0) whose premultiplied channels all differ by at most
   * [CHANNEL_TOLERANCE]; images of different sizes are compared over the larger size (missing pixels are transparent).
   */
  fun compare(a: PreviewImage, b: PreviewImage): Double {
    val width = maxOf(a.width, b.width)
    val height = maxOf(a.height, b.height)
    var covered = 0
    var matching = 0
    for (y in 0 until height) {
      for (x in 0 until width) {
        val pa = pixel(a, x, y)
        val pb = pixel(b, x, y)
        if ((pa ushr 24) == 0 && (pb ushr 24) == 0) continue
        covered++
        if (close(pa, pb)) matching++
      }
    }
    return if (covered == 0) 100.0 else matching * 100.0 / covered
  }

  private fun pixel(image: PreviewImage, x: Int, y: Int): Int =
    if (x < image.width && y < image.height) image.argb[y * image.width + x] else 0

  private fun close(a: Int, b: Int): Boolean {
    val alphaA = a ushr 24
    val alphaB = b ushr 24
    if (abs(alphaA - alphaB) > CHANNEL_TOLERANCE) return false
    for (shift in intArrayOf(16, 8, 0)) {
      val ca = ((a ushr shift) and 0xFF) * alphaA / 255
      val cb = ((b ushr shift) and 0xFF) * alphaB / 255
      if (abs(ca - cb) > CHANNEL_TOLERANCE) return false
    }
    return true
  }

  fun contentBounds(image: PreviewImage): String? {
    val bounds = contentRect(image) ?: return null
    return "${bounds.width}×${bounds.height} at (${bounds.x}, ${bounds.y})"
  }

  fun contentRect(image: PreviewImage): Rectangle? {
    var minX = Int.MAX_VALUE
    var minY = Int.MAX_VALUE
    var maxX = -1
    var maxY = -1
    for (y in 0 until image.height) {
      for (x in 0 until image.width) {
        if ((image.argb[y * image.width + x] ushr 24) != 0) {
          if (x < minX) minX = x
          if (y < minY) minY = y
          if (x > maxX) maxX = x
          if (y > maxY) maxY = y
        }
      }
    }
    return if (maxX < 0) null else Rectangle(minX, minY, maxX - minX + 1, maxY - minY + 1)
  }

  // ---- files ----

  private fun darkPath(path: String): String? =
    if (path.endsWith(".svg") && !path.endsWith("_dark.svg")) path.removeSuffix(".svg") + "_dark.svg" else null

  private fun svgText(path: String, classLoader: ClassLoader?): String? {
    if (classLoader == null || !path.endsWith(".svg")) return null
    return runCatching {
      classLoader.getResourceAsStream(path.removePrefix("/"))?.use { it.readBytes().decodeToString() }
    }.getOrNull()
  }
}
