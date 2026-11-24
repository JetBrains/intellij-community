// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.model

import com.intellij.devkit.icons.inspector.model.InspectorFormatting.className
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.number
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.safeToString
import com.intellij.devkit.icons.inspector.model.InspectorFormatting.simpleClassName
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.ScalableIcon
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.BadgeIcon
import com.intellij.ui.DeferredIcon
import com.intellij.ui.DeferredIconImpl
import com.intellij.ui.LayeredIcon
import com.intellij.ui.RetrievableIcon
import com.intellij.ui.RowIcon
import com.intellij.ui.SizedIcon
import com.intellij.ui.icons.CachedImageIcon
import com.intellij.ui.icons.CompositeIcon
import com.intellij.ui.icons.FilteredIcon
import com.intellij.ui.icons.HoledIcon
import com.intellij.ui.icons.IconWithOverlay
import com.intellij.ui.icons.IconWithToolTip
import com.intellij.ui.icons.TextIcon
import com.intellij.ui.scale.ScaleContextAware
import com.intellij.util.CropIcon
import com.intellij.util.ColorFilter
import com.intellij.util.ui.ColorIcon
import com.intellij.util.ui.EmptyIcon
import java.awt.Color
import java.awt.Font
import java.awt.Insets
import java.awt.Paint
import java.awt.Rectangle
import java.lang.reflect.Field
import java.lang.reflect.Modifier
import javax.swing.Icon
import javax.swing.ImageIcon

private val LOG = logger<LegacyIcons>()

/** One child of a legacy icon: the icon (may be `null`, e.g. an empty layer), its role in the parent and extra rows. */
internal class LegacyIconChild(val icon: Icon?, val role: String, val properties: List<InspectorProperty> = emptyList())

/**
 * Read-only view of the structure of legacy Swing icons. Uses public API first; private-but-essential state is read by
 * reflection ([Reflect]), shown as "unavailable (private)" when that fails. Never triggers deferred-icon evaluation or image
 * loading beyond what `getIconWidth()` already does. Call on EDT (deferred icons answer from their current state only there).
 *
 * Reflective accesses (all read-only):
 * - `com.intellij.ui.RowIcon.alignment`
 * - `com.intellij.ui.icons.FilteredIcon.baseIcon`, `.filterSupplier`
 * - `com.intellij.util.ui.ColorIcon.myBorderColor`, `.myColorWidth`, `.myColorHeight`, `.myArc`
 * - `com.intellij.ui.TextIcon` (platform-impl): `myText`, `myFont`, `myForeground`, `myBackground`, `myBorderColor`, `myInsets`, `myRound`
 * - `com.intellij.ui.icons.TextIcon` (core-ui): `font` (fallback `fontSize` is public)
 * - unknown wrappers (e.g. `IconUtil`'s private `OldScaledIcon`, `ResizeSquareIcon`, `IconSizeWrapper`, `FlippedIcon`,
 *   `SelectionAwareIcon`, `IndentedIcon`, `CenteredIcon`, `ColorizeProxyIcon`): their declared instance fields — `Icon` fields
 *   become children, simple values become rows ([genericFields])
 */
internal object LegacyIcons {
  private const val GROUP_LEGACY = "Legacy icon"
  private const val GROUP_PLACEMENT = "Placement"
  private const val UNAVAILABLE = "unavailable (private)"

