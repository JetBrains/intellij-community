// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.LineNumberConverter
import com.intellij.openapi.editor.impl.LineNumberConverterAdapter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder
import com.intellij.util.ui.codereview.timeline.TimelineDiffComponentFactory
import org.jetbrains.plugins.github.util.GHPatchHunkUtil
import javax.swing.JComponent

class GHPRReviewThreadDiffComponentFactory(project: Project, editorFactory: EditorFactory) {
  private val timelineDiffComponentFactory = TimelineDiffComponentFactory(project, editorFactory)

  fun createComponent(diffHunk: String, startLine: Int?): JComponent {
    try {
      val patchReader = PatchReader(GHPatchHunkUtil.createPatchFromHunk("_", diffHunk))
      val patchHunk = patchReader.readTextPatches().firstOrNull()?.hunks?.firstOrNull()?.let { truncateHunk(it, startLine != null) }
                      ?: throw IllegalStateException("Could not parse diff hunk")

      if (patchHunk.lines.any { it.type != PatchLine.Type.CONTEXT }) {
        val appliedSplitHunks = GenericPatchApplier.SplitHunk.read(patchHunk).map {
          AppliedTextPatch.AppliedSplitPatchHunk(it, -1, -1, AppliedTextPatch.HunkStatus.NOT_APPLIED)
        }

        val builder = PatchChangeBuilder()
        builder.exec(appliedSplitHunks)

        val patchContent = builder.patchContent.removeSuffix("\n")

        return timelineDiffComponentFactory.createDiffComponent(patchContent) { editor ->
          editor.gutter.apply {
            setLineNumberConverter(LineNumberConverterAdapter(builder.lineConvertor1.createConvertor()),
                                   LineNumberConverterAdapter(builder.lineConvertor2.createConvertor()))
          }

          val hunk = builder.hunks.first()
          DiffDrawUtil.createUnifiedChunkHighlighters(editor,
                                                      hunk.patchDeletionRange,
                                                      hunk.patchInsertionRange,
                                                      null)
        }
      }
      else {
        val patchContent = patchHunk.text.removeSuffix("\n")

        return timelineDiffComponentFactory.createDiffComponent(patchContent) { editor ->
          editor.gutter.apply {
            setLineNumberConverter(
              LineNumberConverter.Increasing { _, line -> line + patchHunk.startLineBefore },
              LineNumberConverter.Increasing { _, line -> line + patchHunk.startLineAfter }
            )
          }
        }
      }
    }
    catch (e: Exception) {
      throw IllegalStateException("Could not create diff", e)
    }
  }

  private fun truncateHunk(hunk: PatchHunk, isMultiline: Boolean): PatchHunk {
    val maxDiffSize = if (isMultiline) MULTILINE_DIFF_SIZE else SINGLE_LINE_DIFF_SIZE

    if (hunk.lines.size <= maxDiffSize) return hunk

    var startLineBefore: Int = hunk.startLineBefore
    var startLineAfter: Int = hunk.startLineAfter

    val toRemoveIdx = hunk.lines.lastIndex - maxDiffSize
    for (i in 0..toRemoveIdx) {
      val line = hunk.lines[i]
      when (line.type) {
        PatchLine.Type.CONTEXT -> {
          startLineBefore++
          startLineAfter++
        }
        PatchLine.Type.ADD -> startLineAfter++
        PatchLine.Type.REMOVE -> startLineBefore++
      }
    }
    val truncatedLines = hunk.lines.subList(toRemoveIdx + 1, hunk.lines.size)
    return PatchHunk(startLineBefore, hunk.endLineBefore, startLineAfter, hunk.endLineAfter).apply {
      for (line in truncatedLines) {
        addLine(line)
      }
    }
  }

  companion object {
    private const val SINGLE_LINE_DIFF_SIZE = 3
    private const val MULTILINE_DIFF_SIZE = 10
  }
}
