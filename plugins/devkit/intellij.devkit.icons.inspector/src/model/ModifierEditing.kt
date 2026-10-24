// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.model.InspectorFormatting.number
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.safeToString
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.simpleClassName
import com.intellij.platform.icons.IconDescriptor
import com.intellij.platform.icons.design.BlendMode
import com.intellij.platform.icons.design.Color
import com.intellij.platform.icons.design.DisplayPoint
import com.intellij.platform.icons.design.IconHorizontalAlign
import com.intellij.platform.icons.design.IconUnit
import com.intellij.platform.icons.design.IconVerticalAlign
import com.intellij.platform.icons.design.Pixel
import com.intellij.platform.icons.design.SRGBColor
import com.intellij.platform.icons.design.dp
import com.intellij.platform.icons.design.iconAlign
import com.intellij.platform.icons.design.px
import com.intellij.platform.icons.design.sRGB
import com.intellij.platform.icons.filters.ColorFilter
import com.intellij.platform.icons.filters.tint
import com.intellij.platform.icons.impl.DefaultLayeredIconDescriptor
import com.intellij.platform.icons.impl.IconAnimationFrame
import com.intellij.platform.icons.impl.filters.TintColorFilter
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
import com.intellij.platform.icons.impl.modifiers.CutoutMarginModifier
import com.intellij.platform.icons.impl.modifiers.MarginIconModifier
import com.intellij.platform.icons.impl.modifiers.ScaleModifier
import com.intellij.platform.icons.impl.modifiers.SizeVariantModifier
import com.intellij.platform.icons.impl.modifiers.StrokeModifier
import com.intellij.platform.icons.layers.IconLayer
import com.intellij.platform.icons.modifiers.IconModifier
import com.intellij.platform.icons.modifiers.SizeVariantPolicy
import com.intellij.platform.icons.modifiers.align
import com.intellij.platform.icons.modifiers.alpha
import com.intellij.platform.icons.modifiers.colorFilter
import com.intellij.platform.icons.modifiers.cutoutMargin
import com.intellij.platform.icons.modifiers.margin
import com.intellij.platform.icons.modifiers.scale
import com.intellij.platform.icons.modifiers.sizeVariants
import com.intellij.platform.icons.modifiers.stroke
import com.intellij.platform.icons.modifiers.then
import com.intellij.platform.icons.scale.FactorScale
import com.intellij.platform.icons.scale.FillAreaScale
import com.intellij.platform.icons.scale.FitAreaScale
import com.intellij.platform.icons.scale.factor
import com.intellij.platform.icons.scale.fillArea
import com.intellij.platform.icons.scale.fitArea
import kotlin.math.roundToInt

/** One step from a descriptor down to a layer; a [LayerAddress] locates a layer node's layer in the inspected descriptor. */
internal sealed interface LayerStep {
  /** Layer [index] of a `DefaultLayeredIconDescriptor`. */
  data class Layer(val index: Int) : LayerStep
  /** Into the descriptor of a `NestedIconLayer`. */
  data object Nested : LayerStep
  /** Nested layer [index] of a `LayoutIconLayer`. */
  data class LayoutChild(val index: Int) : LayerStep
  /** Layer [index] of frame [frame] of an `AnimatedIconLayer`. */
  data class Frame(val frame: Int, val index: Int) : LayerStep
}

internal typealias LayerAddress = List<LayerStep>

/**
 * Inspecting and editing the modifiers of new-API layers. Edits never mutate: they produce an edited copy of the descriptor
 * that rebuilds only the path from the root to the edited layer; the modifier chain is rebuilt with the public icons-API
 * modifier functions (`scale`, `margin`, `alpha`, `align`, `colorFilter`, `stroke`, `cutoutMargin`, `sizeVariants`) combined
 * with `then`. Unchanged modifiers keep their original instances.
 */
internal object ModifierEditing {
  private const val UNIT_HINT = "use e.g. \"2dp\", \"1.5dp\" or \"3px\""

  // ---- describe ----

  fun describe(modifier: IconModifier): List<EditableModifier> =
    InspectorTreeBuilder.flattenModifiers(modifier).mapIndexed { index, it -> describeOne("m$index", it) }