  fun children(icon: Icon): List<LegacyIconChild> = try {
    when (icon) {
      is RowIcon -> rowChildren(icon)
      is IconWithOverlay -> overlayChildren(icon)
      is LayeredIcon -> layeredChildren(icon)
      is CompositeIcon -> (0 until icon.getIconCount()).map { index ->
        LegacyIconChild(icon.getIcon(index), "icon $index", listOf(InspectorProperty("index", index.toString(), GROUP_PLACEMENT)))
      }
      is HoledIcon -> listOf(LegacyIconChild(icon.icon, "base",
                                             listOf(InspectorProperty("placement", "(0, 0), under the hole", GROUP_PLACEMENT))))
      is SizedIcon -> sizedChildren(icon)
      is CropIcon -> listOf(LegacyIconChild(icon.sourceIcon, "cropped source",
                                            listOf(InspectorProperty("crop", rect(icon.crop), GROUP_PLACEMENT),
                                                   InspectorProperty("offset", "(${-icon.crop.x}, ${-icon.crop.y})", GROUP_PLACEMENT))))
      is FilteredIcon -> listOfNotNull(Reflect.get<Icon>(icon, "baseIcon")?.let { LegacyIconChild(it, "filtered source") })
      is AnimatedIcon -> animatedChildren(icon)
      is DeferredIcon -> deferredChildren(icon)
      is RetrievableIcon -> listOf(LegacyIconChild(icon.retrieveIcon(), roleOfWrapper(icon), placementOfWrapper(icon)))
      else -> if (isLeaf(icon)) emptyList() else genericFields(icon).children
    }
  }
  catch (t: Throwable) {
    LOG.debug("Cannot expand ${icon.javaClass.name}", t)
    emptyList()
  }

  private fun rowChildren(icon: RowIcon): List<LegacyIconChild> {
    val alignment = Reflect.get<Any>(icon, "alignment")
    val height = runCatching { icon.iconHeight }.getOrDefault(0)
    var x = 0
    val result = ArrayList<LegacyIconChild>()
    for (index in 0 until icon.getIconCount()) {
      val child = icon.getIcon(index)
      if (child == null) {
        result.add(LegacyIconChild(null, "icon $index (empty)", listOf(InspectorProperty("index", index.toString(), GROUP_PLACEMENT))))
        continue
      }
      val y = when (alignment?.toString()) {
        "TOP" -> 0
        "CENTER" -> (height - child.iconHeight) / 2
        "BOTTOM" -> height - child.iconHeight
        else -> null
      }
      result.add(LegacyIconChild(child, "icon $index @($x, ${y ?: "?"})", listOf(
        InspectorProperty("index", index.toString(), GROUP_PLACEMENT),
        InspectorProperty("x", x.toString(), GROUP_PLACEMENT),
        InspectorProperty("y", y?.toString() ?: "$UNAVAILABLE alignment", GROUP_PLACEMENT),
        InspectorProperty("alignment", alignment?.toString() ?: UNAVAILABLE, GROUP_PLACEMENT),
        InspectorProperty("size", InspectorFormatting.iconSize(child), GROUP_PLACEMENT),
      )))
      x += child.iconWidth
    }
    return result
  }

  private fun layeredChildren(icon: LayeredIcon, roles: (Int) -> String = { "layer $it" }): List<LegacyIconChild> =
    (0 until icon.getIconCount()).map { index ->
      val enabled = runCatching { icon.isLayerEnabled(index) }.getOrDefault(true)
      val hShift = runCatching { icon.getHShift(index) }.getOrNull()
      val vShift = runCatching { icon.getVShift(index) }.getOrNull()
      val child = icon.getIcon(index)
      val rows = listOfNotNull(
        InspectorProperty("layer", index.toString(), GROUP_PLACEMENT),
        InspectorProperty("layer enabled", enabled.toString(), GROUP_PLACEMENT),
        if (hShift != null && vShift != null) InspectorProperty("shift", "($hShift, $vShift)", GROUP_PLACEMENT) else null,
        child?.let { InspectorProperty("size", InspectorFormatting.iconSize(it), GROUP_PLACEMENT) },
      )
      val role = roles(index) + (if (hShift != null && vShift != null && (hShift != 0 || vShift != 0)) " +($hShift,$vShift)" else "") +
                 (if (enabled) "" else " disabled")
      LegacyIconChild(child, role, rows)
    }

