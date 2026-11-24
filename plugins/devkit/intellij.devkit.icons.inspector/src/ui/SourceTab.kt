// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.IconPipeline
import com.intellij.devkit.icons.inspector.model.ResolvedSvgSource
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Checkbox
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.Link
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer

private fun newColor(dark: Boolean) = if (dark) Color(0xFF5C9DFF) else Color(0xFF2D6FE0)
private fun legacyOverlayColor(dark: Boolean) = if (dark) Color(0xFFFF8A4C) else Color(0xFFE8590C)
private fun greenOk(dark: Boolean) = if (dark) Color(0xFF63C07A) else Color(0xFF2B8A4B)

@Composable
internal fun SourceTab(sources: List<ResolvedSvgSource>) {
  if (sources.isEmpty()) {
    Text(
      DevkitIconsInspectorBundle.message("ui.source.empty"),
      modifier = Modifier.padding(12.dp),
      color = JewelTheme.globalColors.text.info,
    )
    return
  }
  val dark = JewelTheme.isDark
  val newSource = sources.firstOrNull { it.pipeline == IconPipeline.NEW_ICONS_API }
  val legacy = sources.firstOrNull { it.pipeline == IconPipeline.LEGACY }
  var overlay by remember { mutableStateOf(false) }

  VerticallyScrollableContainer(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
      if (newSource != null && legacy != null) {
        Verdict(newSource, legacy)
        val canOverlay = newSource.rendering != null && legacy.rendering != null
        if (canOverlay) {
          Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Checkbox(checked = overlay, onCheckedChange = { overlay = it })
            Text(DevkitIconsInspectorBundle.message("ui.source.overlay"))
          }
          if (overlay) OverlayView(newSource.rendering!!, legacy.rendering!!, dark)
        }
      }
      BoxWithConstraints(Modifier.fillMaxWidth()) {
        if (maxWidth < 520.dp || sources.size < 2) {
          Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            sources.forEach { SourceColumn(it, Modifier.fillMaxWidth()) }
          }
        }
        else {
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            sources.forEach { SourceColumn(it, Modifier.weight(1f)) }
          }
        }
      }
    }
  }
}

@Composable
private fun Verdict(a: ResolvedSvgSource, b: ResolvedSvgSource) {
  val dark = JewelTheme.isDark
  val same = a.finalPath != null && a.finalPath == b.finalPath
  val color = if (same) greenOk(dark) else amberColor(dark)
  val unknown = DevkitIconsInspectorBundle.message("ui.source.unknown")
  var text = if (same) DevkitIconsInspectorBundle.message("ui.source.verdict.same")
  else DevkitIconsInspectorBundle.message("ui.source.verdict.different", a.finalPath ?: unknown, b.finalPath ?: unknown)
  val ga = a.contentBounds?.substringBefore(" at")
  val gb = b.contentBounds?.substringBefore(" at")
  if (ga != null && gb != null) text += " \u00B7 " + DevkitIconsInspectorBundle.message("ui.source.verdict.glyph", ga, gb)
  Text(
    text,
    color = color,
    modifier = Modifier.fillMaxWidth()
      .background(color.copy(alpha = 0.14f), RoundedCornerShape(4.dp))
      .border(1.dp, color.copy(alpha = 0.6f), RoundedCornerShape(4.dp))
      .padding(horizontal = 8.dp, vertical = 5.dp),
  )
}

