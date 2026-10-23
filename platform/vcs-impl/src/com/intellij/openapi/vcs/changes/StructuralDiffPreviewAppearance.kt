// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffExtension
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.requests.DiffRequest
import com.intellij.diff.tools.fragmented.UnifiedDiffViewer
import com.intellij.diff.tools.util.DiffSplitter
import com.intellij.diff.tools.util.base.DiffViewerListener
import com.intellij.diff.tools.util.side.TwosideTextDiffViewer
import com.intellij.diff.util.DiffDividerDrawUtil
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.Side
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.EditorMarkupModel
import com.intellij.util.ui.UIUtil

/**
 * Gives the Commit diff tab of structural changes a quiet frame. The gutter shows line numbers only: the hunks in the tree have the
 * checkboxes and the rollback.
 */
internal class StructuralDiffPreviewAppearance : DiffExtension() {
  override fun onViewerCreated(viewer: FrameDiffTool.DiffViewer, context: DiffContext, request: DiffRequest) {
    val project = context.project ?: return
    if (context.getUserData(STRUCTURAL_PREVIEW_CONTEXT) != true || !StructuralChangesViewSettings.isEnabled(project)) return
    when (viewer) {
      is UnifiedDiffViewer -> quiet(viewer.editor)
      is TwosideTextDiffViewer -> {
        for (side in Side.entries) quiet(viewer.getEditor(side))
        viewer.addListener(object : DiffViewerListener() {
          override fun onInit() = hideDividerPolygons(viewer)
        })
      }
    }
  }

  /** Each side colors its own lines, so the divider shows no polygons between them. The viewer adds its divider at the init. */
  private fun hideDividerPolygons(viewer: TwosideTextDiffViewer) {
    UIUtil.findComponentOfType(viewer.component, DiffSplitter::class.java)?.setPainter { g, divider ->
      val graphics = DiffDividerDrawUtil.getDividerGraphics(g, divider, viewer.editor1.component)
      graphics.color = DiffDrawUtil.getDividerColor(viewer.editor1)
      graphics.fill(graphics.clipBounds)
      graphics.dispose()
    }
  }

  private fun quiet(editor: EditorEx) {
    editor.settings.isLineMarkerAreaShown = false
    editor.settings.isFoldingOutlineShown = false
    (editor.markupModel as? EditorMarkupModel)?.setTrafficLightIconVisible(false)
  }
}
