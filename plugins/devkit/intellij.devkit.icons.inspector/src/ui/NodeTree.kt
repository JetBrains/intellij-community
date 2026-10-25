// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import com.intellij.devkit.icons.inspector.model.InspectorNodeOrigin
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.FlaggedRenderer
import com.intellij.devkit.icons.inspector.model.IconInspectorController
import com.intellij.devkit.icons.inspector.model.InspectorNode
import com.intellij.devkit.icons.inspector.model.InspectorNodeKind
import org.jetbrains.jewel.foundation.lazy.tree.TreeGeneratorScope
import org.jetbrains.jewel.foundation.lazy.tree.buildTree
import org.jetbrains.jewel.foundation.lazy.tree.rememberTreeState
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.Icon
import org.jetbrains.jewel.ui.component.LazyTree
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.icons.AllIconsKeys
import org.jetbrains.jewel.ui.theme.treeStyle

private val BREAKPOINT_RED = Color(0xFFE5483F)

@Composable
internal fun NodeTree(
  root: InspectorNode,
  controller: IconInspectorController,
  flagged: List<FlaggedRenderer>,
  selectedId: String?,
  onSelected: (String?) -> Unit,
  modifier: Modifier = Modifier,
) {
  Column(modifier) {
    OriginLegend()
    NodeTreeContent(root, controller, flagged, selectedId, onSelected, Modifier.fillMaxWidth().weight(1f))
  }
}

@Composable
private fun NodeTreeContent(
  root: InspectorNode,
  controller: IconInspectorController,
  flagged: List<FlaggedRenderer>,
  selectedId: String?,
  onSelected: (String?) -> Unit,
  modifier: Modifier,
) {
  val tree = remember(root) { buildTree<InspectorNode> { addInspectorNode(root) } }
  val treeState = rememberTreeState()
  LaunchedEffect(tree) { treeState.openNodes(collectBranchIds(root)) }
  // Keep the tree's own highlight in sync when the selected node no longer exists (e.g. after a refresh).
  LaunchedEffect(tree, selectedId) {
    if (selectedId != null && findNode(root, selectedId) == null) {
      treeState.selectedKeys = emptySet()
      onSelected(null)
    }
  }

  LazyTree(
    tree = tree,
    modifier = modifier,
    treeState = treeState,
    onSelectionChange = { selection -> onSelected(selection.firstOrNull()?.data?.id) },
  ) { element ->
    val node = element.data
    val isFlagged = remember(flagged, node.id) { controller.isFlagged(node.id) }
    NodeRow(node, isFlagged, leaf = node.children.isEmpty())
  }
}

private fun TreeGeneratorScope<InspectorNode>.addInspectorNode(node: InspectorNode) {
  if (node.children.isEmpty()) {
    addLeaf(node, node.id)
  }
  else {
    addNode(node, node.id) { node.children.forEach { addInspectorNode(it) } }
  }
}

private fun collectBranchIds(node: InspectorNode, out: MutableList<Any> = mutableListOf()): List<Any> {
  if (node.children.isNotEmpty()) {
    out.add(node.id)
    node.children.forEach { collectBranchIds(it, out) }
  }
  return out
}

