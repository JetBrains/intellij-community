// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.toComposeImageBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.FlaggedRenderer
import com.intellij.devkit.icons.inspector.model.IconInspectorController
import com.intellij.devkit.icons.inspector.model.InspectionTarget
import com.intellij.devkit.icons.inspector.model.InspectorNode
import com.intellij.devkit.icons.inspector.model.InspectorProperty
import com.intellij.devkit.icons.inspector.model.PreviewImage
import com.intellij.openapi.ide.CopyPasteManager
import java.awt.datatransfer.StringSelection
import java.awt.image.BufferedImage
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.CheckboxRow
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.GroupHeader
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.SimpleTabContent
import org.jetbrains.jewel.ui.component.TabData
import org.jetbrains.jewel.ui.component.TabStrip
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.theme.defaultTabStyle

private enum class DetailTab(val key: String) {
  PROPERTIES("ui.tab.properties"),
  MODIFIERS("ui.tab.modifiers"),
  RENDERING("ui.tab.rendering"),
  PREVIEW("ui.tab.preview"),
  SOURCE("ui.tab.source"),
  DEBUG("ui.tab.debug"),
}

@Composable
internal fun DetailTabs(
  controller: IconInspectorController,
  target: InspectionTarget,
  selectedNode: InspectorNode?,
  flagged: List<FlaggedRenderer>,
  modifier: Modifier = Modifier,
) {
  val modifierEditing by controller.modifierEditing.collectAsState()
  var tabIndex by remember { mutableIntStateOf(0) }
  val tabs = remember(tabIndex) {
    DetailTab.entries.mapIndexed { index, tab ->
      TabData.Default(
        selected = index == tabIndex,
        content = { state -> SimpleTabContent(label = DevkitIconsInspectorBundle.message(tab.key), state = state) },
        closable = false,
        onClick = { tabIndex = index },
      )
    }
  }
  Column(modifier) {
    TabStrip(tabs, JewelTheme.defaultTabStyle, Modifier.fillMaxWidth())
    Box(Modifier.fillMaxWidth().weight(1f)) {
      when (DetailTab.entries[tabIndex]) {
        DetailTab.PROPERTIES -> PropertiesTab(selectedNode)
        DetailTab.MODIFIERS -> ModifiersTab(controller, selectedNode, modifierEditing)
        DetailTab.RENDERING -> RenderingTab(target)
        DetailTab.PREVIEW -> PreviewTab(target.previews)
        DetailTab.SOURCE -> SourceTab(target.sources)
        DetailTab.DEBUG -> DebugTab(controller, selectedNode, flagged)
      }
    }
  }
}

@Composable
private fun HintText(text: String, modifier: Modifier = Modifier) {
  Text(text, modifier = modifier.padding(12.dp), color = JewelTheme.globalColors.text.info)
}

@Composable
private fun PropertiesTab(node: InspectorNode?) {
  if (node == null) {
    HintText(DevkitIconsInspectorBundle.message("ui.properties.empty"))
    return
  }
  VerticallyScrollableContainer(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      @OptIn(ExperimentalLayoutApi::class)
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp), itemVerticalAlignment = Alignment.CenterVertically) {
        OriginTag(node.origin)
        Text("${node.title}${node.subtitle?.let { " · $it" } ?: ""}")
      }
      node.warning?.let { Text(it, color = Color(0xFFD9822B)) }
      node.sizes?.let {
        GroupHeader(DevkitIconsInspectorBundle.message("ui.sizes.title"), Modifier.fillMaxWidth())
        SizesSection(it, Modifier.fillMaxWidth())
      }
      if (node.properties.isEmpty()) {
        Text(DevkitIconsInspectorBundle.message("ui.properties.none"), color = JewelTheme.globalColors.text.info)
      }
      else {
        PropertyTable(node.properties, Modifier.fillMaxWidth(), grouped = true)
      }
    }
  }
}

