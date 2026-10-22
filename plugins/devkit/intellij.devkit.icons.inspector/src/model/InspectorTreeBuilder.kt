// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.className
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.number
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.safeToString
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.simpleClassName
import com.intellij.platform.icons.DeferredIconDescriptor
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.ImageResourceLocation
import com.intellij.platform.icons.impl.DefaultDeferredIconDescriptor
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.intellij.rendering.SwingIcon
import com.intellij.platform.icons.impl.layers.AnimatedIconLayer
import com.intellij.platform.icons.impl.layers.ImageIconLayer
import com.intellij.platform.icons.impl.layers.LayoutIconLayer
import com.intellij.platform.icons.impl.layers.NestedIconLayer
import com.intellij.platform.icons.impl.layers.ShapeIconLayer
import com.intellij.platform.icons.impl.layers.SpacerIconLayer
import com.intellij.platform.icons.impl.layers.SwingIconLayer
import com.intellij.platform.icons.impl.modifiers.AlignIconModifier
import com.intellij.platform.icons.impl.modifiers.AlphaIconModifier
import com.intellij.platform.icons.impl.modifiers.ColorFilterModifier
import com.intellij.platform.icons.impl.modifiers.CombinedIconModifier
import com.intellij.platform.icons.impl.modifiers.CutoutMarginModifier
import com.intellij.platform.icons.impl.modifiers.MarginIconModifier
import com.intellij.platform.icons.impl.modifiers.RootIconModifier
import com.intellij.platform.icons.impl.modifiers.ScaleModifier
import com.intellij.platform.icons.impl.modifiers.StrokeModifier
import com.intellij.platform.icons.impl.modifiers.SvgPatcherModifier
import com.intellij.platform.icons.impl.rendering.debug.ImageResourceLocationDescribers
import com.intellij.platform.icons.impl.rendering.debug.RenderCapture
import com.intellij.platform.icons.impl.rendering.debug.RenderNode
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.scale.FactorScale
import java.util.Collections
import java.util.IdentityHashMap
import javax.swing.Icon

/** Result of building the inspector tree: the tree plus, for capture-backed nodes, the renderer instance behind each node id. */
internal class InspectorTree(
  val root: InspectorNode,
  /** Node id → renderer instance (`IconRenderer` / `IconLayerRenderer`) that actually painted; empty for static trees. */
  val renderers: Map<String, Any>,
  /** Layer node id → address of its layer in the tree's root descriptor (only for layers editable through that root). */
  val layerAddresses: Map<String, LayerAddress> = emptyMap(),
)

/**
 * Builds [InspectorNode] trees, either from a [RenderCapture] (the real renderer tree of one paint, nodes are debuggable) or
 * statically from an [IconDescriptor] (nothing is debuggable). Both paths walk the descriptor/layer/modifier structure; the
 * capture path additionally attaches each node to the renderer instance that rendered it and its scaling.
 *
 * Node ids are `<idPrefix>` + a path of child indices, so rebuilding the same structure yields the same ids.
 */
internal class InspectorTreeBuilder private constructor(private val sizing: SizingContext) {
  private val renderers = LinkedHashMap<String, Any>()
  private val layerAddresses = LinkedHashMap<String, LayerAddress>()
  /** Top-level renderer → its capture, to size top-level nodes from the captured paint. */
  private val capturesByTopRenderer = IdentityHashMap<Any, RenderCapture>()

  private fun register(captures: List<RenderCapture>) {
    for (capture in captures) capturesByTopRenderer[capture.iconRenderer] = capture
  }