  private fun describeOne(id: String, modifier: IconModifier): EditableModifier {
    val title = simpleClassName(modifier)
    return when (modifier) {
      is ScaleModifier -> EditableModifier(id, EditableModifierKind.SCALE, title, scaleFields(modifier.scale))
      is MarginIconModifier -> EditableModifier(id, EditableModifierKind.MARGIN, title, listOf(
        unitField("left", modifier.left), unitField("top", modifier.top),
        unitField("right", modifier.right), unitField("bottom", modifier.bottom)))
      is AlphaIconModifier -> EditableModifier(id, EditableModifierKind.ALPHA, title,
                                               listOf(ModifierField("alpha", "alpha (0..1)", ModifierFieldType.NUMBER, number(modifier.alpha))))
      is AlignIconModifier -> EditableModifier(id, EditableModifierKind.ALIGN, title, listOf(
        ModifierField("vertical", "vertical", ModifierFieldType.CHOICE, modifier.align.verticalAlign.name,
                      IconVerticalAlign.entries.map { it.name }),
        ModifierField("horizontal", "horizontal", ModifierFieldType.CHOICE, modifier.align.horizontalAlign.name,
                      IconHorizontalAlign.entries.map { it.name })))
      is ColorFilterModifier -> when (val filter = modifier.colorFilter) {
        is TintColorFilter -> EditableModifier(id, EditableModifierKind.COLOR_FILTER, title, listOf(
          ModifierField("color", "tint color", ModifierFieldType.COLOR, colorText(filter.color)),
          ModifierField("blendMode", "blend mode", ModifierFieldType.CHOICE, filter.blendMode.name, BlendMode.entries.map { it.name })))
        else -> readOnly(id, title, "color filter ${simpleClassName(filter)}: ${safeToString(filter)}")
      }
      is StrokeModifier -> EditableModifier(id, EditableModifierKind.STROKE, title,
                                            listOf(ModifierField("color", "stroke color", ModifierFieldType.COLOR, colorText(modifier.color))))
      is CutoutMarginModifier -> EditableModifier(id, EditableModifierKind.CUTOUT_MARGIN, title, listOf(unitField("size", modifier.size)))
      is SizeVariantModifier -> EditableModifier(id, EditableModifierKind.SIZE_VARIANTS, title, listOf(
        ModifierField("policy", "policy", ModifierFieldType.CHOICE, modifier.policy.name, SizeVariantPolicy.entries.map { it.name })))
      else -> readOnly(id, title, safeToString(modifier))
    }
  }

  private fun readOnly(id: String, title: String, description: String) =
    EditableModifier(id, EditableModifierKind.READ_ONLY, title,
                     listOf(ModifierField("description", "description", ModifierFieldType.TEXT, description, editable = false)))

  private fun scaleFields(scale: com.intellij.platform.icons.scale.IconScale): List<ModifierField> {
    val modes = listOf(MODE_FACTOR, MODE_FIT, MODE_FILL)
    return when (scale) {
      is FactorScale -> listOf(ModifierField("mode", "mode", ModifierFieldType.CHOICE, MODE_FACTOR, modes),
                               ModifierField("factor", "factor", ModifierFieldType.NUMBER, number(scale.factor)))
      is FitAreaScale -> areaFields(MODE_FIT, modes, scale.width, scale.height, scale.relative)
      is FillAreaScale -> areaFields(MODE_FILL, modes, scale.width, scale.height, scale.relative)
      else -> listOf(ModifierField("description", "scale", ModifierFieldType.TEXT, safeToString(scale), editable = false))
    }
  }

  private fun areaFields(mode: String, modes: List<String>, width: IconUnit, height: IconUnit, relative: Boolean) = listOf(
    ModifierField("mode", "mode", ModifierFieldType.CHOICE, mode, modes),
    unitField("width", width), unitField("height", height),
    ModifierField("relative", "relative", ModifierFieldType.CHOICE, relative.toString(), listOf("true", "false")))

  private fun unitField(key: String, unit: IconUnit) = ModifierField(key, key, ModifierFieldType.UNIT, unitText(unit))

  fun unitText(unit: IconUnit): String = when (unit) {
    is DisplayPoint -> "${number(unit.value)}dp"
    is Pixel -> "${unit.value}px"
    else -> safeToString(unit)
  }

  /** "#AARRGGBB". */
  fun colorText(color: Color): String {
    val c = color as? SRGBColor ?: return color.toHex()
    fun hex(value: Float) = (value * 255).roundToInt().coerceIn(0, 255).toString(16).padStart(2, '0').uppercase()
    return "#${hex(c.alpha)}${hex(c.red)}${hex(c.green)}${hex(c.blue)}"
  }

  // ---- parse ----