@Composable
private fun SourceColumn(source: ResolvedSvgSource, modifier: Modifier) {
  val info = JewelTheme.globalColors.text.info
  val dark = JewelTheme.isDark
  val isNew = source.pipeline == IconPipeline.NEW_ICONS_API
  Column(modifier, verticalArrangement = Arrangement.spacedBy(6.dp)) {
    GroupHeader(
      DevkitIconsInspectorBundle.message(if (isNew) "ui.source.new" else "ui.source.legacy"),
      Modifier.fillMaxWidth(),
    )
    Text(DevkitIconsInspectorBundle.message("ui.source.final.file"), color = info, fontSize = 11.sp)
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      SelectionContainer(Modifier.weight(1f)) {
        Text(
          source.finalPath ?: DevkitIconsInspectorBundle.message("ui.source.unknown"),
          fontFamily = FontFamily.Monospace,
          fontSize = 12.sp,
        )
      }
      source.finalPath?.let { path ->
        OutlinedButton(onClick = { copy(path) }) { Text(DevkitIconsInspectorBundle.message("ui.copy")) }
      }
    }
    Text(source.resolution, color = info, fontSize = 11.sp)

    if (source.steps.isNotEmpty() || source.basePath != null) {
      Text(DevkitIconsInspectorBundle.message("ui.source.steps"), color = info, fontSize = 11.sp)
      SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
          source.basePath?.let { Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
          source.steps.forEach { Text("\u2192 $it", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
          source.finalPath?.let { Text("\u2192 $it", fontFamily = FontFamily.Monospace, fontSize = 11.sp) }
        }
      }
    }
    if (source.patches.isNotEmpty()) {
      Text(DevkitIconsInspectorBundle.message("ui.source.patches"), color = info, fontSize = 11.sp)
      source.patches.forEach { Text("\u2022 $it", fontSize = 12.sp) }
    }

    Text(DevkitIconsInspectorBundle.message("ui.source.rendering"), color = info, fontSize = 11.sp)
    RenderingView(source)

    SvgText(source, dark)
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun RenderingView(source: ResolvedSvgSource) {
  val rendering = source.rendering
  val bitmap = remember(rendering) { rendering?.toBitmapOrNull() }
  if (rendering == null || bitmap == null) {
    Text(DevkitIconsInspectorBundle.message("ui.source.no.rendering"), color = JewelTheme.globalColors.text.info)
    return
  }
  FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
    PixelImage(bitmap, scale = 1)
    Column(Modifier.horizontalScroll(rememberScrollState())) { PixelImage(bitmap, scale = 4) }
  }
  val caption = source.contentBounds
    ?.let { DevkitIconsInspectorBundle.message("ui.source.frame", rendering.width, rendering.height, it) }
    ?: DevkitIconsInspectorBundle.message("ui.source.frame.unknown", rendering.width, rendering.height)
  Text(caption, color = JewelTheme.globalColors.text.info, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
}

@Composable
private fun SvgText(source: ResolvedSvgSource, dark: Boolean) {
  var expanded by remember { mutableStateOf(false) }
  val svg = source.svgText
  if (svg == null) {
    Text(DevkitIconsInspectorBundle.message("ui.source.svg.none"), color = JewelTheme.globalColors.text.info, fontSize = 11.sp)
    return
  }
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
    Link(
      DevkitIconsInspectorBundle.message(if (expanded) "ui.source.svg.hide" else "ui.source.svg.show"),
      onClick = { expanded = !expanded },
    )
    if (expanded) OutlinedButton(onClick = { copy(svg) }) { Text(DevkitIconsInspectorBundle.message("ui.copy")) }
  }
  if (expanded) {
    VerticallyScrollableContainer(
      Modifier.fillMaxWidth().heightIn(max = 240.dp)
        .border(1.dp, JewelTheme.globalColors.text.info.copy(alpha = 0.3f), RoundedCornerShape(3.dp)),
    ) {
      SelectionContainer {
        Text(svg, fontFamily = FontFamily.Monospace, fontSize = 11.sp, modifier = Modifier.padding(6.dp))
      }
    }
  }
}

private fun copy(text: String) {
  CopyPasteManager.getInstance().setContents(StringSelection(text))
}

private fun com.intellij.devkit.icons.inspector.model.PreviewImage.toBitmapOrNull(): ImageBitmap? {
  if (width <= 0 || height <= 0 || argb.size < width * height) return null
  val image = java.awt.image.BufferedImage(width, height, java.awt.image.BufferedImage.TYPE_INT_ARGB)
  image.setRGB(0, 0, width, height, argb, 0, width)
  return image.toComposeImageBitmap()
}

/** Both renderings centered in one frame at 4x: new API tinted blue, legacy tinted orange, 50% alpha each. */
@Composable
private fun OverlayView(
  newImage: com.intellij.devkit.icons.inspector.model.PreviewImage,
  legacyImage: com.intellij.devkit.icons.inspector.model.PreviewImage,
  dark: Boolean,
) {
  val a = remember(newImage) { newImage.toBitmapOrNull() } ?: return
  val b = remember(legacyImage) { legacyImage.toBitmapOrNull() } ?: return
  val density = LocalDensity.current
  val scale = 4
  val frameW = maxOf(a.width, b.width)
  val frameH = maxOf(a.height, b.height)
  val newTint = newColor(dark)
  val legacyTint = legacyOverlayColor(dark)
  val light = if (dark) Color(0xFF3C3F41) else Color(0xFFFFFFFF)
  val darker = if (dark) Color(0xFF2B2D30) else Color(0xFFE6E8EC)
  Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
      Text("\u25A0 " + DevkitIconsInspectorBundle.message("ui.source.overlay.new"), color = newTint, fontSize = 11.sp)
      Text("\u25A0 " + DevkitIconsInspectorBundle.message("ui.source.overlay.legacy"), color = legacyTint, fontSize = 11.sp)
    }
    Column(Modifier.horizontalScroll(rememberScrollState())) {
      Canvas(Modifier.size(with(density) { (frameW * scale).toDp() }, with(density) { (frameH * scale).toDp() })) {
        drawCheckerboard(light, darker, tile = 8f * density.density)
        fun draw(image: ImageBitmap, tint: Color) {
          drawImage(
            image = image,
            srcOffset = IntOffset.Zero,
            srcSize = IntSize(image.width, image.height),
            dstOffset = IntOffset(((frameW - image.width) / 2) * scale, ((frameH - image.height) / 2) * scale),
            dstSize = IntSize(image.width * scale, image.height * scale),
            alpha = 0.5f,
            colorFilter = ColorFilter.tint(tint, BlendMode.SrcIn),
            filterQuality = FilterQuality.None,
          )
        }
        draw(a, newTint)
        draw(b, legacyTint)
      }
    }
  }
}