  companion object {
    const val MAX_DEPTH: Int = 64
    /** A chain of more than this many single-[NestedIconLayer] wrappers gets a warning. */
    const val WRAPPER_WARNING_THRESHOLD: Int = 3

    const val GROUP_SCALING: String = "Scaling"
    const val GROUP_RENDERER: String = "Renderer"

    /** Static tree for [descriptor] (no renderer instances, `debuggable = false` everywhere). */
    fun buildStatic(idPrefix: String, icon: Icon?, descriptor: IconDescriptor?, compose: ComposePick? = null,
                    sizing: SizingContext = SizingContext.NONE): InspectorTree {
      if (icon != null && icon !is SwingIcon) return buildForLegacy(idPrefix, icon, emptyList(), descriptor, sizing)
      val builder = InspectorTreeBuilder(sizing)
      val rootId = idPrefix + "root"
      val children = if (descriptor == null) emptyList()
      else listOf(builder.withRoot(icon) {
        builder.withThemeScope(ThemeInspection.rootScope(icon, null)) { builder.iconNode(descriptor, null, "$rootId/0", 1, false, emptyList()) }
      })
      val root = if (icon == null && compose != null) builder.composeIconNode(rootId, compose, compose.capture, children)
      else builder.swingIconNode(rootId, icon, null, children)
      return InspectorTree(root, builder.renderers.toMap(), builder.layerAddresses.toMap())
    }

    /**
     * Tree for a Compose element that is not rendered by the icons API (e.g. a Jewel `Icon(painter)` / `IconKey`): there is
     * no descriptor or renderer, only what the semantics tree tells.
     */
    fun buildComposeLegacy(idPrefix: String, compose: ComposePick): InspectorTree {
      val rootId = idPrefix + "root"
      val semantics = compose.semantics
      val root = InspectorNode(
        id = rootId,
        kind = InspectorNodeKind.SWING_ICON,
        title = DevkitIconsInspectorBundle.message("model.compose.legacy.title"),
        subtitle = semantics?.contentDescription?.let { "\"$it\"" } ?: compose.hostDescription,
        properties = ComposeIcons.properties(compose, null),
        warning = DevkitIconsInspectorBundle.message("model.compose.legacy.warning"),
        origin = InspectorNodeOrigin.LEGACY,
        sizes = IconSizing.forComposeSemantics(semantics),
      )
      return InspectorTree(root, emptyMap())
    }

    /** Tree of the renderer instances that actually ran for [capture]; every node backed by a renderer is debuggable. */
    fun buildFromCapture(idPrefix: String, icon: Icon?, capture: RenderCapture, sizing: SizingContext = SizingContext.NONE): InspectorTree {
      val builder = InspectorTreeBuilder(sizing)
      builder.register(listOf(capture))
      val rootId = idPrefix + "root"
      val pool = capture.roots.toMutableList()
      val children = ArrayList<InspectorNode>()
      val main = pool.firstOrNull { it.subject === capture.descriptor } ?: pool.firstOrNull()
      if (main != null) pool.remove(main)
      children.add(builder.withRoot(icon) {
        builder.withThemeScope(ThemeInspection.rootScope(icon, capture.iconRenderer)) {
          builder.iconNode(capture.descriptor, main, "$rootId/0", 1, false, emptyList())
        }
      })
      for (extra in pool) {
        children.add(builder.genericNode(extra, "$rootId/${children.size}", 1))
      }
      val root = if (icon == null && capture.isCompose) builder.composeIconNode(rootId, null, capture, children)
      else builder.swingIconNode(rootId, icon, capture, children)
      return InspectorTree(root, builder.renderers.toMap(), builder.layerAddresses.toMap())
    }

    /**
     * Tree for a picked LEGACY Swing icon (not a [SwingIcon]): the root is the legacy icon itself with its legacy structure
     * expanded. [SwingIcon]s found inside become BRIDGE nodes; their descriptor subtree is matched to the [captures] of their
     * paints (by descriptor identity), so those nodes are debuggable. [bridgedDescriptor] (the `toIconDescriptor()` result)
     * is only shown, as a separate labelled node, when it is more than the trivial swing-layer wrapper of [icon].
     */
    fun buildForLegacy(idPrefix: String, icon: Icon, captures: List<RenderCapture>, bridgedDescriptor: IconDescriptor?,
                       sizing: SizingContext = SizingContext.NONE): InspectorTree {
      val builder = InspectorTreeBuilder(sizing)
      builder.register(captures)
      val rootId = idPrefix + "root"
      val pool = if (captures.isEmpty()) null else captures.flatMap { it.roots }.toMutableList()
      var root = builder.legacyNode(icon, pool, rootId, 0, isRoot = true)
      val extra = ArrayList<InspectorNode>()
      if (bridgedDescriptor != null && !isTrivialBridge(bridgedDescriptor, icon)) {
        val bridgeId = "$rootId/${root.children.size}"
        extra.add(InspectorNode(
          id = bridgeId,
          kind = InspectorNodeKind.OTHER,
          title = DevkitIconsInspectorBundle.message("model.node.bridged.conversion"),
          subtitle = simpleClassName(bridgedDescriptor),
          properties = listOf(InspectorProperty("class", className(bridgedDescriptor))),
          children = listOf(builder.withVisiting(icon) {
            builder.withThemeScope(ThemeInspection.rootScope(null, null)) { builder.iconNode(bridgedDescriptor, null, "$bridgeId/0", 1, false) }
          }),
          origin = InspectorNodeOrigin.BRIDGE,
        ))
      }
      // render nodes that matched nothing (should not happen; kept visible rather than dropped)
      for (leftover in pool.orEmpty()) {
        extra.add(builder.genericNode(leftover, "$rootId/${root.children.size + extra.size}", 1))
      }
      if (extra.isNotEmpty()) root = root.copy(children = root.children + extra)
      return InspectorTree(root, builder.renderers.toMap())
    }

    /** `toIconDescriptor()` of a legacy icon without a dedicated conversion: one swing layer wrapping the icon itself. */
    fun isTrivialBridge(descriptor: IconDescriptor, icon: Icon): Boolean {
      val layer = (descriptor as? DefaultLayeredIconDescriptor)?.layers?.singleOrNull() as? SwingIconLayer ?: return false
      return layer.legacyIcon === icon
    }

    /** [SwingIcon]s inside the legacy structure of [icon] (including [icon] itself), in tree order, without duplicates. */
    fun collectSwingIcons(icon: Icon): List<SwingIcon> {
      val result = ArrayList<SwingIcon>()
      val visited = Collections.newSetFromMap(IdentityHashMap<Any, Boolean>())
      fun visit(current: Icon, depth: Int) {
        if (depth > MAX_DEPTH || !visited.add(current)) return
        if (current is SwingIcon) {
          result.add(current)
          return
        }
        for (child in LegacyIcons.children(current)) {
          child.icon?.let { visit(it, depth + 1) }
        }
      }
      visit(icon, 0)
      return result
    }

    /** The modifiers of [modifier] in application order, with [CombinedIconModifier]s flattened and no-op roots removed. */
    fun flattenModifiers(modifier: IconModifier): List<IconModifier> {
      val result = ArrayList<IconModifier>()
      fun visit(current: IconModifier, depth: Int) {
        if (depth > MAX_DEPTH) return
        when {
          current is CombinedIconModifier -> {
            // CombinedIconModifier.applyTo = other.applyTo(root.applyTo(layout)): root is applied first
            visit(current.root, depth + 1)
            visit(current.other, depth + 1)
          }
          current === RootIconModifier || current === IconModifier -> Unit
          else -> result.add(current)
        }
      }
      visit(modifier, 0)
      return result
    }

    /** Number of directly nested single-[NestedIconLayer] wrappers starting at [layer] (1 for a lone nested layer). */
    fun wrapperChainLength(layer: NestedIconLayer): Int {
      var length = 1
      var current = layer
      while (length < MAX_DEPTH) {
        val next = singleNestedLayer(current.iconDescriptor) ?: break
        current = next
        length++
      }
      return length
    }

    private fun singleNestedLayer(descriptor: IconDescriptor): NestedIconLayer? =
      ((descriptor as? DefaultLayeredIconDescriptor)?.layers?.singleOrNull()) as? NestedIconLayer
  }

