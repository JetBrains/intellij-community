// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.minimap

import com.intellij.ide.minimap.geometry.MinimapGeometryCalculator
import com.intellij.ide.minimap.geometry.MinimapScaleData
import com.intellij.ide.minimap.layout.MinimapLayoutCalculator
import com.intellij.ide.minimap.model.MinimapModel
import com.intellij.ide.minimap.model.MinimapStructureMarker
import com.intellij.ide.minimap.render.MinimapRenderEntry
import com.intellij.ide.minimap.scene.MinimapSceneBuilder
import com.intellij.ide.minimap.scene.MinimapSnapshot
import com.intellij.ide.minimap.settings.MinimapScaleMode
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.openapi.application.WriteIntentReadAction
import com.intellij.openapi.editor.impl.editorModelFixture
import com.intellij.openapi.util.Disposer
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * End-to-end smoke test of the minimap render pipeline against a real editor (document, syntax highlighter, folding model).
 */
@TestApplication
class MinimapSnapshotSmokeTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val fixture by editorModelFixture(codeInsightModuleFixture(projectFixture))

  private fun buildSnapshot(): MinimapSnapshot {
    val editor = fixture.editor
    val model = MinimapModel(editor)
    Disposer.register(fixture.testRootDisposable, model)
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

  @Test
  fun testSnapshotHasContentForNonEmptyFile(): Unit = fixture.runTest {
    initText((0 until 30).joinToString("\n") { "value$it = compute($it)" })
    setEditorVisibleSize(80, 20)

    val snapshot = buildSnapshot()

    assertTrue(snapshot.geometry.minimapHeight > 0, "minimap should have a non-degenerate height")
    assertFalse(snapshot.tokenEntries.isEmpty(), "minimap should render token rectangles for a non-empty file")
    assertNotNull(snapshot.layoutMetrics, "layout metrics should be computed for a non-empty file")
  }

  @Test
  fun testSnapshotIsGracefulForEmptyFile(): Unit = fixture.runTest {
    initText("")
    setEditorVisibleSize(80, 20)

    val snapshot = buildSnapshot()

    assertTrue(snapshot.geometry.minimapHeight >= 0)
    assertTrue(snapshot.tokenEntries.isEmpty(), "an empty document has nothing to render")
  }

  @Test
  fun testStructureEntriesDoNotRetainStructureElementsDirectly(): Unit = fixture.runTest {
    assertHasNoStructureElementField(MinimapStructureMarker::class.java)
    assertHasNoStructureElementField(MinimapRenderEntry::class.java)
  }

  private fun assertHasNoStructureElementField(clazz: Class<*>) {
    val retainingField = clazz.declaredFields.firstOrNull { StructureViewTreeElement::class.java.isAssignableFrom(it.type) }
    assertNull(retainingField, "${clazz.simpleName} must not retain a structure element directly")
  }
}
