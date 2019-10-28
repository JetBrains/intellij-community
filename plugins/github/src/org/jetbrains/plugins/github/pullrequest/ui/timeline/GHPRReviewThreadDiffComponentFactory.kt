// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder
import com.intellij.ui.SimpleColoredComponent
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import javax.swing.JComponent

class GHPRReviewThreadDiffComponentFactory(private val fileTypeRegistry: FileTypeRegistry,
                                           private val project: Project,
                                           private val editorFactory: EditorFactory) {
  fun createComponent(filePath: String, diffHunk: String): JComponent = JBUI.Panels
    .simplePanel(createDiff(filePath, diffHunk))
    .addToTop(createFileName(filePath))
    .andTransparent()

  private fun createFileName(filePath: String): SimpleColoredComponent {
    val name = PathUtil.getFileName(filePath)
    val path = PathUtil.getParentPath(filePath)
    val fileType = fileTypeRegistry.getFileTypeByFileName(name)

    return SimpleColoredComponent().apply {
      isOpaque = false

      icon = fileType.icon
      append(name)
      if (!path.isBlank()) append(" ").append(path, SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
  }

  private fun createDiff(filePath: String, diffHunk: String): JComponent {
    try {
      val patchReader = PatchReader(createPatchFromHunk(filePath, diffHunk))
      val patchHunk = patchReader.readTextPatches().firstOrNull()?.hunks?.firstOrNull()?.let { truncateHunk(it) }
                      ?: throw IllegalStateException("Could not parse diff hunk")

      if (patchHunk.lines.find { it.type != PatchLine.Type.CONTEXT } != null) {
        val appliedSplitHunks = GenericPatchApplier.SplitHunk.read(patchHunk).map {
          AppliedTextPatch.AppliedSplitPatchHunk(it, -1, -1, AppliedTextPatch.HunkStatus.NOT_APPLIED)
        }

        val builder = PatchChangeBuilder()
        builder.exec(appliedSplitHunks)

        val patchContent = builder.patchContent.removeSuffix("\n")
        val document = editorFactory.createDocument(patchContent)

        return EditorHandlerPanel.create(editorFactory) {
          val editor = createEditor(document)
          editor.gutterComponentEx.apply {
            setLineNumberConvertor(builder.lineConvertor1.createConvertor(),
                                   builder.lineConvertor2.createConvertor())
          }

          val hunk = builder.hunks.first()
          DiffDrawUtil.createUnifiedChunkHighlighters(editor,
                                                      hunk.patchDeletionRange,
                                                      hunk.patchInsertionRange,
                                                      null)
          editor
        }
      }
      else {
        val patchContent = patchHunk.text.removeSuffix("\n")
        val document = editorFactory.createDocument(patchContent)

        return EditorHandlerPanel.create(editorFactory) {
          val editor = createEditor(document)
          editor.gutterComponentEx.apply {
            setLineNumberConvertor({ it + patchHunk.startLineBefore }, { it + patchHunk.startLineAfter })
          }
          editor
        }
      }
    }
    catch (e: Exception) {
      throw IllegalStateException("Could not create diff", e)
    }
  }

  private fun truncateHunk(hunk: PatchHunk): PatchHunk {
    if (hunk.lines.size <= DIFF_SIZE) return hunk

    var startLineBefore: Int = hunk.startLineBefore
    var startLineAfter: Int = hunk.startLineAfter

    val toRemoveIdx = hunk.lines.lastIndex - DIFF_SIZE
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

  private fun createEditor(document: Document): EditorEx {
    return (editorFactory.createViewer(document, project, EditorKind.DIFF) as EditorEx).apply {
      setHorizontalScrollbarVisible(false)
      setVerticalScrollbarVisible(false)
      setCaretEnabled(false)
      setBorder(null)
      settings.apply {
        isCaretRowShown = false
        additionalLinesCount = 0
        additionalColumnsCount = 0
        isRightMarginShown = false
        setRightMargin(-1)
        isFoldingOutlineShown = false
        isIndentGuidesShown = false
        isVirtualSpace = false
        isWheelFontChangeEnabled = false
        isAdditionalPageAtBottom = false
        lineCursorWidth = 1
      }

      gutterComponentEx.setPaintBackground(false)
    }
  }

  companion object {
    private const val DIFF_SIZE = 3

    private fun createPatchFromHunk(filePath: String, diffHunk: String): String {
      return """--- a/$filePath
+++ b/$filePath
""" + diffHunk
    }
  }
}