  // ---- root ----

  private fun swingIconNode(id: String, icon: Icon?, capture: RenderCapture?, children: List<InspectorNode>): InspectorNode {
    val properties = ArrayList<InspectorProperty>()
    if (icon != null) {
      properties.add(InspectorProperty("class", className(icon)))
      properties.add(InspectorProperty("size", InspectorFormatting.iconSize(icon)))
      if (icon is SwingIcon) {
        properties.add(InspectorProperty("iconScale", InspectorFormatting.scale(icon.scale)))
        properties.add(InspectorProperty("scale (ScalableIcon)", runCatching { number(icon.getScale()) }.getOrElse { "?" }))
      }
      properties.add(InspectorProperty("identity", InspectorFormatting.identity(icon)))
      properties.add(InspectorProperty("toString", safeToString(icon)))
    }
    if (capture != null) {
      val info = capture.paintInfo
      properties.add(InspectorProperty("top-level renderer", InspectorFormatting.identity(capture.iconRenderer), GROUP_RENDERER))
      properties.add(InspectorProperty("component", info.componentClass, GROUP_RENDERER))
      properties.add(InspectorProperty("paint scale", info.scale ?: "none", GROUP_RENDERER))
      properties.add(InspectorProperty("final size", InspectorFormatting.size(info.finalWidth, info.finalHeight), GROUP_RENDERER))
      properties.add(InspectorProperty("density", number(info.density), GROUP_RENDERER))
    }
    if (icon is SwingIcon) {
      val painted = paintedVariantSize() ?: capture?.paintInfo?.let { SizeVariants.variantSize(it.finalWidth, it.density) }
      SizeVariants.forDescriptor(capture?.descriptor ?: icon.iconDescriptor, painted)?.let(properties::add)
    }
    val subtitle = if (icon == null) null else InspectorFormatting.iconSize(icon)
    val sizes = when (icon) {
      null -> null
      is SwingIcon -> IconSizing.forSwingIconRoot(icon, capture, sizing, isPicked = true)
      else -> IconSizing.forLegacy(icon, sizing, isPicked = true)
    }
    return InspectorNode(
      id = id,
      kind = InspectorNodeKind.SWING_ICON,
      title = if (icon == null) "Icon" else simpleClassName(icon),
      subtitle = subtitle,
      properties = properties,
      children = children,
      origin = if (icon == null || icon is SwingIcon) InspectorNodeOrigin.BRIDGE else InspectorNodeOrigin.LEGACY,
      sizes = sizes,
    )
  }