  private fun overlayChildren(icon: IconWithOverlay): List<LegacyIconChild> {
    val layers = layeredChildren(icon) { index -> if (index == 0) "main" else if (index == 1) "overlay" else "layer $index" }
    val shape = runCatching { icon.getOverlayShape(0, 0)?.bounds }.getOrNull()
    if (shape == null || layers.size < 2) return layers
    val overlay = layers[1]
    return layers.toMutableList().also {
      it[1] = LegacyIconChild(overlay.icon, overlay.role,
                              overlay.properties + InspectorProperty("overlay cut-out shape", rect(shape), GROUP_PLACEMENT))
    }
  }

  private fun sizedChildren(icon: SizedIcon): List<LegacyIconChild> {
    val delegate = icon.retrieveIcon()
    val dx = icon.iconWidth - delegate.iconWidth
    val dy = icon.iconHeight - delegate.iconHeight
    val offset = if (dx > 0 || dy > 0) "(${maxOf(dx, 0) / 2}, ${maxOf(dy, 0) / 2}), centered" else "(0, 0)"
    return listOf(LegacyIconChild(delegate, "delegate", listOf(
      InspectorProperty("offset", offset, GROUP_PLACEMENT),
      InspectorProperty("size", InspectorFormatting.iconSize(delegate), GROUP_PLACEMENT),
    )))
  }

  private fun animatedChildren(icon: AnimatedIcon): List<LegacyIconChild> {
    @Suppress("UnstableApiUsage")
    val frames = runCatching { icon.frames }.getOrNull() ?: return emptyList()
    return frames.mapIndexed { index, frame ->
      LegacyIconChild(frame.icon, "frame $index", listOf(
        InspectorProperty("frame", index.toString(), GROUP_PLACEMENT),
        InspectorProperty("delay", "${frame.delay} ms", GROUP_PLACEMENT),
      ))
    }
  }

  private fun deferredChildren(icon: DeferredIcon): List<LegacyIconChild> {
    val result = ArrayList<LegacyIconChild>()
    // currently painted icon: DeferredIconImpl exposes it without evaluating; never call evaluate()/retrieveIcon() off EDT
    val current: Icon? = when {
      icon is DeferredIconImpl<*> -> icon.currentlyPaintedIcon()
      icon is RetrievableIcon && ApplicationManager.getApplication()?.isDispatchThread == true -> icon.retrieveIcon()
      else -> null
    }
    val base = icon.baseIcon
    if (current != null) result.add(LegacyIconChild(current, if (isDone(icon) == true) "evaluated" else "currently painted"))
    if (current == null || current !== base) result.add(LegacyIconChild(base, "base"))
    return result
  }

  private fun isDone(icon: DeferredIcon): Boolean? = runCatching { icon.isDone }.getOrNull()

  /** Role of the single child of a wrapper (`RetrievableIcon`), named after the known IconUtil / util wrappers. */
  private fun roleOfWrapper(icon: Icon): String = when (icon.javaClass.simpleName) {
    "OldScaledIcon" -> "scaled source"
    "ResizeSquareIcon" -> "resized source"
    "IconSizeWrapper" -> "sized source"
    "FlippedIcon" -> "flipped source"
    "SelectionAwareIcon" -> "icon under selection"
    "IndentedIcon" -> "indented source"
    "CenteredIcon" -> "centered source"
    else -> if (icon is IconWithToolTip) "wrapped" else "retrieved"
  }

  /** Simple fields of a wrapper as placement rows (e.g. scale, size, insets, flip direction). */
  private fun placementOfWrapper(icon: Icon): List<InspectorProperty> =
    if (icon.javaClass.name.startsWith("com.intellij.")) genericFields(icon).properties.map { it.copy(group = GROUP_PLACEMENT) }
    else emptyList()

  private fun isLeaf(icon: Icon): Boolean =
    icon is CachedImageIcon || icon is EmptyIcon || icon is ImageIcon || icon is TextIcon || icon is com.intellij.ui.TextIcon

