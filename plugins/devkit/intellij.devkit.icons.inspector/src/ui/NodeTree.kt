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
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.Measurable
import androidx.compose.ui.layout.Placeable
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
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

/**
 * The branches opened when a tree is shown: all of them except layers, which start folded unless they contain a Swing
 * icon (a `SwingIconLayer` and what it wraps), so that the bridge to the legacy icon stays visible.
 */
private fun collectBranchIds(node: InspectorNode, out: MutableList<Any> = mutableListOf()): List<Any> {
  if (node.children.isNotEmpty()) {
    if (node.kind != InspectorNodeKind.LAYER || containsSwingIcon(node)) out.add(node.id)
    node.children.forEach { collectBranchIds(it, out) }
  }
  return out
}

private fun containsSwingIcon(node: InspectorNode): Boolean =
  node.origin != InspectorNodeOrigin.ICONS_API || node.kind == InspectorNodeKind.SWING_ICON || node.children.any(::containsSwingIcon)

@Composable
private fun NodeRow(node: InspectorNode, isFlagged: Boolean, leaf: Boolean) {
  val dark = JewelTheme.isDark
  val legacy = node.origin == InspectorNodeOrigin.LEGACY
  val textColor = JewelTheme.globalColors.text.normal
  val infoColor = JewelTheme.globalColors.text.info
  val sizesText = node.sizes?.let { sizeTrail(it, infoColor) }
  TitleFirstRow(
    leading = { OriginTag(node.origin, node.kind) },
    title = {
      Text(
        node.title,
        color = if (legacy) textColor.copy(alpha = 0.7f) else textColor,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    },
    subtitle = node.subtitle?.let { subtitle ->
      {
        Text(
          subtitle,
          fontFamily = FontFamily.Monospace,
          fontSize = 11.sp,
          color = infoColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
    },
    trailing = {
      sizesText?.let {
        Text(it, fontFamily = FontFamily.Monospace, fontSize = 11.sp, color = infoColor, maxLines = 1, softWrap = false)
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
            softWrap = false,
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
    },
    modifier = Modifier.fillMaxWidth()
      // Jewel's LazyTree draws no chevron (and no chevron gap) for leaves, so a leaf would line up with its parent's
      // content and read as a sibling; reserve the same space a node's chevron takes.
      .padding(start = if (leaf) LEAF_CHEVRON_WIDTH + JewelTheme.treeStyle.metrics.chevronContentGap else 0.dp),
  )
}

/**
 * A tree row that keeps the title readable in a narrow tree: [leading] and [title] are placed first (the title is
 * ellipsized only when it alone does not fit), then the [trailing] items in order while each fits completely, and the
 * [subtitle] gets the width that is left, ellipsized, or is left out when that is less than [MIN_SUBTITLE_WIDTH].
 */
@Composable
private fun TitleFirstRow(
  leading: @Composable () -> Unit,
  title: @Composable () -> Unit,
  subtitle: (@Composable () -> Unit)?,
  trailing: @Composable () -> Unit,
  modifier: Modifier = Modifier,
) {
  Layout(contents = listOf(leading, title, subtitle ?: {}, trailing), modifier = modifier) { measurables, constraints ->
    val (leadingMeasurables, titleMeasurables, subtitleMeasurables, trailingMeasurables) = measurables
    val gap = ROW_GAP.roundToPx()
    // a horizontally scrolled tree gives an unbounded width: then everything fits
    val maxWidth = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE / 4
    var used = 0
    fun measure(measurable: Measurable, width: Int): Placeable =
      measurable.measure(Constraints(maxWidth = width.coerceAtLeast(0), maxHeight = constraints.maxHeight))
    fun take(placeable: Placeable) {
      if (used > 0) used += gap
      used += placeable.width
    }

    val leadingPlaceables = leadingMeasurables.map { measure(it, maxWidth - used).also(::take) }
    val titlePlaceables = titleMeasurables.map { measure(it, maxWidth - used - (if (used > 0) gap else 0)).also(::take) }
    val trailingPlaceables = ArrayList<Placeable>()
    for (measurable in trailingMeasurables) {
      val placeable = measure(measurable, Constraints.Infinity)
      if (used + gap + placeable.width > maxWidth) break
      trailingPlaceables.add(placeable)
      take(placeable)
    }
    val subtitleWidth = maxWidth - used - gap
    val subtitlePlaceables =
      if (subtitleMeasurables.isEmpty() || subtitleWidth < MIN_SUBTITLE_WIDTH.roundToPx()) emptyList()
      else subtitleMeasurables.map { measure(it, subtitleWidth).also(::take) }

    val ordered = leadingPlaceables + titlePlaceables + subtitlePlaceables + trailingPlaceables
    val height = (ordered.maxOfOrNull { it.height } ?: 0).coerceAtLeast(constraints.minHeight)
    val width = used.coerceIn(constraints.minWidth, maxOf(constraints.minWidth, maxWidth))
    layout(width, height) {
      var x = 0
      for (placeable in ordered) {
        placeable.placeRelative(x, (height - placeable.height) / 2)
        x += placeable.width + gap
      }
    }
  }
}

private val ROW_GAP = 6.dp

/** A subtitle narrower than this would only show an ellipsis, so it is left out. */
private val MIN_SUBTITLE_WIDTH = 32.dp

// ---- Origin (new icons API vs legacy Swing vs bridge) ----

private fun apiColor(dark: Boolean) = if (dark) Color(0xFF5C9DFF) else Color(0xFF2D6FE0)
private fun resourceColor(dark: Boolean) = if (dark) Color(0xFF5FB865) else Color(0xFF2E8B3E)
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

/**
 * For new icons API nodes, the tag names what the node is: an icon descriptor, a layer or a modifier. Image resource
 * locations have a tag of their own.
 */
private fun originTagKey(origin: InspectorNodeOrigin, kind: InspectorNodeKind?) = when {
  kind == InspectorNodeKind.RESOURCE -> "ui.origin.resource.tag"
  origin == InspectorNodeOrigin.ICONS_API -> when (kind) {
    // a new-API SWING_ICON node is the paint of a Compose icon, not a descriptor
    InspectorNodeKind.ICON -> "ui.origin.api.descriptor.tag"
    InspectorNodeKind.LAYER -> "ui.origin.api.layer.tag"
    InspectorNodeKind.MODIFIER -> "ui.origin.api.modifier.tag"
    else -> "ui.origin.api.tag"
  }
  origin == InspectorNodeOrigin.LEGACY -> "ui.origin.legacy.tag"
  else -> "ui.origin.bridge.tag"
}

private fun originDescKey(origin: InspectorNodeOrigin, kind: InspectorNodeKind?) = originTagKey(origin, kind).replace(".tag", ".desc")

/**
 * Tiny text tag: API = solid outline on tint, Swing = dashed outline, Bridge = two-tone gradient outline, resource
 * location = green. For new icons API nodes, [kind] picks the label (ID, L, M).
 */
@Composable
internal fun OriginTag(origin: InspectorNodeOrigin, kind: InspectorNodeKind? = null, modifier: Modifier = Modifier) {
  val dark = JewelTheme.isDark
  val api = apiColor(dark)
  val legacy = legacyColor(dark)
  val resource = resourceColor(dark)
  val isResource = kind == InspectorNodeKind.RESOURCE
  val color = when {
    isResource -> resource
    origin == InspectorNodeOrigin.ICONS_API -> api
    origin == InspectorNodeOrigin.LEGACY -> legacy
    else -> JewelTheme.globalColors.text.normal
  }
  Text(
    DevkitIconsInspectorBundle.message(originTagKey(origin, kind)),
    color = color,
    fontSize = 9.sp,
    fontWeight = FontWeight.SemiBold,
    maxLines = 1,
    modifier = modifier
      .drawBehind {
        val r = CornerRadius(3.dp.toPx())
        val stroke = 1.dp.toPx()
        if (isResource) {
          drawRoundRect(resource.copy(alpha = 0.18f), cornerRadius = r)
          drawRoundRect(resource, cornerRadius = r, style = Stroke(stroke))
          return@drawBehind
        }
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

/** The tags the legend explains, in its order. */
private val LEGEND_ENTRIES: List<Pair<InspectorNodeOrigin, InspectorNodeKind?>> = listOf(
  InspectorNodeOrigin.ICONS_API to InspectorNodeKind.ICON,
  InspectorNodeOrigin.ICONS_API to InspectorNodeKind.LAYER,
  InspectorNodeOrigin.ICONS_API to InspectorNodeKind.MODIFIER,
  InspectorNodeOrigin.ICONS_API to InspectorNodeKind.RESOURCE,
  InspectorNodeOrigin.ICONS_API to null,
  InspectorNodeOrigin.LEGACY to null,
  InspectorNodeOrigin.BRIDGE to null,
)

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OriginLegend() {
  FlowRow(
    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp),
    horizontalArrangement = Arrangement.spacedBy(10.dp),
    verticalArrangement = Arrangement.spacedBy(2.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    for ((origin, kind) in LEGEND_ENTRIES) {
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        OriginTag(origin, kind)
        Text(DevkitIconsInspectorBundle.message(originDescKey(origin, kind)), fontSize = 11.sp, color = JewelTheme.globalColors.text.info)
      }
    }
  }
}

/** Width of the chevron icon LazyTree draws in front of a node (its intrinsic 16dp icon size). */
private val LEAF_CHEVRON_WIDTH = 16.dp
