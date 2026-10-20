// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.intellij.devkit.icons.inspector.DevkitIconsInspectorBundle
import com.intellij.devkit.icons.inspector.model.IconInspectorController
import com.intellij.devkit.icons.inspector.model.InspectionTarget
import com.intellij.devkit.icons.inspector.model.InspectorNode
import com.intellij.devkit.icons.inspector.model.InspectorState
import org.jetbrains.jewel.ui.Orientation
import org.jetbrains.jewel.foundation.theme.JewelTheme
import org.jetbrains.jewel.ui.component.DefaultButton
import org.jetbrains.jewel.ui.component.Divider
import org.jetbrains.jewel.ui.component.HorizontalSplitLayout
import org.jetbrains.jewel.ui.component.OutlinedButton
import org.jetbrains.jewel.ui.component.Text
import org.jetbrains.jewel.ui.component.Tooltip
import org.jetbrains.jewel.ui.component.VerticallyScrollableContainer
import org.jetbrains.jewel.ui.component.rememberSplitLayoutState

/**
 * Entry point of the Icon Inspector UI. All data and actions go through [controller]. [onDock], if set, adds the
 * "Dock to Tool Window" button (the detached window, see [IconInspectorWindows]).
 */
@Composable
internal fun IconInspectorPanel(controller: IconInspectorController, onDock: (() -> Unit)? = null) {
  val state by controller.state.collectAsState()
  val flagged by controller.flagged.collectAsState()

  Column(Modifier.fillMaxSize()) {
    TopBar(controller, state, onDock)
    Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
    Box(Modifier.fillMaxWidth().weight(1f)) {
      when (val s = state) {
        InspectorState.Empty -> EmptyState(controller)
        InspectorState.Picking -> PickingState()
        is InspectorState.Error -> ErrorState(controller, s.message)
        is InspectorState.Inspecting -> InspectingContent(controller, s.target, flagged)
      }
    }
  }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TopBar(controller: IconInspectorController, state: InspectorState, onDock: (() -> Unit)?) {
  FlowRow(
    Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
    verticalArrangement = Arrangement.spacedBy(6.dp),
    itemVerticalAlignment = Alignment.CenterVertically,
  ) {
    if (state == InspectorState.Picking) {
      Text(DevkitIconsInspectorBundle.message("ui.picking"))
      OutlinedButton(onClick = controller::cancelPicking) { Text(DevkitIconsInspectorBundle.message("ui.cancel")) }
    }
    else {
      DefaultButton(onClick = controller::startPicking) { Text(DevkitIconsInspectorBundle.message("ui.pick")) }
      OutlinedButton(onClick = controller::refresh, enabled = state is InspectorState.Inspecting) {
        Text(DevkitIconsInspectorBundle.message("ui.refresh"))
      }
      OutlinedButton(onClick = controller::clear, enabled = state !is InspectorState.Empty) {
        Text(DevkitIconsInspectorBundle.message("ui.clear"))
      }
    }
    if (onDock != null) {
      OutlinedButton(onClick = onDock) { Text(DevkitIconsInspectorBundle.message("ui.dock")) }
    }
  }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
  Box(Modifier.fillMaxSize().padding(16.dp), contentAlignment = Alignment.Center) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
      content()
    }
  }
}

/** Shown when nothing has been picked yet. */
@Composable
internal fun EmptyState(controller: IconInspectorController) {
  CenteredMessage {
    Text(DevkitIconsInspectorBundle.message("inspector.empty.title"), textAlign = TextAlign.Center)
    Text(
      DevkitIconsInspectorBundle.message("inspector.empty.hint"),
      color = JewelTheme.globalColors.text.info,
      textAlign = TextAlign.Center,
    )
    DefaultButton(onClick = controller::startPicking) { Text(DevkitIconsInspectorBundle.message("ui.pick")) }
  }
}

@Composable
private fun PickingState() {
  CenteredMessage {
    Text(DevkitIconsInspectorBundle.message("ui.picking"), textAlign = TextAlign.Center)
  }
}

@Composable
private fun ErrorState(controller: IconInspectorController, message: String) {
  CenteredMessage {
    Text(DevkitIconsInspectorBundle.message("ui.error.title"), textAlign = TextAlign.Center)
    Text(message, color = JewelTheme.globalColors.text.info, textAlign = TextAlign.Center)
    DefaultButton(onClick = controller::startPicking) { Text(DevkitIconsInspectorBundle.message("ui.retry")) }
  }
}

@Composable
private fun InspectingContent(
  controller: IconInspectorController,
  target: InspectionTarget,
  flagged: List<com.intellij.devkit.icons.inspector.model.FlaggedRenderer>,
) {
  var selectedId by remember { mutableStateOf<String?>(null) }
  val selectedNode = remember(target.root, selectedId) { selectedId?.let { findNode(target.root, it) } }

  Column(Modifier.fillMaxSize()) {
    val editing by controller.modifierEditing.collectAsState()
    TargetSummary(target, (editing?.editCount ?: 0) > 0)
    StabilityBanner(target.stability, controller)
    Divider(Orientation.Horizontal, Modifier.fillMaxWidth())
    HorizontalSplitLayout(
      first = {
        NodeTree(
          root = target.root,
          controller = controller,
          flagged = flagged,
          selectedId = selectedId,
          onSelected = { selectedId = it },
          modifier = Modifier.fillMaxSize(),
        )
      },
      second = { DetailTabs(controller, target, selectedNode, flagged, Modifier.fillMaxSize()) },
      modifier = Modifier.fillMaxSize(),
      firstPaneMinWidth = 140.dp,
      secondPaneMinWidth = 180.dp,
      state = rememberSplitLayoutState(0.4f),
    )
  }
}

@Composable
private fun TargetSummary(target: InspectionTarget, edited: Boolean) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
    @OptIn(ExperimentalLayoutApi::class)
    FlowRow(
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
      itemVerticalAlignment = Alignment.CenterVertically,
    ) {
      Text(target.componentDescription, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text("·", color = JewelTheme.globalColors.text.info)
      Text(
        target.iconClass,
        fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace,
        color = JewelTheme.globalColors.text.info,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (target.bridged) BridgedPill()
      if (edited) Text(DevkitIconsInspectorBundle.message("ui.summary.edited"), color = amberColor(JewelTheme.isDark))
    }
    if (target.summary.isNotEmpty()) {
      VerticallyScrollableContainer(Modifier.fillMaxWidth().heightIn(max = 110.dp)) {
        PropertyTable(target.summary, Modifier.fillMaxWidth())
      }
    }
  }
}

@Composable
private fun BridgedPill() {
  val color = JewelTheme.globalColors.text.info
  Tooltip(tooltip = { Text(DevkitIconsInspectorBundle.message("ui.bridged.tooltip")) }) {
    Text(
      DevkitIconsInspectorBundle.message("ui.bridged"),
      color = color,
      fontSize = 11.sp,
      modifier = Modifier
        .background(color.copy(alpha = 0.15f), RoundedCornerShape(8.dp))
        .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
        .padding(horizontal = 6.dp, vertical = 1.dp),
    )
  }
}

internal fun findNode(root: InspectorNode, id: String): InspectorNode? {
  if (root.id == id) return root
  for (child in root.children) {
    findNode(child, id)?.let { return it }
  }
  return null
}
