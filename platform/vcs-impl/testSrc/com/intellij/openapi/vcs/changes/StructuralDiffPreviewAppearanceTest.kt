// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffContext
import com.intellij.diff.contents.DocumentContent
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.util.DiffGutterRenderer
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
      assertTrue(Side.entries.any { hasArrows(viewer.getEditor(it)) })
    }
    withViewer(::UnifiedDiffViewer, commitTab = true) { viewer ->
      assertDefaultDiff(viewer.editor)
    }
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `a diff outside the Commit diff tab keeps its diff`(): Unit = timeoutRunBlocking {
    withViewer(::SimpleDiffViewer, commitTab = false) { viewer ->
      for (side in Side.entries) assertDefaultDiff(viewer.getEditor(side))
      assertTrue(Side.entries.any { hasArrows(viewer.getEditor(it)) })
    }
    withViewer(::UnifiedDiffViewer, commitTab = false) { viewer ->
      assertDefaultDiff(viewer.editor)
    }
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `the side-by-side Commit diff tab shows a gutter without icons and a blue change on both sides`(): Unit = timeoutRunBlocking {
    withViewer(::SimpleDiffViewer, commitTab = true) { viewer ->
      for (side in Side.entries) {
        val editor = viewer.getEditor(side)
        assertIconFreeGutter(editor)
        assertLineColor(editor, DiffColors.DIFF_DELETED, Hue.RED)
        assertLineColor(editor, DiffColors.DIFF_INSERTED, Hue.GREEN)
        assertLineColor(editor, DiffColors.DIFF_MODIFIED, Hue.BLUE)
      }
    }
  }

  @Test
  @RegistryKey(key = StructuralChangesViewSettings.REGISTRY_KEY, value = "true")
  fun `the unified Commit diff tab shows a gutter without icons and both columns of line numbers`(): Unit = timeoutRunBlocking {
    withViewer(::UnifiedDiffViewer, commitTab = true) { viewer ->
      val editor = viewer.editor
      assertIconFreeGutter(editor)
      assertNotNull(editor.gutterComponentEx.additionalLineNumberConverter)
      assertLineColor(editor, DiffColors.DIFF_DELETED, Hue.RED)
      assertLineColor(editor, DiffColors.DIFF_INSERTED, Hue.GREEN)
      assertLineColor(editor, DiffColors.DIFF_MODIFIED, Hue.BLUE)
    }
  }

  private suspend fun <V : DiffViewerBase> withViewer(create: (DiffContext, DiffRequest) -> V, commitTab: Boolean, test: (V) -> Unit) {
    val project = project
    withContext(Dispatchers.EDT) {
      val contents = DiffContentFactory.getInstance()
      val request = SimpleDiffRequest(null, contents.create(project, "first\nold\nlast"), contents.create(project, "first\nnew\nlast"), null, null)
      // An editable right side gets the arrows that apply a change.
      (request.contents[1] as DocumentContent).document.setReadOnly(false)
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
    assertTrue(editor.settings.areGutterIconsShown())
    assertTrue(editor.settings.isLineNumbersShown)
    val scheme = EditorColorsManager.getInstance().globalScheme
    for (key in listOf(DiffColors.DIFF_DELETED, DiffColors.DIFF_INSERTED, DiffColors.DIFF_MODIFIED)) {
      assertEquals(scheme.getAttributes(key), editor.colorsScheme.getAttributes(key), key.externalName)
    }
  }

  /** The gutter keeps the line marker area, because only that area paints the colors of the changes. */
  private fun assertIconFreeGutter(editor: EditorEx) {
    assertTrue(editor.settings.isLineMarkerAreaShown)
    assertFalse(editor.settings.areGutterIconsShown())
    assertFalse(editor.settings.isFoldingOutlineShown)
    assertTrue(editor.settings.isLineNumbersShown)
    assertFalse(hasArrows(editor))
  }

  private fun hasArrows(editor: EditorEx): Boolean = editor.markupModel.allHighlighters.any { it.gutterIconRenderer is DiffGutterRenderer }

  /** The diff paints a line with changed words in the foreground color of the key, and the words in its background color. */
  private fun assertLineColor(editor: EditorEx, key: TextAttributesKey, hue: Hue) {
    val attributes = editor.colorsScheme.getAttributes(key)
    for (color in listOf(attributes.foregroundColor, attributes.backgroundColor)) {
      val channels = listOf(color.red, color.green, color.blue)
      assertEquals(hue.ordinal, channels.indexOf(channels.max()), "${key.externalName} is ${hue.name.lowercase()}: $color")
    }
  }

  /** The strongest channel of a color. */
  private enum class Hue { RED, GREEN, BLUE }

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
