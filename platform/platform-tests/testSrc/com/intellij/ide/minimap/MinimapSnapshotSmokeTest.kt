// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.geometry.MinimapGeometryCalculator
import com.intellij.ide.minimap.geometry.MinimapScaleData
import com.intellij.ide.minimap.layout.MinimapLayoutCalculator
import com.intellij.ide.minimap.model.MinimapModel
import com.intellij.ide.minimap.scene.MinimapSceneBuilder
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.impl.AbstractEditorTest
import com.intellij.openapi.util.Disposer

/**
 * End-to-end smoke test of the minimap render pipeline against a real editor (document, syntax highlighter, folding model).
 */
class MinimapSnapshotSmokeTest : AbstractEditorTest() {

  private fun buildSnapshot(): MinimapSnapshot {
    val editor = editor
    val model = MinimapModel(editor)
    Disposer.register(testRootDisposable, model)
    val sceneBuilder = MinimapSceneBuilder(editor, model, MinimapLayoutCalculator(editor), MinimapGeometryCalculator(editor))
    return WriteIntentReadAction.compute {
      sceneBuilder.buildSnapshot(
        panelWidth = 120,
        panelHeight = 600,
        scaleData = MinimapScaleData(width = 120, fitToHeight = false),
        scaleMode = MinimapScaleMode.FILL,
      )
    }
  }

  fun testSnapshotHasContentForNonEmptyFile() {
    initText((0 until 30).joinToString("\n") { "value$it = compute($it)" })
    setEditorVisibleSize(80, 20)

    val snapshot = buildSnapshot()

    assertTrue("minimap should have a non-degenerate height", snapshot.geometry.minimapHeight > 0)
    assertFalse("minimap should render token rectangles for a non-empty file", snapshot.tokenEntries.isEmpty())
    assertNotNull("layout metrics should be computed for a non-empty file", snapshot.layoutMetrics)
  }

  fun testSnapshotIsGracefulForEmptyFile() {
    initText("")
    setEditorVisibleSize(80, 20)

    val snapshot = buildSnapshot()

    assertTrue(snapshot.geometry.minimapHeight >= 0)
    assertTrue("an empty document has nothing to render", snapshot.tokenEntries.isEmpty())
  }
}