  /** Root of a Compose-drawn icon: the composable's draw (Jewel `Modifier.iconRender`) of an icons-API renderer. */
  private fun composeIconNode(id: String, pick: ComposePick?, capture: RenderCapture?, children: List<InspectorNode>): InspectorNode {
    val properties = ArrayList<InspectorProperty>()
    if (capture != null) {
      properties.add(InspectorProperty("top-level renderer", InspectorFormatting.identity(capture.iconRenderer), GROUP_RENDERER))
    }
    properties.addAll(ComposeIcons.properties(pick, capture))
    val info = capture?.composePaintInfo
    return InspectorNode(
      id = id,
      kind = InspectorNodeKind.SWING_ICON,
      title = DevkitIconsInspectorBundle.message("model.compose.icon.title"),
      subtitle = info?.let { "${InspectorFormatting.size(it.finalWidth, it.finalHeight)} px @${number(it.density)}" },
      properties = properties,
      children = children,
      origin = InspectorNodeOrigin.ICONS_API,
      sizes = capture?.root?.let { IconSizing.forRenderNode(it, capture, topLevel = true) }
              ?: capture?.descriptor?.let(IconSizing::forStaticDescriptor),
    )
  }

  // ---- descriptors ----

  private fun iconNode(descriptor: IconDescriptor, renderNode: RenderNode?, id: String, depth: Int, chainLink: Boolean,
                       address: LayerAddress? = null): InspectorNode {
    if (depth > MAX_DEPTH) return depthLimitNode(id)
    val properties = ArrayList<InspectorProperty>()
    properties.add(InspectorProperty("class", className(descriptor)))
    val children = ArrayList<InspectorNode>()
    val pool = renderNode?.children?.toMutableList()
    var subtitle: String? = null

    when (descriptor) {
      is DefaultLayeredIconDescriptor -> {
        val layers = descriptor.layers
        subtitle = if (layers.size == 1) "1 layer" else "${layers.size} layers"
        properties.add(InspectorProperty("layers", layers.size.toString()))
        val singleNested = chainLink && layers.size == 1 && layers[0] is NestedIconLayer
        layers.forEachIndexed { index, layer ->
          children.add(layerNode(layer, take(pool, layer), childId(id, children), depth + 1, singleNested,
                                 address?.plus(LayerStep.Layer(index))))
        }
      }
      is DeferredIconDescriptor -> {
        subtitle = safeToString(descriptor.id)
        properties.add(InspectorProperty("id", safeToString(descriptor.id)))
        properties.add(InspectorProperty("id class", className(descriptor.id)))
        val placeholder = descriptor.placeholder
        properties.add(InspectorProperty("placeholder", if (placeholder == null) "none (resolved or unset)" else simpleClassName(placeholder)))
        if (descriptor is DefaultDeferredIconDescriptor) {
          properties.add(InspectorProperty("resolved", (placeholder == null).toString()))
        }
        if (pool != null) {
          // the deferred renderer renders its delegate (placeholder or resolved icon) as its single child
          for (child in pool.toList()) {
            pool.remove(child)
            val node = genericNode(child, childId(id, children), depth + 1)
            val role = if (placeholder != null && child.subject === placeholder) "placeholder" else "resolved icon"
            children.add(node.copy(subtitle = listOfNotNull(role, node.subtitle).joinToString(" · ")))
          }
        }
        else if (placeholder != null) {
          val node = iconNode(placeholder, null, childId(id, children), depth + 1, false)
          children.add(node.copy(subtitle = listOfNotNull("placeholder", node.subtitle).joinToString(" · ")))
        }
      }
      else -> {
        properties.add(InspectorProperty("toString", safeToString(descriptor)))
      }
    }
    appendLeftovers(pool, id, children, depth)
    if (renderNode != null) addRendererProperties(renderNode, properties)
    return InspectorNode(
      id = id,
      kind = InspectorNodeKind.ICON,
      title = simpleClassName(descriptor),
      subtitle = subtitle(renderNode, subtitle),
      properties = properties,
      children = children,
      debuggable = register(id, renderNode),
      sizes = if (renderNode != null) renderNodeSizes(renderNode) else IconSizing.forStaticDescriptor(descriptor),
    )
  }

  // ---- layers ----

