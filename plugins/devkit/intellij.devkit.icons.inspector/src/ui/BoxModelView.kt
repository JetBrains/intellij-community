// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.InspectorNode
import com.intellij.devkit.icons.inspector.model.LayerBounds
import com.intellij.devkit.icons.inspector.model.PreviewImage
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Text
import kotlin.math.floor
import kotlin.math.roundToInt

/** Upper bound of the magnification, in screen pixels per device pixel of the icon. */
private const val MAX_ZOOM = 24

private val SELECTION_COLOR = Color(0xFF3C8CE7)
private val SPACING_COLOR = Color(0xFF7FB35A)
private val OUTER_COLOR = Color(0xFFB8865B)
private val CUTOUT_COLOR = Color(0xFFE5483F)

/**
 * The painted icon, magnified, with guides for the selected node: the area it painted, the space between it and the
 * box around it (margins and alignment, from where both painted) with its size on every side, and the outlines of the
 * boxes further out. Like the box model of browser developer tools, but over the actual image. In device pixels.
 */
@Composable
internal fun BoxModelView(
  root: InspectorNode,
  selectedId: String?,
  previews: List<PreviewImage>,
  previewError: String?,
  modifier: Modifier = Modifier,
) {
  val model = remember(root, selectedId) { boxModel(root, selectedId) }
  val levels = model.levels
  val icon = levels.firstOrNull()
  if (icon == null) {
    Text(DevkitIconsInspectorBundle.message("ui.box.model.empty"), modifier = modifier.padding(12.dp), color = JewelTheme.globalColors.text.info)
    return
  }
  // the previews are rendered without a component: take the one of the painted size, or stretch the largest one
  val preview = remember(previews, icon) {
    previews.firstOrNull { it.width == icon.width && it.height == icon.height } ?: previews.maxByOrNull { it.width * it.height }
  }
  val bitmap = remember(preview) { preview?.toImageBitmap() }
  val selected = levels.last().takeIf { model.hasSelection }
  Column(modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    if (bitmap == null) PreviewErrorBanner(previewError, Modifier.fillMaxWidth())
    GuidesCanvas(levels, selected, bitmap, Modifier.fillMaxWidth().weight(1f))
    val shown = selected ?: icon
    Text(
      if (shown.cutout > 0) DevkitIconsInspectorBundle.message("ui.box.model.caption.cutout", shown.width, shown.height, shown.cutout)
      else DevkitIconsInspectorBundle.message("ui.box.model.caption", shown.width, shown.height),
      fontSize = 11.sp,
      color = JewelTheme.globalColors.text.info,
    )
  }
}

/** Says why the icon image is missing from the view: the previews could not be rendered. */
@Composable
internal fun PreviewErrorBanner(error: String?, modifier: Modifier = Modifier) {
  val color = JewelTheme.globalColors.text.error
  Text(
    error?.let { DevkitIconsInspectorBundle.message("ui.preview.error", it) } ?: DevkitIconsInspectorBundle.message("ui.preview.none"),
    color = color,
    fontSize = 11.sp,
    modifier = modifier
      .background(color.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
      .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
      .padding(horizontal = 6.dp, vertical = 3.dp),
  )
}

@Composable
private fun GuidesCanvas(levels: List<LayerBounds>, selected: LayerBounds?, bitmap: ImageBitmap?, modifier: Modifier) {
  val textMeasurer = rememberTextMeasurer()
  val density = LocalDensity.current
  val labelColor = JewelTheme.globalColors.text.normal
  val labelBackground = JewelTheme.globalColors.panelBackground
  val checkerLight = if (JewelTheme.isDark) Color(0xFF3C3F41) else Color(0xFFFFFFFF)
  val checkerDark = if (JewelTheme.isDark) Color(0xFF2B2D30) else Color(0xFFE6E8EC)
  val outline = JewelTheme.globalColors.text.info
  Canvas(modifier) {
    val icon = levels.first()
    // whole screen pixels per icon pixel keep the pixels crisp; leave room for the distance labels around the icon
    val margin = 24.dp.toPx()
    val zoom = floor(minOf((size.width - 2 * margin) / icon.width, (size.height - 2 * margin) / icon.height))
      .coerceIn(1f, MAX_ZOOM.toFloat())
    val origin = Offset((size.width - icon.width * zoom) / 2, (size.height - icon.height * zoom) / 2)
    fun rect(b: LayerBounds) = Rect(origin.x + (b.x - icon.x) * zoom, origin.y + (b.y - icon.y) * zoom,
                                    origin.x + (b.right - icon.x) * zoom, origin.y + (b.bottom - icon.y) * zoom)

    val iconRect = rect(icon)
    clipRect(iconRect.left, iconRect.top, iconRect.right, iconRect.bottom) {
      drawCheckerboard(checkerLight, checkerDark, tile = 8f * density.density)
    }
    if (bitmap != null) {
      drawImage(
        image = bitmap,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(bitmap.width, bitmap.height),
        dstOffset = IntOffset(iconRect.left.roundToInt(), iconRect.top.roundToInt()),
        dstSize = IntSize(iconRect.width.roundToInt(), iconRect.height.roundToInt()),
        filterQuality = FilterQuality.None,
      )
    }
    val hairline = 1.dp.toPx()
    val dashes = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 3.dp.toPx()))
    drawRect(outline, iconRect.topLeft, iconRect.size, style = Stroke(hairline))
    if (selected == null) return@Canvas

    val selectedRect = rect(selected)
    val parent = levels.getOrNull(levels.size - 2)
    // the boxes further out, faint
    for (level in levels.drop(1).dropLast(2)) {
      val r = rect(level)
      drawRect(OUTER_COLOR, r.topLeft, r.size, style = Stroke(hairline, pathEffect = dashes))
    }
    // the edges of the selected node, across the whole view
    val guide = SELECTION_COLOR.copy(alpha = 0.5f)
    for (x in listOf(selectedRect.left, selectedRect.right)) {
      drawLine(guide, Offset(x, 0f), Offset(x, size.height), hairline, pathEffect = dashes)
    }
    for (y in listOf(selectedRect.top, selectedRect.bottom)) {
      drawLine(guide, Offset(0f, y), Offset(size.width, y), hairline, pathEffect = dashes)
    }
    if (parent != null) {
      val parentRect = rect(parent)
      val spacing = Path().apply {
        fillType = PathFillType.EvenOdd
        addRect(parentRect)
        addRect(selectedRect)
      }
      drawPath(spacing, SPACING_COLOR.copy(alpha = 0.3f))
      drawRect(SPACING_COLOR, parentRect.topLeft, parentRect.size, style = Stroke(hairline, pathEffect = dashes))
    }
    drawRect(SELECTION_COLOR.copy(alpha = 0.2f), selectedRect.topLeft, selectedRect.size)
    drawRect(SELECTION_COLOR, selectedRect.topLeft, selectedRect.size, style = Stroke(hairline))
    if (selected.cutout > 0) {
      // the ring a shape clears around itself, outside its own area
      val cutoutRect = rect(selected.withCutout())
      drawRect(CUTOUT_COLOR, cutoutRect.topLeft, cutoutRect.size, style = Stroke(hairline, pathEffect = dashes))
    }
    val label = Label(textMeasurer, labelColor, labelBackground)
    if (parent != null) {
      val parentRect = rect(parent)
      label.draw(this, "${selected.y - parent.y}", Offset(selectedRect.center.x, (parentRect.top + selectedRect.top) / 2))
      label.draw(this, "${parent.bottom - selected.bottom}", Offset(selectedRect.center.x, (selectedRect.bottom + parentRect.bottom) / 2))
      label.draw(this, "${selected.x - parent.x}", Offset((parentRect.left + selectedRect.left) / 2, selectedRect.center.y))
      label.draw(this, "${parent.right - selected.right}", Offset((selectedRect.right + parentRect.right) / 2, selectedRect.center.y))
    }
    // the size of the selected node: inside it, or under the view's bottom guide when it is too small for the label
    val sizeText = "${selected.width}\u00D7${selected.height}"
    val sizeSize = label.size(sizeText)
    val fitsInside = sizeSize.width < selectedRect.width && sizeSize.height < selectedRect.height
    val sizeCenter = if (fitsInside) selectedRect.center
    else Offset(selectedRect.center.x, minOf(selectedRect.bottom + sizeSize.height, size.height - sizeSize.height / 2))
    label.draw(this, sizeText, sizeCenter, emphasized = true)
  }
}

