// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.devkit.icons.inspector.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import org.jetbrains.compose.ui.tooling.preview.Preview

// DEV ONLY: previews of the Icon Inspector UI with the canned sample of FakeIconInspectorController, for the Compose
// preview tool window of DevKit (no IDE restart and no picking needed to see a UI change).

@Preview
@Composable
internal fun IconInspectorPanelPreview() {
  PreviewHost(900.dp, 640.dp) { IconInspectorPanel(it) }
}

/** A narrow tool window: tree rows keep their titles and drop the rest. */
@Preview
@Composable
internal fun IconInspectorPanelNarrowPreview() {
  PreviewHost(360.dp, 640.dp) { IconInspectorPanel(it) }
}

/** The separate window, which adds the "Dock to Tool Window" button. */
@Preview
@Composable
internal fun IconInspectorPanelDetachedPreview() {
  PreviewHost(900.dp, 640.dp) { IconInspectorPanel(it, onDock = {}) }
}

@Preview
@Composable
internal fun IconInspectorEmptyStatePreview() {
  PreviewHost(500.dp, 300.dp) { EmptyState(it) }
}

@Composable
private fun PreviewHost(width: Dp, height: Dp, content: @Composable (FakeIconInspectorController) -> Unit) {
  val controller = remember { FakeIconInspectorController() }
  DisposableEffect(controller) { onDispose { controller.dispose() } }
  Box(Modifier.size(width, height)) { content(controller) }
}