  private fun layerNode(layer: IconLayer, renderNode: RenderNode?, id: String, depth: Int, chainLink: Boolean,
                        address: LayerAddress? = null): InspectorNode {
    if (address != null && depth <= MAX_DEPTH) layerAddresses[id] = address
    if (depth > MAX_DEPTH) return depthLimitNode(id)
    val properties = ArrayList<InspectorProperty>()
    properties.add(InspectorProperty("class", className(layer)))
    val children = ArrayList<InspectorNode>()
    val pool = renderNode?.children?.toMutableList()
    var subtitle: String? = null
    var warning: String? = null

    for (modifier in flattenModifiers(layer.modifier)) {
      children.add(modifierNode(modifier, childId(id, children)))
    }

    when (layer) {
      is ImageIconLayer -> {
        val fields = describeLocation(layer.loader)
        subtitle = fields.firstOrNull { it.first == "path" }?.second ?: simpleClassName(layer.loader)
        children.add(resourceNode(layer.loader, fields, childId(id, children)))
        themeScope?.let { properties.addAll(ThemeInspection.imageLayerRows(layer, it)) }
      }
      is NestedIconLayer -> {
        val nested = layer.iconDescriptor
        subtitle = simpleClassName(nested)
        val chainLength = wrapperChainLength(layer)
        if (!chainLink && chainLength > WRAPPER_WARNING_THRESHOLD) {
          warning = DevkitIconsInspectorBundle.message("model.warning.nested.wrappers", chainLength)
          properties.add(InspectorProperty("nested wrapper chain", chainLength.toString()))
        }
        val childRender = take(pool, nested) ?: pool?.firstOrNull { it.isIconRenderer }?.also { pool.remove(it) }
        children.add(withThemeScope(themeScope?.adjustTo(layer, themeLabel(layer, id))) {
          iconNode(nested, childRender, childId(id, children), depth + 1, chainLink = true, address?.plus(LayerStep.Nested))
        })
      }
      is LayoutIconLayer -> {
        subtitle = "${layer.direction} · ${layer.nestedLayers.size}"
        properties.add(InspectorProperty("direction", layer.direction.toString()))
        properties.add(InspectorProperty("nested layers", layer.nestedLayers.size.toString()))
        withThemeScope(themeScope?.adjustTo(layer, themeLabel(layer, id))) {
          layer.nestedLayers.forEachIndexed { index, nestedLayer ->
            children.add(layerNode(nestedLayer, take(pool, nestedLayer), childId(id, children), depth + 1, false,
                                   address?.plus(LayerStep.LayoutChild(index))))
          }
        }
      }
      is SwingIconLayer -> {
        val legacy = layer.legacyIcon
        subtitle = "${simpleClassName(legacy)} ${InspectorFormatting.iconSize(legacy)}"
        properties.add(InspectorProperty("legacy icon class", className(legacy)))
        properties.add(InspectorProperty("legacy icon size", InspectorFormatting.iconSize(legacy)))
        properties.add(InspectorProperty("legacy icon", InspectorFormatting.identity(legacy)))
        properties.add(InspectorProperty("legacy toString", safeToString(legacy)))
        themeScope?.let { properties.addAll(ThemeInspection.swingLayerRows(layer, it)) }
        // the legacy icon is painted (rasterized) by this layer's renderer: SwingIcons inside it render below it
        children.add(legacyNode(legacy, pool, childId(id, children), depth + 1, isRoot = false))
      }
      is ShapeIconLayer -> {
        subtitle = "${InspectorFormatting.shape(layer.shape)} ${InspectorFormatting.color(layer.color)}"
        properties.add(InspectorProperty("color", InspectorFormatting.color(layer.color)))
        properties.add(InspectorProperty("shape", InspectorFormatting.shape(layer.shape)))
      }
      is AnimatedIconLayer -> {
        subtitle = if (layer.frames.size == 1) "1 frame" else "${layer.frames.size} frames"
        properties.add(InspectorProperty("frames", layer.frames.size.toString()))
        properties.add(InspectorProperty("total duration", "${layer.frames.sumOf { it.duration + it.fadeIn + it.fadeOut }} ms"))
        val frameScope = themeScope?.adjustTo(layer, themeLabel(layer, id))
        layer.frames.forEachIndexed { index, frame ->
          val frameId = childId(id, children)
          val frameChildren = ArrayList<InspectorNode>()
          withThemeScope(frameScope) {
            frame.layers.forEachIndexed { layerIndex, frameLayer ->
              frameChildren.add(layerNode(frameLayer, take(pool, frameLayer), childId(frameId, frameChildren), depth + 2, false,
                                          address?.plus(LayerStep.Frame(index, layerIndex))))
            }
          }
          children.add(InspectorNode(
            id = frameId,
            kind = InspectorNodeKind.OTHER,
            title = "Frame #$index",
            subtitle = "${frame.duration} ms",
            properties = listOf(
              InspectorProperty("duration", "${frame.duration} ms"),
              InspectorProperty("fadeIn", "${frame.fadeIn} ms"),
              InspectorProperty("fadeOut", "${frame.fadeOut} ms"),
              InspectorProperty("layers", frame.layers.size.toString()),
            ),
            children = frameChildren,
          ))
        }
      }
      is SpacerIconLayer -> Unit
      else -> properties.add(InspectorProperty("toString", safeToString(layer)))
    }
    appendLeftovers(pool, id, children, depth)
    if (renderNode != null) addRendererProperties(renderNode, properties)
    return InspectorNode(
      id = id,
      kind = InspectorNodeKind.LAYER,
      title = simpleClassName(layer),
      subtitle = subtitle(renderNode, subtitle),
      properties = properties,
      children = children,
      debuggable = register(id, renderNode),
      warning = warning,
      origin = if (layer is SwingIconLayer) InspectorNodeOrigin.BRIDGE else InspectorNodeOrigin.ICONS_API,
      sizes = if (renderNode != null) renderNodeSizes(renderNode) else IconSizing.forStaticLayer(),
      modifiers = runCatching { ModifierEditing.describe(layer.modifier) }.getOrDefault(emptyList()),
    )
  }