@Composable
private fun NodeRow(node: InspectorNode, isFlagged: Boolean, leaf: Boolean) {
  val dark = JewelTheme.isDark
  val legacy = node.origin == InspectorNodeOrigin.LEGACY
  val textColor = JewelTheme.globalColors.text.normal
  Row(
    Modifier.fillMaxWidth()
      // Jewel's LazyTree draws no chevron (and no chevron gap) for leaves, so a leaf would line up with its parent's
      // content and read as a sibling; reserve the same space a node's chevron takes.
      .padding(start = if (leaf) LEAF_CHEVRON_WIDTH + JewelTheme.treeStyle.metrics.chevronContentGap else 0.dp)
      .drawBehind { drawOriginStripe(node.origin, dark) }.padding(start = 7.dp, top = 1.dp, bottom = 1.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(6.dp),
  ) {
    KindBadge(node.kind)
    OriginTag(node.origin)
    Text(
      node.title,
      color = if (legacy) textColor.copy(alpha = 0.7f) else textColor,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    node.subtitle?.let {
      Text(
        it,
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        color = JewelTheme.globalColors.text.info,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = Modifier.weight(1f, fill = false),
      )
    }
    node.sizes?.let { sizes ->
      sizeTrail(sizes, JewelTheme.globalColors.text.info)?.let {
        Text(
          it,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = JewelTheme.globalColors.text.info,
          maxLines = 1,
          softWrap = false,
        )
      }
    }
    node.warning?.let { warning ->
      Tooltip(tooltip = { Text(warning) }) { Icon(AllIconsKeys.General.Warning, contentDescription = warning, modifier = Modifier.size(14.dp)) }
    }
    if (node.recreations > 0) {
      val amber = amberColor(dark)
      Tooltip(tooltip = { Text(DevkitIconsInspectorBundle.message("ui.recreated.tooltip", node.recreations)) }) {
        Text(
          DevkitIconsInspectorBundle.message("ui.recreated.chip", node.recreations),
          color = amber,
          fontSize = 10.sp,
          fontWeight = FontWeight.SemiBold,
          maxLines = 1,
          modifier = Modifier
            .background(amber.copy(alpha = 0.18f), RoundedCornerShape(8.dp))
            .border(1.dp, amber.copy(alpha = 0.7f), RoundedCornerShape(8.dp))
            .padding(horizontal = 5.dp, vertical = 0.dp),
        )
      }
    }
    if (isFlagged) {
      Tooltip(tooltip = { Text(DevkitIconsInspectorBundle.message("ui.breakpoint.tooltip")) }) {
        Box(Modifier.size(9.dp).background(BREAKPOINT_RED, CircleShape))
      }
    }
  }
}

/** Kind colors: (light theme, dark theme). Muted hues; the badge uses tinted background + colored letter. */
private fun kindColor(kind: InspectorNodeKind, dark: Boolean): Color = when (kind) {
  InspectorNodeKind.SWING_ICON -> if (dark) Color(0xFF6E9BF5) else Color(0xFF2F63D8)
  InspectorNodeKind.ICON -> if (dark) Color(0xFF63C07A) else Color(0xFF2B8A4B)
  InspectorNodeKind.LAYER -> if (dark) Color(0xFFB88CF0) else Color(0xFF7C3FC6)
  InspectorNodeKind.MODIFIER -> if (dark) Color(0xFFE5A94D) else Color(0xFFB36B00)
  InspectorNodeKind.RESOURCE -> if (dark) Color(0xFF4CC3CF) else Color(0xFF14808C)
  InspectorNodeKind.OTHER -> if (dark) Color(0xFF9DA0A8) else Color(0xFF6C707E)
}

private fun kindLetter(kind: InspectorNodeKind): String = when (kind) {
  InspectorNodeKind.SWING_ICON -> "S"
  InspectorNodeKind.ICON -> "I"
  InspectorNodeKind.LAYER -> "L"
  InspectorNodeKind.MODIFIER -> "M"
  InspectorNodeKind.RESOURCE -> "R"
  InspectorNodeKind.OTHER -> "?"
}

@Composable
private fun KindBadge(kind: InspectorNodeKind) {
  val color = kindColor(kind, JewelTheme.isDark)
  Box(
    Modifier.size(16.dp)
      .background(color.copy(alpha = 0.2f), RoundedCornerShape(3.dp))
      .border(1.dp, color.copy(alpha = 0.7f), RoundedCornerShape(3.dp)),
    contentAlignment = Alignment.Center,
  ) {
    Text(kindLetter(kind), color = color, fontSize = 10.sp, fontWeight = FontWeight.Bold)
  }
}

// ---- Origin (new icons API vs legacy Swing vs bridge) ----

private fun apiColor(dark: Boolean) = if (dark) Color(0xFF5C9DFF) else Color(0xFF2D6FE0)
private fun legacyColor(dark: Boolean) = if (dark) Color(0xFFB8977A) else Color(0xFF8A6A4A)

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawOriginStripe(origin: InspectorNodeOrigin, dark: Boolean) {
  val w = 3.dp.toPx()
  val api = apiColor(dark)
  val legacy = legacyColor(dark)
  when (origin) {
    InspectorNodeOrigin.ICONS_API -> drawRect(api, Offset.Zero, androidx.compose.ui.geometry.Size(w, size.height))
    InspectorNodeOrigin.LEGACY -> {
      // dashed
      val dash = 4.dp.toPx()
      var y = 0f
      while (y < size.height) {
        drawRect(legacy, Offset(0f, y), androidx.compose.ui.geometry.Size(w, minOf(dash, size.height - y)))
        y += dash * 1.75f
      }
    }
    InspectorNodeOrigin.BRIDGE -> {
      drawRect(api, Offset.Zero, androidx.compose.ui.geometry.Size(w, size.height / 2))
      drawRect(legacy, Offset(0f, size.height / 2), androidx.compose.ui.geometry.Size(w, size.height / 2))
    }
  }
}

private fun originTagKey(origin: InspectorNodeOrigin) = when (origin) {
  InspectorNodeOrigin.ICONS_API -> "ui.origin.api.tag"
  InspectorNodeOrigin.LEGACY -> "ui.origin.legacy.tag"
  InspectorNodeOrigin.BRIDGE -> "ui.origin.bridge.tag"
}

private fun originDescKey(origin: InspectorNodeOrigin) = when (origin) {
  InspectorNodeOrigin.ICONS_API -> "ui.origin.api.desc"
  InspectorNodeOrigin.LEGACY -> "ui.origin.legacy.desc"
  InspectorNodeOrigin.BRIDGE -> "ui.origin.bridge.desc"
}

/** Tiny text tag: API = solid outline on tint, Swing = dashed outline, Bridge = two-tone gradient outline. */
@Composable
internal fun OriginTag(origin: InspectorNodeOrigin, modifier: Modifier = Modifier) {
  val dark = JewelTheme.isDark
  val api = apiColor(dark)
  val legacy = legacyColor(dark)
  val color = when (origin) {
    InspectorNodeOrigin.ICONS_API -> api
    InspectorNodeOrigin.LEGACY -> legacy
    InspectorNodeOrigin.BRIDGE -> JewelTheme.globalColors.text.normal
  }
  Text(
    DevkitIconsInspectorBundle.message(originTagKey(origin)),
    color = color,
    fontSize = 9.sp,
    fontWeight = FontWeight.SemiBold,
    maxLines = 1,
    modifier = modifier
      .drawBehind {
        val r = CornerRadius(3.dp.toPx())
        val stroke = 1.dp.toPx()
        when (origin) {
          InspectorNodeOrigin.ICONS_API -> {
            drawRoundRect(api.copy(alpha = 0.18f), cornerRadius = r)
            drawRoundRect(api, cornerRadius = r, style = Stroke(stroke))
          }
          InspectorNodeOrigin.LEGACY ->
            drawRoundRect(legacy, cornerRadius = r, style = Stroke(stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3.dp.toPx(), 2.dp.toPx()))))
          InspectorNodeOrigin.BRIDGE -> {
            drawRoundRect(Brush.horizontalGradient(listOf(api.copy(alpha = 0.25f), legacy.copy(alpha = 0.25f))), cornerRadius = r)
            drawRoundRect(Brush.horizontalGradient(listOf(api, legacy)), cornerRadius = r, style = Stroke(stroke))
          }
        }
      }
      .padding(horizontal = 4.dp, vertical = 1.dp),
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OriginLegend() {
  FlowRow(
    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    for (origin in InspectorNodeOrigin.entries) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OriginTag(origin)
        Text(DevkitIconsInspectorBundle.message(originDescKey(origin)), fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
      }
    }
  }
}

/** Width of the chevron icon LazyTree draws in front of a node (its intrinsic 16dp icon size). */
private val LEAF_CHEVRON_WIDTH = 16.dp
