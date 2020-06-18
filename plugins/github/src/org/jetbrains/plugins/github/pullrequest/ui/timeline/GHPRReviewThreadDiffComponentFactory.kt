// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.pullrequest.ui.timeline

import com.intellij.diff.util.DiffDrawUtil
import com.intellij.openapi.diff.impl.patch.PatchHunk
import com.intellij.openapi.diff.impl.patch.PatchLine
import com.intellij.openapi.diff.impl.patch.PatchReader
import com.intellij.openapi.diff.impl.patch.apply.GenericPatchApplier
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.LineNumberConverter
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.LineNumberConverterAdapter
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.patch.AppliedTextPatch
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder
import com.intellij.ui.ClickListener
import com.intellij.ui.components.panels.HorizontalLayout
import com.intellij.ui.components.panels.NonOpaquePanel
import com.intellij.util.PathUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UI
import com.intellij.util.ui.UIUtil
import org.jetbrains.plugins.github.util.GHPatchHunkUtil
import java.awt.Cursor
import java.awt.event.MouseEvent
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.SwingConstants

class GHPRReviewThreadDiffComponentFactory(private val fileTypeRegistry: FileTypeRegistry,
                                           private val project: Project,
                                           private val editorFactory: EditorFactory,
                                           private val selectInToolWindowHelper: GHPRSelectInToolWindowHelper) {

  fun createComponent(filePath: String, diffHunk: String, commit: String?): JComponent = JBUI.Panels
    .simplePanel(createDiff(filePath, diffHunk))
    .addToTop(createFileName(filePath, commit))
    .andTransparent()

  private fun createFileName(filePath: String, commit: String?): JComponent {
    val name = PathUtil.getFileName(filePath)
    val path = PathUtil.getParentPath(filePath)
    val fileType = fileTypeRegistry.getFileTypeByFileName(name)

    return NonOpaquePanel(HorizontalLayout(UI.scale(5))).apply {
      add(JLabel(name, fileType.icon, SwingConstants.LEFT).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        object : ClickListener() {
          override fun onClick(event: MouseEvent, clickCount: Int): Boolean {
            selectInToolWindowHelper.selectChange(commit, filePath)
            return true
          }
        }.installOn(this)
      })

      if (!path.isBlank())
        add(JLabel(path).apply {
          foreground = UIUtil.getContextHelpForeground()
        })
    }
  }

  private fun createDiff(filePath: String, diffHunk: String): JComponent {
    try {
      val patchReader = PatchReader(GHPatchHunkUtil.createPatchFromHunk(filePath, diffHunk))
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
          editor.gutter.apply {
            setLineNumberConverter(LineNumberConverterAdapter(builder.lineConvertor1.createConvertor()),
                                   LineNumberConverterAdapter(builder.lineConvertor2.createConvertor()))
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
          editor.gutter.apply {
            setLineNumberConverter(
              LineNumberConverter.Increasing { _, line -> line + patchHunk.startLineBefore },
              LineNumberConverter.Increasing { _, line -> line + patchHunk.startLineAfter }
            )
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
      setHorizontalScrollbarVisible(true)
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
  }
}