  // ---- theme ----

  /** The theme scope of the layers being built (see [ThemeInspection.ThemeScope]); null if it cannot be determined. */
  private var themeScope: ThemeInspection.ThemeScope? = null

  private fun <T> withThemeScope(scope: ThemeInspection.ThemeScope?, block: () -> T): T {
    val previous = themeScope
    themeScope = scope
    try {
      return block()
    }
    finally {
      themeScope = previous
    }
  }

  private fun themeLabel(layer: IconLayer, id: String): String = "${simpleClassName(layer)} [$id]"

  // ---- legacy Swing icons ----

  /** Legacy icons on the current path, to stop cycles (e.g. a swing layer wrapping the picked icon). */
  private val visiting: MutableSet<Any> = Collections.newSetFromMap(IdentityHashMap())

  private fun <T> withRoot(icon: Icon?, block: () -> T): T = if (icon == null) block() else withVisiting(icon, block)

  private fun <T> withVisiting(icon: Icon, block: () -> T): T {
    val added = visiting.add(icon)
    try {
      return block()
    }
    finally {
      if (added) visiting.remove(icon)
    }
  }

  /**
   * A legacy Swing [icon] with its structure expanded as LEGACY nodes. A [SwingIcon] becomes a BRIDGE node whose descriptor
   * subtree is matched against [pool] (render nodes of the paint that rasterized / painted it).
   */
  private fun legacyNode(icon: Icon, pool: MutableList<RenderNode>?, id: String, depth: Int, isRoot: Boolean,
                         role: String? = null): InspectorNode {
    if (depth > MAX_DEPTH) return depthLimitNode(id)
    if (icon in visiting) {
      return InspectorNode(
        id = id,
        kind = InspectorNodeKind.OTHER,
        title = simpleClassName(icon),
        subtitle = listOfNotNull(role, "cycle").joinToString(" · "),
        properties = listOf(InspectorProperty("class", className(icon)), InspectorProperty("identity", InspectorFormatting.identity(icon))),
        warning = DevkitIconsInspectorBundle.message("model.warning.icon.cycle"),
        origin = if (icon is SwingIcon) InspectorNodeOrigin.BRIDGE else InspectorNodeOrigin.LEGACY,
      )
    }
    if (icon is SwingIcon && !isRoot) return nestedSwingIconNode(icon, pool, id, depth, role)
    return withVisiting(icon) {
      val properties = ArrayList<InspectorProperty>()
      properties.add(InspectorProperty("class", className(icon)))
      properties.add(InspectorProperty("size", InspectorFormatting.iconSize(icon)))
      properties.add(InspectorProperty("identity", InspectorFormatting.identity(icon)))
      properties.addAll(LegacyIcons.properties(icon))
      properties.add(InspectorProperty("toString", safeToString(icon)))
      if (isRoot) SizeVariants.forLegacy(icon, paintedVariantSize())?.let(properties::add)
      val children = ArrayList<InspectorNode>()
      for (child in LegacyIcons.children(icon)) {
        val childIcon = child.icon
        if (childIcon == null) {
          children.add(InspectorNode(id = childId(id, children), kind = InspectorNodeKind.OTHER, title = "null", subtitle = child.role,
                                     properties = child.properties, origin = InspectorNodeOrigin.LEGACY))
          continue
        }
        val node = legacyNode(childIcon, pool, childId(id, children), depth + 1, isRoot = false, role = child.role)
        children.add(if (child.properties.isEmpty()) node else node.copy(properties = child.properties + node.properties))
      }
      val summary = LegacyIcons.subtitle(icon)
      InspectorNode(
        id = id,
        kind = if (isRoot) InspectorNodeKind.SWING_ICON else InspectorNodeKind.OTHER,
        title = simpleClassName(icon),
        subtitle = listOfNotNull(role, summary, InspectorFormatting.iconSize(icon)).joinToString(" · "),
        properties = properties,
        children = children,
        origin = InspectorNodeOrigin.LEGACY,
        sizes = IconSizing.forLegacy(icon, sizing, isPicked = isRoot),
      )
    }
  }