  fun parseUnit(text: String): Result<IconUnit> {
    val match = Regex("""^\s*(-?\d+(?:\.\d+)?)\s*(dp|px)\s*$""").matchEntire(text)
                ?: return Result.failure(IllegalArgumentException("Invalid unit \"$text\": $UNIT_HINT"))
    val (value, suffix) = match.destructured
    return if (suffix == "px") {
      value.toIntOrNull()?.let { Result.success<IconUnit>(it.px) }
      ?: Result.failure(IllegalArgumentException("Pixels must be whole numbers: \"$text\""))
    }
    else Result.success(value.toDouble().dp)
  }

  /** "#AARRGGBB" or "#RRGGBB". */
  fun parseColor(text: String): Result<Color> {
    val hex = text.trim().removePrefix("#")
    if (!Regex("^[0-9a-fA-F]{6}([0-9a-fA-F]{2})?$").matches(hex)) {
      return Result.failure(IllegalArgumentException("Invalid color \"$text\": use #AARRGGBB or #RRGGBB"))
    }
    val argb = if (hex.length == 6) "FF$hex" else hex
    fun channel(offset: Int) = argb.substring(offset, offset + 2).toInt(16) / 255f
    return Result.success(sRGB(red = channel(2), green = channel(4), blue = channel(6), alpha = channel(0)))
  }

  private fun parseNumber(text: String, what: String): Result<Double> =
    text.trim().toDoubleOrNull()?.let { Result.success(it) } ?: Result.failure(IllegalArgumentException("Invalid $what \"$text\""))

  // ---- edit ----

  /**
   * Applies [edit] to the layer at [address] of [descriptor] and returns the edited copy, or a failure whose message explains
   * the problem (invalid value, unknown modifier, read-only modifier).
   */
  fun apply(descriptor: IconDescriptor, address: LayerAddress, edit: ModifierEdit): Result<IconDescriptor> = runCatching {
    val layer = layerAt(descriptor, address) ?: error("The layer of this node cannot be edited")
    val modifiers = InspectorTreeBuilder.flattenModifiers(layer.modifier).toMutableList()
    fun indexOf(id: String): Int = id.removePrefix("m").toIntOrNull()?.takeIf { it in modifiers.indices }
                                   ?: error("Unknown modifier $id")
    when (edit) {
      is ModifierEdit.SetField -> {
        val index = indexOf(edit.modifierId)
        modifiers[index] = setField(modifiers[index], edit.key, edit.value).getOrThrow()
      }
      is ModifierEdit.Add -> modifiers.add(create(edit.kind).getOrThrow())
      is ModifierEdit.Remove -> modifiers.removeAt(indexOf(edit.modifierId))
      is ModifierEdit.Move -> {
        val moved = modifiers.removeAt(indexOf(edit.modifierId))
        modifiers.add(edit.toIndex.coerceIn(0, modifiers.size), moved)
      }
    }
    val chain = modifiers.fold(IconModifier as IconModifier) { acc, modifier -> acc then modifier }
    replaceLayer(descriptor, address) { withModifier(it, chain) }
  }

  /** A new modifier of [kind] with neutral defaults, created with the public modifier functions. */
  fun create(kind: EditableModifierKind): Result<IconModifier> = runCatching {
    when (kind) {
      EditableModifierKind.SCALE -> IconModifier.scale(factor(1.0))
      EditableModifierKind.MARGIN -> IconModifier.margin(0.dp, 0.dp, 0.dp, 0.dp)
      EditableModifierKind.ALPHA -> IconModifier.alpha(1f)
      EditableModifierKind.ALIGN -> IconModifier.align(iconAlign(IconVerticalAlign.Center, IconHorizontalAlign.Center))
      EditableModifierKind.COLOR_FILTER -> IconModifier.colorFilter(ColorFilter.tint(Color.Black))
      EditableModifierKind.STROKE -> IconModifier.stroke(Color.Black)
      EditableModifierKind.CUTOUT_MARGIN -> IconModifier.cutoutMargin(1.dp)
      EditableModifierKind.SIZE_VARIANTS -> IconModifier.sizeVariants(SizeVariantPolicy.Exact)
      EditableModifierKind.READ_ONLY -> error("Read-only modifiers cannot be added")
    }.let(::single)
  }

  /** The single impl modifier of a chain made by one public modifier function (`IconModifier.x(...)`). */
  private fun single(chain: IconModifier): IconModifier = InspectorTreeBuilder.flattenModifiers(chain).single()