  /** Type-specific rows (shown in group "Legacy icon"). */
  fun properties(icon: Icon): List<InspectorProperty> {
    val rows = ArrayList<InspectorProperty>()
    try {
      when (icon) {
        is CachedImageIcon -> {
          rows.add(InspectorProperty("original path", icon.originalPath, GROUP_LEGACY))
          rows.add(InspectorProperty("expUI path", icon.expUIPath, GROUP_LEGACY))
          rows.add(InspectorProperty("obj scale", icon.getObjScale()?.let(::number) ?: "none", GROUP_LEGACY))
          val coords = runCatching { SvgSources.effectiveCoords(icon) }.getOrNull()
          if (coords != null) {
            rows.add(InspectorProperty("resource path", coords.first, GROUP_LEGACY))
            rows.add(InspectorProperty("class loader", safeToString(coords.second), GROUP_LEGACY))
          }
          icon.localFilterSupplier?.let { rows.add(filterRow(it.getFilter())) }
          rows.addAll(ThemeInspection.legacyRows(icon))
        }
        is ColorIcon -> {
          rows.add(InspectorProperty("color", awtColor(icon.iconColor), GROUP_LEGACY))
          rows.add(InspectorProperty("border color", Reflect.text(icon, "myBorderColor"), GROUP_LEGACY))
          val colorSize = "${Reflect.text(icon, "myColorWidth")}×${Reflect.text(icon, "myColorHeight")}"
          rows.add(InspectorProperty("color size", colorSize, GROUP_LEGACY))
          rows.add(InspectorProperty("arc", Reflect.text(icon, "myArc"), GROUP_LEGACY))
        }
        is EmptyIcon -> rows.add(InspectorProperty("empty", InspectorFormatting.iconSize(icon), GROUP_LEGACY))
        is DeferredIcon -> {
          rows.add(InspectorProperty("done", isDone(icon)?.toString() ?: "unknown", GROUP_LEGACY))
          rows.add(InspectorProperty("base icon", className(icon.baseIcon), GROUP_LEGACY))
        }
        is RowIcon -> {
          rows.add(InspectorProperty("icons", icon.getIconCount().toString(), GROUP_LEGACY))
          rows.add(InspectorProperty("alignment", Reflect.text(icon, "alignment"), GROUP_LEGACY))
        }
        is IconWithOverlay -> {
          rows.add(InspectorProperty("main icon", className(icon.mainIcon), GROUP_LEGACY))
          rows.add(InspectorProperty("overlay icon", className(icon.overlayIcon), GROUP_LEGACY))
        }
        is LayeredIcon -> rows.add(InspectorProperty("layers", icon.getIconCount().toString(), GROUP_LEGACY))
        is CompositeIcon -> rows.add(InspectorProperty("icons", icon.getIconCount().toString(), GROUP_LEGACY))
        is BadgeIcon -> {
          rows.add(InspectorProperty("badge paint", paint(icon.paint), GROUP_LEGACY))
          rows.add(InspectorProperty("badge shape provider", className(icon.provider), GROUP_LEGACY))
          rows.add(InspectorProperty("badge border", runCatching { number(icon.provider.border) }.getOrElse { "?" }, GROUP_LEGACY))
          val base = icon.icon
          val badge = runCatching { icon.provider.createShape(base.iconWidth, base.iconHeight, false)?.bounds }.getOrNull()
          rows.add(InspectorProperty("badge bounds", badge?.let(::rect) ?: "none", GROUP_LEGACY))
        }
        is HoledIcon -> rows.add(InspectorProperty("base icon", className(icon.icon), GROUP_LEGACY))
        is SizedIcon -> rows.add(InspectorProperty("sized to", "${icon.sizedWidth}×${icon.sizedHeight}", GROUP_LEGACY))
        is CropIcon -> rows.add(InspectorProperty("crop", rect(icon.crop), GROUP_LEGACY))
        is FilteredIcon -> {
          val supplier = Reflect.get<Any>(icon, "filterSupplier")
          val filter = (supplier as? com.intellij.ui.icons.RgbImageFilterSupplier)?.let { runCatching { it.getFilter() }.getOrNull() }
          rows.add(if (filter != null) filterRow(filter)
                   else InspectorProperty("filter", supplier?.let(::className) ?: UNAVAILABLE, GROUP_LEGACY))
        }
        is TextIcon -> {
          rows.add(InspectorProperty("text", icon.text, GROUP_LEGACY))
          rows.add(InspectorProperty("font size", number(icon.fontSize), GROUP_LEGACY))
          rows.add(InspectorProperty("highlight", icon.highlight.toString(), GROUP_LEGACY))
          rows.add(InspectorProperty("font", Reflect.text(icon, "font"), GROUP_LEGACY))
        }
        is com.intellij.ui.TextIcon -> {
          for ((label, field) in listOf("text" to "myText", "font" to "myFont", "foreground" to "myForeground",
                                        "background" to "myBackground", "border color" to "myBorderColor", "insets" to "myInsets",
                                        "round" to "myRound")) {
            rows.add(InspectorProperty(label, Reflect.text(icon, field), GROUP_LEGACY))
          }
        }
        is AnimatedIcon -> {
          @Suppress("UnstableApiUsage")
          val frames = runCatching { icon.frames }.getOrNull()
          rows.add(InspectorProperty("frames", frames?.size?.toString() ?: UNAVAILABLE, GROUP_LEGACY))
          frames?.let { rows.add(InspectorProperty("total delay", "${it.sumOf { frame -> frame.delay }} ms", GROUP_LEGACY)) }
        }
        is ImageIcon -> {
          val image = icon.image
          rows.add(InspectorProperty("image", "${className(image)} ${image?.getWidth(null)}×${image?.getHeight(null)}", GROUP_LEGACY))
          rows.add(InspectorProperty("description", icon.description, GROUP_LEGACY))
        }
      }
      if (icon is IconWithToolTip) {
        rows.add(InspectorProperty("tooltip", runCatching { icon.getToolTip(false) }.getOrElse { "?" } ?: "none", GROUP_LEGACY))
      }
      if (icon is RetrievableIcon && icon.javaClass.name.startsWith("com.intellij.") && !knownWrapper(icon)) {
        genericFields(icon).properties.forEach(rows::add)
      }
      if (icon is ScalableIcon) rows.add(InspectorProperty("scale", runCatching { number(icon.scale) }.getOrElse { "?" }, GROUP_LEGACY))
      if (icon is ScaleContextAware) {
        rows.add(InspectorProperty("scale context", runCatching { safeToString(icon.scaleContext) }.getOrElse { "?" }, GROUP_LEGACY))
      }
    }
    catch (t: Throwable) {
      rows.add(InspectorProperty("error", t.toString(), GROUP_LEGACY))
    }
    return rows
  }