@Composable
private fun RenderingTab(target: InspectionTarget) {
  val rendering = target.rendering
  VerticallyScrollableContainer(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      target.root.sizes?.let {
        GroupHeader(DevkitIconsInspectorBundle.message("ui.sizes.picked.title"), Modifier.fillMaxWidth())
        SizesSection(it, Modifier.fillMaxWidth())
      }
      GroupHeader(DevkitIconsInspectorBundle.message("ui.rendering.title"), Modifier.fillMaxWidth())
      if (rendering.properties.isEmpty()) {
        Text(DevkitIconsInspectorBundle.message("ui.rendering.none"), color = JewelTheme.globalColors.text.info)
      }
      else {
        PropertyTable(rendering.properties, Modifier.fillMaxWidth())
      }
      GroupHeader(DevkitIconsInspectorBundle.message("ui.rendering.last.paint"), Modifier.fillMaxWidth())
      if (rendering.lastPaint.isEmpty()) {
        Text(DevkitIconsInspectorBundle.message("ui.rendering.last.paint.none"), color = JewelTheme.globalColors.text.info)
      }
      else {
        PropertyTable(rendering.lastPaint, Modifier.fillMaxWidth())
      }
    }
  }
}

/** Selectable key/value table. When [grouped], properties are sectioned under [InspectorProperty.group] headers. */
@Composable
internal fun PropertyTable(properties: List<InspectorProperty>, modifier: Modifier = Modifier, grouped: Boolean = false) {
  SelectionContainer {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
      if (grouped) {
        for ((group, items) in properties.groupBy { it.group }) {
          if (group != null) GroupHeader(group, Modifier.fillMaxWidth().padding(top = 4.dp))
          items.forEach { PropertyRow(it) }
        }
      }
      else {
        properties.forEach { PropertyRow(it) }
      }
    }
  }
}

@Composable
private fun PropertyRow(property: InspectorProperty) {
  Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
    Text(
      property.name,
      color = JewelTheme.globalColors.text.info,
      modifier = Modifier.widthIn(max = 150.dp).weight(0.4f),
    )
    Text(
      property.value ?: DevkitIconsInspectorBundle.message("ui.property.no.value"),
      fontFamily = FontFamily.Monospace,
      fontSize = 12.sp,
      modifier = Modifier.weight(0.6f),
    )
  }
}

// ---- Preview ----

@Composable
private fun PreviewTab(previews: List<PreviewImage>) {
  if (previews.isEmpty()) {
    HintText(DevkitIconsInspectorBundle.message("ui.preview.none"))
    return
  }
  VerticallyScrollableContainer(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
      previews.forEach { PreviewItem(it) }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewItem(preview: PreviewImage) {
  val bitmap = remember(preview) { preview.toImageBitmap() }
  GroupHeader("${preview.label}  ${preview.width}×${preview.height}", Modifier.fillMaxWidth())
  FlowRow(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(16.dp),
    verticalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    if (bitmap != null) {
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(DevkitIconsInspectorBundle.message("ui.preview.native"), color = JewelTheme.globalColors.text.info)
        PixelImage(bitmap, scale = 1)
      }
      Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(DevkitIconsInspectorBundle.message("ui.preview.zoomed"), color = JewelTheme.globalColors.text.info)
        Box(Modifier.horizontalScroll(rememberScrollState())) { PixelImage(bitmap, scale = 8) }
      }
    }
  }
}

private fun PreviewImage.toImageBitmap(): ImageBitmap? {
  if (width <= 0 || height <= 0 || argb.size < width * height) return null
  val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
  image.setRGB(0, 0, width, height, argb, 0, width)
  return image.toComposeImageBitmap()
}

/** Draws [bitmap] at [scale] physical pixels per image pixel on a checkerboard, without smoothing. */
@Composable
internal fun PixelImage(bitmap: ImageBitmap, scale: Int) {
  val density = LocalDensity.current
  val widthPx = bitmap.width * scale
  val heightPx = bitmap.height * scale
  val widthDp = with(density) { widthPx.toDp() }
  val heightDp = with(density) { heightPx.toDp() }
  val light = if (JewelTheme.isDark) Color(0xFF3C3F41) else Color(0xFFFFFFFF)
  val dark = if (JewelTheme.isDark) Color(0xFF2B2D30) else Color(0xFFE6E8EC)
  Canvas(Modifier.size(widthDp, heightDp)) {
    drawCheckerboard(light, dark, tile = 8f * density.density)
    drawImage(
      image = bitmap,
      srcOffset = IntOffset.Zero,
      srcSize = IntSize(bitmap.width, bitmap.height),
      dstOffset = IntOffset.Zero,
      dstSize = IntSize(widthPx, heightPx),
      filterQuality = FilterQuality.None,
    )
  }
}