  /** A [SwingIcon] nested in a legacy icon: BRIDGE node with the descriptor subtree, debuggable if it was rendered in [pool]. */
  private fun nestedSwingIconNode(icon: SwingIcon, pool: MutableList<RenderNode>?, id: String, depth: Int, role: String?): InspectorNode {
    val descriptor = icon.iconDescriptor
    val renderNode = take(pool, descriptor)
                     ?: pool?.firstOrNull { it.isIconRenderer && it.subject == descriptor }?.also { pool.remove(it) }
    val properties = ArrayList<InspectorProperty>()
    properties.add(InspectorProperty("class", className(icon)))
    properties.add(InspectorProperty("size", InspectorFormatting.iconSize(icon)))
    properties.add(InspectorProperty("iconScale", InspectorFormatting.scale(icon.scale)))
    properties.add(InspectorProperty("identity", InspectorFormatting.identity(icon)))
    if (pool != null && renderNode == null) {
      properties.add(InspectorProperty("render capture", DevkitIconsInspectorBundle.message("model.node.not.rendered.in.capture")))
    }
    val child = withVisiting(icon) {
      withThemeScope(ThemeInspection.rootScope(icon, renderNode?.renderer)) { iconNode(descriptor, renderNode, "$id/0", depth + 1, false) }
    }
    return InspectorNode(
      id = id,
      kind = InspectorNodeKind.SWING_ICON,
      title = simpleClassName(icon),
      subtitle = listOfNotNull(role, InspectorFormatting.iconSize(icon)).joinToString(" · "),
      properties = properties,
      children = listOf(child),
      origin = InspectorNodeOrigin.BRIDGE,
      sizes = IconSizing.forSwingIconRoot(icon, renderNode?.let { capturesByTopRenderer[it.renderer] }, sizing, isPicked = false),
    )
  }

  // ---- modifiers & resources ----

  private fun modifierNode(modifier: IconModifier, id: String): InspectorNode {
    val properties = ArrayList<InspectorProperty>()
    properties.add(InspectorProperty("class", className(modifier)))
    val subtitle: String = when (modifier) {
      is ScaleModifier -> {
        val scale = modifier.scale
        properties.add(InspectorProperty("scale", InspectorFormatting.scale(scale)))
        properties.add(InspectorProperty("scale class", className(scale)))
        if (scale is FactorScale) properties.add(InspectorProperty("factor", number(scale.factor)))
        if (scale is FactorScale) "factor=${number(scale.factor)}" else InspectorFormatting.scale(scale)
      }
      is MarginIconModifier -> {
        properties.add(InspectorProperty("left", InspectorFormatting.unit(modifier.left)))
        properties.add(InspectorProperty("top", InspectorFormatting.unit(modifier.top)))
        properties.add(InspectorProperty("right", InspectorFormatting.unit(modifier.right)))
        properties.add(InspectorProperty("bottom", InspectorFormatting.unit(modifier.bottom)))
        "l=${InspectorFormatting.unit(modifier.left)} t=${InspectorFormatting.unit(modifier.top)} " +
        "r=${InspectorFormatting.unit(modifier.right)} b=${InspectorFormatting.unit(modifier.bottom)}"
      }
      is AlphaIconModifier -> {
        properties.add(InspectorProperty("alpha", number(modifier.alpha)))
        "alpha=${number(modifier.alpha)}"
      }
      is AlignIconModifier -> {
        properties.add(InspectorProperty("align", InspectorFormatting.align(modifier.align)))
        InspectorFormatting.align(modifier.align)
      }
      is ColorFilterModifier -> {
        properties.add(InspectorProperty("color filter class", className(modifier.colorFilter)))
        properties.add(InspectorProperty("color filter", safeToString(modifier.colorFilter)))
        simpleClassName(modifier.colorFilter)
      }
      is SvgPatcherModifier -> {
        val patcher = modifier.svgPatcher
        properties.add(InspectorProperty("operations", patcher.operations.size.toString()))
        properties.add(InspectorProperty("filtered operations", patcher.filteredOperations.size.toString()))
        patcher.operations.forEachIndexed { index, operation ->
          properties.add(InspectorProperty("#$index", safeToString(operation), "Operations"))
        }
        patcher.filteredOperations.forEachIndexed { index, operation ->
          properties.add(InspectorProperty("#$index", safeToString(operation), "Filtered operations"))
        }
        "${patcher.operations.size} ops"
      }
      is CutoutMarginModifier -> {
        properties.add(InspectorProperty("size", InspectorFormatting.unit(modifier.size)))
        "size=${InspectorFormatting.unit(modifier.size)}"
      }
      is StrokeModifier -> {
        properties.add(InspectorProperty("color", InspectorFormatting.color(modifier.color)))
        InspectorFormatting.color(modifier.color)
      }
      else -> {
        properties.add(InspectorProperty("toString", safeToString(modifier)))
        safeToString(modifier)
      }
    }
    return InspectorNode(id = id, kind = InspectorNodeKind.MODIFIER, title = simpleClassName(modifier), subtitle = subtitle,
                         properties = properties)
  }