  private fun knownWrapper(icon: Icon): Boolean = icon is SizedIcon || icon is DeferredIcon || icon is LayeredIcon || icon is RowIcon

  private fun filterRow(filter: Any): InspectorProperty = when (filter) {
    is ColorFilter -> InspectorProperty("filter", "colorize ${awtColor(filter.color)} (keepGray=${filter.keepGray}, " +
                                                  "keepBrightness=${filter.keepBrightness})", GROUP_LEGACY)
    else -> InspectorProperty("filter", className(filter), GROUP_LEGACY)
  }

  /** Short type-specific subtitle, e.g. the image path or "3 layers". */
  fun subtitle(icon: Icon): String? = try {
    when (icon) {
      is CachedImageIcon -> icon.originalPath ?: icon.expUIPath
      is RowIcon -> "${icon.getIconCount()} icons, ${Reflect.get<Any>(icon, "alignment") ?: "?"}"
      is IconWithOverlay -> "main + overlay"
      is LayeredIcon -> "${icon.getIconCount()} layers"
      is CompositeIcon -> "${icon.getIconCount()} icons"
      is BadgeIcon -> "badge ${paint(icon.paint)}"
      is SizedIcon -> "sized ${icon.sizedWidth}×${icon.sizedHeight}"
      is CropIcon -> "crop ${rect(icon.crop)}"
      is FilteredIcon -> "filtered"
      is ColorIcon -> awtColor(icon.iconColor)
      is TextIcon -> "\"${icon.text}\""
      is com.intellij.ui.TextIcon -> Reflect.get<String>(icon, "myText")?.let { "\"$it\"" }
      is AnimatedIcon -> runCatching { "${icon.frames.size} frames" }.getOrNull()
      is DeferredIcon -> if (isDone(icon) == true) "done" else "pending"
      is EmptyIcon -> "empty"
      is ImageIcon -> "image"
      else -> null
    }
  }
  catch (_: Throwable) {
    null
  }