/** A value in device pixels, centered at a point, on a chip so that it reads over the image. */
private class Label(private val measurer: TextMeasurer, private val color: Color, private val background: Color) {
  private val style = TextStyle(color = color, fontSize = 10.sp, fontFamily = FontFamily.Monospace)
  private val emphasizedStyle = style.copy(fontWeight = FontWeight.SemiBold)

  fun size(text: String): Size = measurer.measure(text, emphasizedStyle).size.let { Size(it.width.toFloat(), it.height.toFloat()) }

  /** [emphasized] marks the selected node's size, as opposed to the spacing around it. */
  fun draw(scope: DrawScope, text: String, center: Offset, emphasized: Boolean = false) {
    val layout = measurer.measure(text, if (emphasized) emphasizedStyle else style)
    val padding = with(scope) { 2.dp.toPx() }
    val topLeft = Offset(center.x - layout.size.width / 2f, center.y - layout.size.height / 2f)
    scope.drawRoundRect(
      if (emphasized) SELECTION_COLOR.copy(alpha = 0.9f) else background.copy(alpha = 0.85f),
      topLeft - Offset(padding, 0f),
      Size(layout.size.width + 2 * padding, layout.size.height.toFloat()),
      CornerRadius(padding),
    )
    if (emphasized) scope.drawText(layout, color = Color.White, topLeft = topLeft) else scope.drawText(layout, topLeft = topLeft)
  }
}

/** @property levels the boxes from the icon inwards; the last one is the selected node's if [hasSelection] */
private class BoxModel(val levels: List<LayerBounds>, val hasSelection: Boolean)

/**
 * The boxes for the node [selectedId]: the nodes on its path from [root] that painted, ending at the selected node;
 * consecutive nodes that painted the same area share one box. Bounds are only comparable within one paint, so the
 * path stops at the first node above that has none. Without a selection (or when the selected node did not paint),
 * only the first painted node of the tree, the icon.
 */
private fun boxModel(root: InspectorNode, selectedId: String?): BoxModel {
  val path = selectedId?.let { pathTo(root, it) }
  if (path == null || path.last().bounds == null) return BoxModel(listOfNotNull(firstBounds(root)), hasSelection = false)
  val levels = ArrayList<LayerBounds>()
  for (node in path.takeLastWhile { it.bounds != null }) {
    val bounds = node.bounds!!
    if (levels.lastOrNull() != bounds) levels.add(bounds)
  }
  return BoxModel(levels, hasSelection = true)
}

private fun firstBounds(node: InspectorNode): LayerBounds? =
  node.bounds ?: node.children.firstNotNullOfOrNull(::firstBounds)

private fun pathTo(node: InspectorNode, id: String): List<InspectorNode>? {
  if (node.id == id) return listOf(node)
  for (child in node.children) {
    pathTo(child, id)?.let { return listOf(node) + it }
  }
  return null
}