  private fun resourceNode(location: ImageResourceLocation, fields: List<Pair<String, String?>>, id: String): InspectorNode {
    val properties = ArrayList<InspectorProperty>()
    properties.add(InspectorProperty("class", className(location)))
    fields.mapTo(properties) { (name, value) -> InspectorProperty(name, value) }
    return InspectorNode(
      id = id,
      kind = InspectorNodeKind.RESOURCE,
      title = simpleClassName(location),
      subtitle = fields.firstOrNull { it.first == "path" }?.second,
      properties = properties,
    )
  }

  private fun describeLocation(location: ImageResourceLocation): List<Pair<String, String?>> = try {
    ImageResourceLocationDescribers.describe(location)
  }
  catch (e: Throwable) {
    listOf("class" to className(location), "describe failed" to e.toString())
  }

  // ---- render nodes without a matching descriptor element ----

  private fun genericNode(renderNode: RenderNode, id: String, depth: Int): InspectorNode {
    return when (val subject = renderNode.subject) {
      is IconDescriptor -> iconNode(subject, renderNode, id, depth, false)
      is IconLayer -> layerNode(subject, renderNode, id, depth, false)
      else -> {
        if (depth > MAX_DEPTH) return depthLimitNode(id)
        val properties = ArrayList<InspectorProperty>()
        properties.add(InspectorProperty("subject", safeToString(subject)))
        addRendererProperties(renderNode, properties)
        val children = ArrayList<InspectorNode>()
        for (child in renderNode.children) {
          children.add(genericNode(child, childId(id, children), depth + 1))
        }
        InspectorNode(
          id = id,
          kind = if (renderNode.isIconRenderer) InspectorNodeKind.ICON else InspectorNodeKind.OTHER,
          title = InspectorFormatting.simpleName(renderNode.renderer.javaClass),
          subtitle = subtitle(renderNode, null),
          properties = properties,
          children = children,
          debuggable = register(id, renderNode),
          sizes = renderNodeSizes(renderNode),
        )
      }
    }
  }

  private fun appendLeftovers(pool: MutableList<RenderNode>?, parentId: String, children: MutableList<InspectorNode>, depth: Int) {
    if (pool.isNullOrEmpty()) return
    for (child in pool) {
      children.add(genericNode(child, childId(parentId, children), depth + 1))
    }
    pool.clear()
  }

  // ---- helpers ----

  /** Removes and returns the render node whose subject is exactly [subject] (identity). */
  private fun take(pool: MutableList<RenderNode>?, subject: Any): RenderNode? {
    if (pool == null) return null
    val index = pool.indexOfFirst { it.subject === subject }
    return if (index < 0) null else pool.removeAt(index)
  }

  /** Painted frame of the picked icon in size-variant units (logical, without user scale), if it was measured. */
  private fun paintedVariantSize(): Int? {
    val frame = sizing.rootPaintedFrame ?: return null
    val scale = sizing.screenScale ?: return null
    return SizeVariants.variantSize(frame.width, scale)
  }

  private fun renderNodeSizes(renderNode: RenderNode): IconSizes {
    val capture = capturesByTopRenderer[renderNode.renderer]
    return IconSizing.forRenderNode(renderNode, capture, topLevel = capture != null)
  }

  private fun register(id: String, renderNode: RenderNode?): Boolean {
    if (renderNode == null) return false
    renderers[id] = renderNode.renderer
    return true
  }

  private fun addRendererProperties(renderNode: RenderNode, properties: MutableList<InspectorProperty>) {
    properties.add(InspectorProperty("renderer", InspectorFormatting.identity(renderNode.renderer), GROUP_RENDERER))
    properties.add(InspectorProperty("renderer class", renderNode.rendererClass, GROUP_RENDERER))
    val scaling = renderNode.scaling
    properties.add(InspectorProperty("display density", number(scaling.displayDensity), GROUP_SCALING))
    properties.add(InspectorProperty("context scale", number(scaling.contextScale), GROUP_SCALING))
    properties.add(InspectorProperty("dimensions (device px)", InspectorFormatting.dimensions(renderNode.dimensions), GROUP_SCALING))
    properties.add(InspectorProperty("offset", "(${renderNode.offsetX}, ${renderNode.offsetY})", GROUP_SCALING))
  }

  private fun subtitle(renderNode: RenderNode?, base: String?): String? {
    if (renderNode == null) return base
    val scaling = InspectorFormatting.scalingSubtitle(renderNode.scaling, renderNode.dimensions)
    return if (base == null) scaling else "$base · $scaling"
  }

  private fun depthLimitNode(id: String): InspectorNode = InspectorNode(
    id = id,
    kind = InspectorNodeKind.OTHER,
    title = "…",
    warning = DevkitIconsInspectorBundle.message("model.warning.depth.limit", MAX_DEPTH),
  )

  private fun childId(parentId: String, siblings: List<*>): String = "$parentId/${siblings.size}"
}