internal fun DrawScope.drawCheckerboard(light: Color, dark: Color, tile: Float) {
  drawRect(light, Offset.Zero, size)
  var row = 0
  var y = 0f
  while (y < size.height) {
    var col = 0
    var x = 0f
    while (x < size.width) {
      if ((row + col) % 2 == 1) {
        drawRect(dark, Offset(x, y), Size(minOf(tile, size.width - x), minOf(tile, size.height - y)))
      }
      x += tile
      col++
    }
    y += tile
    row++
  }
}

// ---- Debug ----

@Composable
private fun DebugTab(controller: IconInspectorController, node: InspectorNode?, flagged: List<FlaggedRenderer>) {
  VerticallyScrollableContainer(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxWidth().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
      GroupHeader(DevkitIconsInspectorBundle.message("ui.debug.node"), Modifier.fillMaxWidth())
      when {
        node == null -> Text(DevkitIconsInspectorBundle.message("ui.debug.select.node"), color = JewelTheme.globalColors.text.info)
        node.debuggable -> {
          val isFlagged = remember(flagged, node.id) { controller.isFlagged(node.id) }
          Text("${node.title}${node.subtitle?.let { " · $it" } ?: ""}")
          if (node.recreations > 0) {
            Text(
              "\u21BB " + DevkitIconsInspectorBundle.message("ui.debug.recreated.warning"),
              color = amberColor(JewelTheme.isDark),
              modifier = Modifier.fillMaxWidth(),
            )
          }
          CheckboxRow(
            checked = isFlagged,
            onCheckedChange = { controller.setFlagged(node.id, it) },
          ) {
            Text(DevkitIconsInspectorBundle.message("ui.debug.toggle"))
          }
        }
        else -> Text(DevkitIconsInspectorBundle.message("ui.debug.not.debuggable"), color = JewelTheme.globalColors.text.info)
      }

      Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
      FlaggedList(controller, flagged)
      Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
      BreakpointInstructions(controller.breakpointExceptionClass)
    }
  }
}

@Composable
private fun FlaggedList(controller: IconInspectorController, flagged: List<FlaggedRenderer>) {
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    GroupHeader(DevkitIconsInspectorBundle.message("ui.debug.flagged.title"), Modifier.weight(1f))
    OutlinedButton(onClick = controller::clearFlags, enabled = flagged.isNotEmpty()) {
      Text(DevkitIconsInspectorBundle.message("ui.debug.clear.all"))
    }
  }
  if (flagged.isEmpty()) {
    Text(DevkitIconsInspectorBundle.message("ui.debug.flagged.none"), color = JewelTheme.globalColors.text.info)
  }
  for (item in flagged) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      Text(item.title, modifier = Modifier.weight(1f), maxLines = 2)
      Text(
        DevkitIconsInspectorBundle.message("ui.debug.hits", item.hitCount),
        fontFamily = FontFamily.Monospace,
        fontSize = 12.sp,
        color = JewelTheme.globalColors.text.info,
      )
      OutlinedButton(onClick = { controller.setFlagged(item.nodeId, false) }) {
        Text(DevkitIconsInspectorBundle.message("ui.debug.remove"))
      }
    }
  }
}

@Composable
private fun BreakpointInstructions(exceptionClass: String) {
  GroupHeader(DevkitIconsInspectorBundle.message("ui.debug.instructions.title"), Modifier.fillMaxWidth())
  Text(DevkitIconsInspectorBundle.message("ui.debug.instructions", exceptionClass))
  Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    SelectionContainer(Modifier.weight(1f)) {
      Text(exceptionClass, fontFamily = FontFamily.Monospace, fontSize = 12.sp)
    }
    OutlinedButton(onClick = { CopyPasteManager.getInstance().setContents(StringSelection(exceptionClass)) }) {
      Text(DevkitIconsInspectorBundle.message("ui.copy"))
    }
  }
}