  // ---- generic reflection over unknown wrappers ----

  internal class Reflected(val children: List<LegacyIconChild>, val properties: List<InspectorProperty>)

  /**
   * Declared instance fields of [icon]'s class hierarchy (up to, not including, `java.*`): Icon fields → children, simple values
   * → rows.
   */
  fun genericFields(icon: Icon): Reflected {
    val children = ArrayList<LegacyIconChild>()
    val rows = ArrayList<InspectorProperty>()
    var clazz: Class<*>? = icon.javaClass
    while (clazz != null && !clazz.name.startsWith("java.") && !clazz.name.startsWith("javax.")) {
      for (field in clazz.declaredFields) {
        if (Modifier.isStatic(field.modifiers) || field.isSynthetic) continue
        val value = Reflect.read(field, icon) ?: continue
        when {
          value is Icon && value !== icon -> children.add(LegacyIconChild(value, field.name, listOf(
            InspectorProperty("field", "${clazz.simpleName}.${field.name} (private)", GROUP_PLACEMENT))))
          isSimple(value) -> rows.add(InspectorProperty("${field.name} (private)", simpleText(value), GROUP_LEGACY))
        }
      }
      clazz = clazz.superclass
    }
    return Reflected(children, rows)
  }

  private fun isSimple(value: Any): Boolean =
    value is Number || value is Boolean || value is String || value is Char || value is Enum<*> || value is Color || value is Rectangle ||
    value is Insets || value is Font

  private fun simpleText(value: Any): String = when (value) {
    is Color -> awtColor(value)
    is Rectangle -> rect(value)
    is Insets -> "t=${value.top} l=${value.left} b=${value.bottom} r=${value.right}"
    is Font -> "${value.family} ${value.size}"
    else -> safeToString(value)
  }

  private fun paint(paint: Paint): String = if (paint is Color) awtColor(paint) else simpleClassName(paint)

  fun awtColor(color: Color?): String =
    if (color == null) "none"
    else "#%02X%02X%02X%02X".format(color.red, color.green, color.blue, color.alpha) + " (${simpleClassName(color)})"

  private fun rect(r: Rectangle): String = "(${r.x}, ${r.y}) ${r.width}×${r.height}"

  /** Read-only reflective field access for this internal tool; failures are `null` / "unavailable (private)". */
  internal object Reflect {
    fun field(owner: Any, name: String): Field? {
      var clazz: Class<*>? = owner.javaClass
      while (clazz != null) {
        val field = runCatching { clazz.getDeclaredField(name) }.getOrNull()
        if (field != null) return field
        clazz = clazz.superclass
      }
      return null
    }

    fun read(field: Field, owner: Any): Any? = runCatching {
      field.isAccessible = true
      field.get(owner)
    }.getOrNull()

    inline fun <reified T> get(owner: Any, name: String): T? = field(owner, name)?.let { read(it, owner) } as? T

    fun text(owner: Any, name: String): String {
      val field = field(owner, name) ?: return UNAVAILABLE
      val value = runCatching {
        field.isAccessible = true
        field.get(owner)
      }.getOrElse { return UNAVAILABLE }
      return if (value == null) "none" else simpleText(value)
    }
  }
}
