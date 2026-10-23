// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContext
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.util.Side
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diff.DiffColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.RegistryKey
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.fixture.projectFixture
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

/** The diff of the Commit diff tab changes only while structural changes are on. The diff changes the middle of three lines. */
@TestApplication
internal class StructuralDiffPreviewAppearanceTest {
  private val project get() = projectFixture.get()

  @BeforeEach
  fun setUp() {
    PropertiesComponent.getInstance(project).setValue(OPTION_ID, true)
  }

  @AfterEach
  fun tearDown() {
    PropertiesComponent.getInstance(project).unsetValue(OPTION_ID)
  }

  @Test
  fun `the Commit diff tab keeps its diff while the registry key is off`(): Unit = timeoutRunBlocking {
    withViewer(::SimpleDiffViewer, commitTab = true) { viewer ->
      for (side in Side.entries) assertDefaultDiff(viewer.getEditor(side))
    }
    withViewer(::UnifiedDiffViewer, commitTab = true) { viewer ->
      assertDefaultDiff(viewer.editor)
      assertNotNull(viewer.editor.gutterComponentEx.additionalLineNumberConverter)
    }
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `a diff outside the Commit diff tab keeps its diff`(): Unit = timeoutRunBlocking {
    withViewer(::SimpleDiffViewer, commitTab = false) { viewer ->
      for (side in Side.entries) assertDefaultDiff(viewer.getEditor(side))
    }
    withViewer(::UnifiedDiffViewer, commitTab = false) { viewer ->
      assertDefaultDiff(viewer.editor)
      assertNotNull(viewer.editor.gutterComponentEx.additionalLineNumberConverter)
    }
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `the side-by-side Commit diff tab shows only line numbers in the gutter and a color for each side`(): Unit = timeoutRunBlocking {
    withViewer(::SimpleDiffViewer, commitTab = true) { viewer ->
      for (side in Side.entries) {
        val editor = viewer.getEditor(side)
        assertNumbersOnlyGutter(editor)
        assertLineColor(editor, DiffColors.DIFF_DELETED, removed = true)
        assertLineColor(editor, DiffColors.DIFF_INSERTED, removed = false)
      }
      assertLineColor(viewer.getEditor(Side.LEFT), DiffColors.DIFF_MODIFIED, removed = true)
      assertLineColor(viewer.getEditor(Side.RIGHT), DiffColors.DIFF_MODIFIED, removed = false)
    }
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `the unified Commit diff tab numbers only the current lines`(): Unit = timeoutRunBlocking {
    withViewer(::UnifiedDiffViewer, commitTab = true) { viewer ->
      val editor = viewer.editor
      assertNumbersOnlyGutter(editor)
      assertLineColor(editor, DiffColors.DIFF_DELETED, removed = true)
      assertLineColor(editor, DiffColors.DIFF_INSERTED, removed = false)

      // The unified text has the first line, the removed line, the added line, the last line, and an empty line.
      val gutter = editor.gutterComponentEx
      assertNull(gutter.additionalLineNumberConverter)
      val numbers = (1..editor.document.lineCount).map { gutter.primaryLineNumberConverter.convert(editor, it) }
      assertEquals(listOf(1, null, 2, 3, null), numbers)
    }
  }

  private suspend fun <V : DiffViewerBase> withViewer(create: (DiffContext, DiffRequest) -> V, commitTab: Boolean, test: (V) -> Unit) {
    val project = project
    withContext(Dispatchers.EDT) {
      val contents = DiffContentFactory.getInstance()
      val request = SimpleDiffRequest(null, contents.create(project, "first\nold\nlast"), contents.create(project, "first\nnew\nlast"), null, null)
      val context = TestDiffContext(project)
      if (commitTab) context.putUserData(STRUCTURAL_PREVIEW_CONTEXT, true)
      val viewer = create(context, request)
      try {
        // The diff request processor calls the extensions before the init of the viewer.
        StructuralDiffPreviewAppearance().onViewerCreated(viewer, context, request)
        viewer.init()
        PlatformTestUtil.waitWithEventsDispatching("The rediff did not end", { !viewer.hasPendingRediff() }, 10)
        test(viewer)
      }
      finally {
        Disposer.dispose(viewer)
      }
    }
  }

  private fun assertDefaultDiff(editor: EditorEx) {
    assertTrue(editor.settings.isLineMarkerAreaShown)
    val scheme = EditorColorsManager.getInstance().globalScheme
    for (key in listOf(DiffColors.DIFF_DELETED, DiffColors.DIFF_INSERTED, DiffColors.DIFF_MODIFIED)) {
      assertEquals(scheme.getAttributes(key), editor.colorsScheme.getAttributes(key), key.externalName)
    }
  }

  private fun assertNumbersOnlyGutter(editor: EditorEx) {
    assertFalse(editor.settings.isLineMarkerAreaShown)
    assertFalse(editor.settings.isFoldingOutlineShown)
  }

  /** The diff paints a line with changed words in the foreground color of the key, and the words in its background color. */
  private fun assertLineColor(editor: EditorEx, key: TextAttributesKey, removed: Boolean) {
    val attributes = editor.colorsScheme.getAttributes(key)
    for (color in listOf(attributes.foregroundColor, attributes.backgroundColor)) {
      if (removed) assertTrue(color.red > color.green, "${key.externalName} is red: $color")
      else assertTrue(color.green > color.red, "${key.externalName} is green: $color")
    }
  }

  private class TestDiffContext(private val project: Project) : DiffContext() {
    override fun getProject(): Project = project
    override fun isWindowFocused(): Boolean = true
    override fun isFocusedInWindow(): Boolean = true
    override fun requestFocusInWindow() {}
  }

  private companion object {
    const val OPTION_ID = "ChangesView.ShowStructuralChanges"
    val projectFixture = projectFixture()
  }
}