  /** [modifier] with field [key] set to [value] (textual form), rebuilt with the public modifier functions. */
  fun setField(modifier: IconModifier, key: String, value: String): Result<IconModifier> = runCatching {
    fun unknown(): Nothing = error("Unknown field \"$key\" of ${simpleClassName(modifier)}")
    val result: IconModifier = when (modifier) {
      is ScaleModifier -> IconModifier.scale(scaleWith(modifier.scale, key, value))
      is MarginIconModifier -> {
        val unit = parseUnit(value).getOrThrow()
        when (key) {
          "left" -> IconModifier.margin(unit, modifier.top, modifier.right, modifier.bottom)
          "top" -> IconModifier.margin(modifier.left, unit, modifier.right, modifier.bottom)
          "right" -> IconModifier.margin(modifier.left, modifier.top, unit, modifier.bottom)
          "bottom" -> IconModifier.margin(modifier.left, modifier.top, modifier.right, unit)
          else -> unknown()
        }
      }
      is AlphaIconModifier -> {
        if (key != "alpha") unknown()
        val alpha = parseNumber(value, "alpha").getOrThrow()
        require(alpha in 0.0..1.0) { "Alpha must be between 0 and 1: $value" }
        IconModifier.alpha(alpha.toFloat())
      }
      is AlignIconModifier -> when (key) {
        "vertical" -> IconModifier.align(iconAlign(enumValue<IconVerticalAlign>(value), modifier.align.horizontalAlign))
        "horizontal" -> IconModifier.align(iconAlign(modifier.align.verticalAlign, enumValue<IconHorizontalAlign>(value)))
        else -> unknown()
      }
      is ColorFilterModifier -> {
        val tint = modifier.colorFilter as? TintColorFilter ?: error("This color filter is read-only")
        when (key) {
          "color" -> IconModifier.colorFilter(ColorFilter.tint(parseColor(value).getOrThrow(), tint.blendMode))
          "blendMode" -> IconModifier.colorFilter(ColorFilter.tint(tint.color, enumValue<BlendMode>(value)))
          else -> unknown()
        }
      }
      is StrokeModifier -> if (key == "color") IconModifier.stroke(parseColor(value).getOrThrow()) else unknown()
      is CutoutMarginModifier -> if (key == "size") IconModifier.cutoutMargin(parseUnit(value).getOrThrow()) else unknown()
      is SizeVariantModifier -> if (key == "policy") IconModifier.sizeVariants(enumValue<SizeVariantPolicy>(value)) else unknown()
      else -> error("${simpleClassName(modifier)} is read-only")
    }
    single(result)
  }

  private fun scaleWith(scale: com.intellij.platform.icons.scale.IconScale, key: String, value: String):
    com.intellij.platform.icons.scale.IconScale {
    val width = (scale as? FitAreaScale)?.width ?: (scale as? FillAreaScale)?.width ?: 16.dp
    val height = (scale as? FitAreaScale)?.height ?: (scale as? FillAreaScale)?.height ?: 16.dp
    val relative = (scale as? FitAreaScale)?.relative ?: (scale as? FillAreaScale)?.relative ?: true
    val mode = when (scale) {
      is FitAreaScale -> MODE_FIT
      is FillAreaScale -> MODE_FILL
      else -> MODE_FACTOR
    }
    fun area(mode: String, w: IconUnit, h: IconUnit, r: Boolean) = when (mode) {
      MODE_FIT -> fitArea(w, h, r)
      MODE_FILL -> fillArea(w, h, r)
      else -> factor(1.0)
    }
    return when (key) {
      "mode" -> when (value) {
        MODE_FACTOR -> (scale as? FactorScale) ?: factor(1.0)
        MODE_FIT, MODE_FILL -> area(value, width, height, relative)
        else -> error("Invalid scale mode \"$value\"")
      }
      "factor" -> {
        val factor = parseNumber(value, "scale factor").getOrThrow()
        require(factor > 0) { "Scale factor must be > 0: $value" }
        factor(factor)
      }
      "width" -> area(mode.takeIf { it != MODE_FACTOR } ?: MODE_FIT, parseUnit(value).getOrThrow(), height, relative)
      "height" -> area(mode.takeIf { it != MODE_FACTOR } ?: MODE_FIT, width, parseUnit(value).getOrThrow(), relative)
      "relative" -> area(mode.takeIf { it != MODE_FACTOR } ?: MODE_FIT, width, height,
                         value.toBooleanStrictOrNull() ?: error("relative must be true or false"))
      else -> error("Unknown field \"$key\" of ScaleModifier")
    }
  }

