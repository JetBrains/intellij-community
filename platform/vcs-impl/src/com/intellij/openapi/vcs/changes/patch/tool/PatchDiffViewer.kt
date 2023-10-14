// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch.tool

import com.intellij.diff.DiffContext
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.FrameDiffTool.DiffViewer
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.PrevNextDifferenceIterableBase
import com.intellij.diff.tools.util.SimpleDiffPanel
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.LineRange
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataProvider
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.LineNumberConverterAdapter
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder.Companion.computeInnerDifferences
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder.Hunk
import com.intellij.ui.components.panels.Wrapper
import it.unimi.dsi.fastutil.ints.IntConsumer
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import javax.swing.JComponent

internal class PatchDiffViewer(private val diffContext: DiffContext,
                               private val diffRequest: PatchDiffRequest) : DiffViewer, DataProvider, EditorDiffViewer {
  private val project: Project? = diffContext.getProject()

  private val panel: SimpleDiffPanel
  private val editor: EditorEx

  private val prevNextDifferenceIterable: MyPrevNextDifferenceIterable

  private val hunks: MutableList<Hunk> = ArrayList()

  init {
    val document = EditorFactory.getInstance().createDocument("")
    editor = DiffUtil.createEditor(document, project, true, true)

    val editorPanel = Wrapper(BorderLayout(0, DiffUtil.TITLE_GAP.get()), editor.getComponent())
    val panelTitle = diffRequest.panelTitle
    if (panelTitle != null) {
      editorPanel.add(DiffUtil.createTitle(panelTitle), BorderLayout.NORTH)
    }
    panel = SimpleDiffPanel(editorPanel, this, diffContext)

    prevNextDifferenceIterable = MyPrevNextDifferenceIterable()
  }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusedComponent(): JComponent = editor.getContentComponent()

  override fun getEditors(): List<Editor?> = listOf(editor)

  override fun init(): FrameDiffTool.ToolbarComponents {
    panel.setPersistentNotifications(DiffUtil.createCustomNotifications(this, diffContext, diffRequest))
    onInit()
    return FrameDiffTool.ToolbarComponents()
  }

  override fun dispose() {
    EditorFactory.getInstance().releaseEditor(editor)
  }

  private fun onInit() {
    val state = PatchChangeBuilder().build(diffRequest.patch.hunks)
    hunks.addAll(state.hunks)

    val patchDocument: Document = editor.getDocument()
    runWriteAction { patchDocument.setText(state.patchContent.toString()) }

    editor.getGutter().setLineNumberConverter(
      LineNumberConverterAdapter(state.lineConvertor1.createConvertor()),
      LineNumberConverterAdapter(state.lineConvertor2.createConvertor())
    )

    state.separatorLines.forEach(IntConsumer { line: Int ->
      val offset = patchDocument.getLineStartOffset(line)
      DiffDrawUtil.createLineSeparatorHighlighter(editor, offset, offset)
    })

    for (hunk in hunks) {
      val innerFragments = computeInnerDifferences(patchDocument, hunk)
      DiffDrawUtil.createUnifiedChunkHighlighters(editor, hunk.patchDeletionRange, hunk.patchInsertionRange, innerFragments)
    }
    editor.getGutterComponentEx().revalidateMarkup()
  }

  override fun getData(dataId: @NonNls String): Any? {
    if (CommonDataKeys.PROJECT.`is`(dataId)) return project
    if (DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE.`is`(dataId)) return prevNextDifferenceIterable
    if (DiffDataKeys.CURRENT_EDITOR.`is`(dataId)) return editor
    if (DiffDataKeys.CURRENT_CHANGE_RANGE.`is`(dataId)) return prevNextDifferenceIterable.currentLineRange
    return null
  }

  private inner class MyPrevNextDifferenceIterable : PrevNextDifferenceIterableBase<Hunk>() {
    override fun getChanges(): List<Hunk> = hunks
    override fun getEditor(): EditorEx = this@PatchDiffViewer.editor

    override fun getStartLine(change: Hunk): Int {
      return change.patchDeletionRange.start
    }

    override fun getEndLine(change: Hunk): Int {
      return change.patchInsertionRange.end
    }
  }
}
