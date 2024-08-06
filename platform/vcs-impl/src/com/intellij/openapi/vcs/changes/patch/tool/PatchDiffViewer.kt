// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch.tool

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffViewerEx
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.actions.impl.SetEditorSettingsAction
import com.intellij.diff.tools.holders.EditorHolder
import com.intellij.diff.tools.holders.TextEditorHolder
import com.intellij.diff.tools.util.DiffDataKeys
import com.intellij.diff.tools.util.PrevNextDifferenceIterable
import com.intellij.diff.tools.util.PrevNextDifferenceIterableBase
import com.intellij.diff.tools.util.SimpleDiffPanel
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.tools.util.side.OnesideContentPanel
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.LineNumberConverterAdapter
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder.Companion.computeInnerDifferences
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder.Hunk
import com.intellij.util.concurrency.annotations.RequiresEdt
import it.unimi.dsi.fastutil.ints.IntConsumer
import javax.swing.JComponent

internal class PatchDiffViewer(
  private val diffContext: DiffContext,
  private val diffRequest: PatchDiffRequest,
) : DiffViewerEx, EditorDiffViewer {
  private val project: Project? = diffContext.getProject()

  private val panel: SimpleDiffPanel
  private val editor: EditorEx
  private val editorHolder: EditorHolder

  private val prevNextDifferenceIterable: MyPrevNextDifferenceIterable
  private val editorSettingsAction: SetEditorSettingsAction

  private var hunks: List<Hunk> = mutableListOf()

  init {
    val document = EditorFactory.getInstance().createDocument("")
    editor = DiffUtil.createEditor(document, project, true, true)
    DiffUtil.setEditorCodeStyle(project, editor, null)

    editorHolder = TextEditorHolder(project, editor)

    val titles = DiffUtil.createPatchTextTitles(this,
                                                diffRequest,
                                                listOf(diffRequest.contentTitle1, diffRequest.contentTitle2))
      .filterNotNull()
    val titlePanel = if (titles.isNotEmpty()) DiffUtil.createStackedComponents(titles, DiffUtil.TITLE_GAP) else null

    val contentPanel = OnesideContentPanel.createFromHolder(editorHolder)
    contentPanel.setTitle(titlePanel)

    prevNextDifferenceIterable = MyPrevNextDifferenceIterable()

    panel = object : SimpleDiffPanel(contentPanel, diffContext) {
      override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink[CommonDataKeys.PROJECT] = project
        sink[DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE] = prevNextDifferenceIterable
        sink[DiffDataKeys.CURRENT_EDITOR] = editor
        sink[DiffDataKeys.CURRENT_CHANGE_RANGE] = prevNextDifferenceIterable.currentLineRange
      }
    }
    editorSettingsAction = SetEditorSettingsAction(TextDiffViewerUtil.getTextSettings(diffContext), editors)
    editorSettingsAction.applyDefaults()
  }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusedComponent(): JComponent = editor.getContentComponent()

  override fun getEditors(): List<Editor?> = listOf(editor)

  override fun getDifferenceIterable(): PrevNextDifferenceIterable = prevNextDifferenceIterable

  override fun init(): FrameDiffTool.ToolbarComponents {
    panel.setPersistentNotifications(DiffUtil.createCustomNotifications(this, diffContext, diffRequest))
    onInit()

    val toolbarComponents = FrameDiffTool.ToolbarComponents()
    toolbarComponents.toolbarActions = createToolbarActions()
    return toolbarComponents
  }

  override fun dispose() {
    Disposer.dispose(editorHolder)
  }

  private fun onInit() {
    val state = PatchChangeBuilder().build(diffRequest.patch.hunks)
    hunks = state.hunks

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

  @RequiresEdt
  private fun createToolbarActions(): List<AnAction> {
    val group = mutableListOf<AnAction>()
    group.add(editorSettingsAction)
    group.add(Separator.getInstance())
    group.add(ActionManager.getInstance().getAction(IdeActions.DIFF_VIEWER_TOOLBAR))
    return group
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