  private inline fun <reified E : Enum<E>> enumValue(value: String): E =
    enumValues<E>().firstOrNull { it.name == value } ?: error("Invalid value \"$value\": one of ${enumValues<E>().joinToString { it.name }}")

  // ---- descriptor tree ----

  fun layerAt(descriptor: IconDescriptor, address: LayerAddress): IconLayer? {
    var current: Any = descriptor
    for (step in address) {
      current = when (step) {
        is LayerStep.Layer -> (current as? DefaultLayeredIconDescriptor)?.layers?.getOrNull(step.index) ?: return null
        LayerStep.Nested -> (current as? NestedIconLayer)?.iconDescriptor ?: return null
        is LayerStep.LayoutChild -> (current as? LayoutIconLayer)?.nestedLayers?.getOrNull(step.index) ?: return null
        is LayerStep.Frame -> (current as? AnimatedIconLayer)?.frames?.getOrNull(step.frame)?.layers?.getOrNull(step.index) ?: return null
      }
    }
    return current as? IconLayer
  }

  /** [descriptor] with the layer at [address] replaced by [transform]; only the path from the root to it is rebuilt. */
  fun replaceLayer(descriptor: IconDescriptor, address: LayerAddress, transform: (IconLayer) -> IconLayer): IconDescriptor =
    replaceInDescriptor(descriptor, address, transform)

  private fun replaceInDescriptor(current: IconDescriptor, steps: List<LayerStep>, transform: (IconLayer) -> IconLayer): IconDescriptor {
    val step = steps.firstOrNull() as? LayerStep.Layer ?: error("Address does not start at a layer: $steps")
    val layered = current as? DefaultLayeredIconDescriptor ?: error("Not a layered descriptor: ${simpleClassName(current)}")
    val layers = layered.layers.toMutableList()
    layers[step.index] = replaceInLayer(layers[step.index], steps.drop(1), transform)
    return DefaultLayeredIconDescriptor(layers)
  }

  private fun replaceInLayer(layer: IconLayer, steps: List<LayerStep>, transform: (IconLayer) -> IconLayer): IconLayer {
    if (steps.isEmpty()) return transform(layer)
    return when (val step = steps.first()) {
      LayerStep.Nested -> {
        val nested = layer as NestedIconLayer
        NestedIconLayer(replaceInDescriptor(nested.iconDescriptor, steps.drop(1), transform), nested.modifier)
      }
      is LayerStep.LayoutChild -> {
        val layout = layer as LayoutIconLayer
        val children = layout.nestedLayers.toMutableList()
        children[step.index] = replaceInLayer(children[step.index], steps.drop(1), transform)
        LayoutIconLayer(children, layout.direction, layout.modifier)
      }
      is LayerStep.Frame -> {
        val animated = layer as AnimatedIconLayer
        val frames = animated.frames.toMutableList()
        val frame = frames[step.frame]
        val frameLayers = frame.layers.toMutableList()
        frameLayers[step.index] = replaceInLayer(frameLayers[step.index], steps.drop(1), transform)
        frames[step.frame] = IconAnimationFrame(frameLayers, frame.duration, frame.fadeIn, frame.fadeOut)
        AnimatedIconLayer(frames, animated.modifier)
      }
      is LayerStep.Layer -> error("Unexpected step $step")
    }
  }

  /** A copy of [layer] with [modifier]. */
  fun withModifier(layer: IconLayer, modifier: IconModifier): IconLayer = when (layer) {
    is ImageIconLayer -> ImageIconLayer(layer.loader, modifier)
    is NestedIconLayer -> NestedIconLayer(layer.iconDescriptor, modifier)
    is LayoutIconLayer -> LayoutIconLayer(layer.nestedLayers, layer.direction, modifier)
    is AnimatedIconLayer -> AnimatedIconLayer(layer.frames, modifier)
    is ShapeIconLayer -> ShapeIconLayer(layer.color, layer.shape, modifier)
    is SpacerIconLayer -> SpacerIconLayer(modifier)
    is SwingIconLayer -> SwingIconLayer(layer.legacyIcon, modifier)
    else -> error("${simpleClassName(layer)} cannot be edited")
  }

  private const val MODE_FACTOR = "factor"
  private const val MODE_FIT = "fitArea"
  private const val MODE_FILL = "fillArea"
}
