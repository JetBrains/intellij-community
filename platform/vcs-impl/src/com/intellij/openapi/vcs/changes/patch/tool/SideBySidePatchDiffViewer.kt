// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.patch.tool

import com.intellij.diff.DiffContext
import com.intellij.diff.DiffViewerEx
import com.intellij.diff.EditorDiffViewer
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.actions.impl.FocusOppositePaneAction
import com.intellij.diff.actions.impl.SetEditorSettingsAction
import com.intellij.diff.comparison.ByWord
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.DiffTooBigException
import com.intellij.diff.tools.holders.EditorHolder
import com.intellij.diff.tools.holders.TextEditorHolder
import com.intellij.diff.tools.simple.AlignableChange
import com.intellij.diff.tools.simple.AlignedDiffModel
import com.intellij.diff.tools.simple.AlignedDiffModelBase
import com.intellij.diff.tools.util.*
import com.intellij.diff.tools.util.FocusTrackerSupport.Twoside
import com.intellij.diff.tools.util.SyncScrollSupport.TwosideSyncScrollSupport
import com.intellij.diff.tools.util.base.TextDiffSettingsHolder.TextDiffSettings
import com.intellij.diff.tools.util.base.TextDiffViewerUtil
import com.intellij.diff.tools.util.base.TextDiffViewerUtil.ToggleAutoScrollAction
import com.intellij.diff.tools.util.side.TwosideContentPanel
import com.intellij.diff.util.*
import com.intellij.diff.util.DiffDrawUtil.LineHighlighterBuilder
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.ScrollType
import com.intellij.openapi.editor.event.VisibleAreaEvent
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.LineNumberConverterAdapter
import com.intellij.openapi.progress.DumbProgressIndicator
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.patch.tool.PatchChangeBuilder.PatchSideChange
import com.intellij.ui.DirtyUI
import com.intellij.util.concurrency.annotations.RequiresEdt
import it.unimi.dsi.fastutil.ints.IntConsumer
import it.unimi.dsi.fastutil.ints.IntList
import java.awt.Graphics
import javax.swing.JComponent

internal class SideBySidePatchDiffViewer(
  private val diffContext: DiffContext,
  private val diffRequest: PatchDiffRequest,
) : DiffViewerEx, EditorDiffViewer {
  private val project: Project? = diffContext.getProject()

  private val panel: SimpleDiffPanel
  private val contentPanel: TwosideContentPanel

  private val editors: List<EditorEx>
  private val editorHolders: List<EditorHolder>

  private val prevNextDifferenceIterable: MyPrevNextDifferenceIterable
  private val focusTrackerSupport: FocusTrackerSupport<Side>
  private val editorSettingsAction: SetEditorSettingsAction
  private val syncScrollable: MySyncScrollable
  private val syncScrollSupport: TwosideSyncScrollSupport
  private val alignedDiffModel: AlignedDiffModel

  private var patchChanges: List<PatchSideChange> = mutableListOf()
  private var separatorLines1: IntList = IntList.of()
  private var separatorLines2: IntList = IntList.of()

  init {
    val document1 = EditorFactory.getInstance().createDocument("")
    val document2 = EditorFactory.getInstance().createDocument("")
    val editor1 = DiffUtil.createEditor(document1, project, true, true)
    val editor2 = DiffUtil.createEditor(document2, project, true, true)

    DiffUtil.setEditorCodeStyle(project, editor1, null)
    DiffUtil.setEditorCodeStyle(project, editor2, null)

    editor1.setVerticalScrollbarOrientation(EditorEx.VERTICAL_SCROLLBAR_LEFT)
    DiffUtil.disableBlitting(editor1)
    DiffUtil.disableBlitting(editor2)

    editors = listOf(editor1, editor2)
    editorHolders = editors.map { editor -> TextEditorHolder(project, editor) }

    prevNextDifferenceIterable = MyPrevNextDifferenceIterable()
    focusTrackerSupport = Twoside(editorHolders)

    val titles = DiffUtil.createPatchTextTitles(this,
                                                diffRequest,
                                                listOf(diffRequest.contentTitle1, diffRequest.contentTitle2))

    contentPanel = TwosideContentPanel.createFromHolders(editorHolders)
    contentPanel.setTitles(titles)
    contentPanel.setPainter(MyDividerPainter())

    panel = object : SimpleDiffPanel(contentPanel, diffContext) {
      override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        sink[CommonDataKeys.PROJECT] = project
        sink[DiffDataKeys.PREV_NEXT_DIFFERENCE_ITERABLE] = prevNextDifferenceIterable
        sink[DiffDataKeys.CURRENT_EDITOR] = currentEditor
        sink[DiffDataKeys.CURRENT_CHANGE_RANGE] = prevNextDifferenceIterable
          .getCurrentLineRangeByLine(currentEditor.getCaretModel().logicalPosition.line)
      }
    }

    syncScrollable = MySyncScrollable()
    syncScrollSupport = TwosideSyncScrollSupport(editors, syncScrollable)
    val visibleAreaListener = MyVisibleAreaListener()
    editor1.scrollingModel.addVisibleAreaListener(visibleAreaListener)
    editor2.scrollingModel.addVisibleAreaListener(visibleAreaListener)

    alignedDiffModel = PatchAlignedDiffModel()

    MyFocusOppositePaneAction(true).install(panel)
    MyFocusOppositePaneAction(false).install(panel)

    editorSettingsAction = SetEditorSettingsAction(textSettings, editors)
    editorSettingsAction.setSyncScrollSupport(syncScrollSupport)
    editorSettingsAction.applyDefaults()
  }

  override fun getComponent(): JComponent = panel

  override fun getPreferredFocusedComponent(): JComponent? {
    return currentSide.select(editorHolders).getPreferredFocusedComponent()
  }

  override fun getCurrentEditor(): EditorEx = currentSide.select(editors)

  override fun getEditors(): List<Editor> = editors

  override fun getDifferenceIterable(): PrevNextDifferenceIterable = prevNextDifferenceIterable

  val editor1: EditorEx get() = editors[0]
  val editor2: EditorEx get() = editors[1]

  private val currentSide: Side get() = focusTrackerSupport.getCurrentSide()
  val textSettings: TextDiffSettings get() = TextDiffViewerUtil.getTextSettings(diffContext)

  override fun init(): FrameDiffTool.ToolbarComponents {
    panel.setPersistentNotifications(DiffUtil.createCustomNotifications(this, diffContext, diffRequest))
    onInit()

    val toolbarComponents = FrameDiffTool.ToolbarComponents()
    toolbarComponents.toolbarActions = createToolbarActions()
    return toolbarComponents
  }

  override fun dispose() {
    for (holder in editorHolders) {
      Disposer.dispose(holder)
    }
    Disposer.dispose(alignedDiffModel)
  }

  private fun onInit() {
    val state = SideBySidePatchChangeBuilder().build(diffRequest.patch.hunks)
    patchChanges = state.changes
    separatorLines1 = state.separatorLines1
    separatorLines2 = state.separatorLines2

    val document1 = editor1.document
    val document2 = editor2.document
    runWriteAction { document1.setText(state.patchContent1.toString()) }
    runWriteAction { document2.setText(state.patchContent2.toString()) }

    editor1.getGutter().setLineNumberConverter(
      LineNumberConverterAdapter(state.lineConvertor1.createConvertor())
    )
    editor2.getGutter().setLineNumberConverter(
      LineNumberConverterAdapter(state.lineConvertor2.createConvertor())
    )

    state.separatorLines1.forEach(IntConsumer { line: Int ->
      val offset = document1.getLineStartOffset(line)
      DiffDrawUtil.createLineSeparatorHighlighter(editor1, offset, offset)
    })
    state.separatorLines2.forEach(IntConsumer { line: Int ->
      val offset = document2.getLineStartOffset(line)
      DiffDrawUtil.createLineSeparatorHighlighter(editor2, offset, offset)
    })

    val alignedSides = alignedDiffModel.needAlignChanges()
    for (change in state.changes) {
      highlightChange(change, alignedSides)
    }

    alignedDiffModel.realignChanges()

    editor1.getGutterComponentEx().revalidateMarkup()
    editor2.getGutterComponentEx().revalidateMarkup()
  }

  private fun highlightChange(change: PatchSideChange, alignedSides: Boolean) {
    val document1 = editor1.document
    val document2 = editor2.document

    val range = change.range
    val diffType = DiffUtil.getDiffType(range)

    val innerFragments = try {
      val deleted = DiffUtil.getLinesContent(document1, range.start1, range.end1)
      val inserted = DiffUtil.getLinesContent(document2, range.start2, range.end2)
      ByWord.compare(deleted, inserted, ComparisonPolicy.DEFAULT, DumbProgressIndicator.INSTANCE)
    }
    catch (e: DiffTooBigException) {
      null
    }

    LineHighlighterBuilder(editor1, range.start1, range.end1, diffType)
      .withIgnored(innerFragments != null)
      .withAlignedSides(alignedSides)
      .done()
    LineHighlighterBuilder(editor2, range.start2, range.end2, diffType)
      .withIgnored(innerFragments != null)
      .withAlignedSides(alignedSides)
      .done()

    if (innerFragments != null) {
      for (fragment in innerFragments) {
        val innerDiffType = DiffUtil.getDiffType(fragment)
        val startOffset1 = document1.getLineStartOffset(range.start1)
        val startOffset2 = document2.getLineStartOffset(range.start2)

        DiffDrawUtil.createInlineHighlighter(editor1,
                                             startOffset1 + fragment.startOffset1,
                                             startOffset1 + fragment.endOffset1,
                                             innerDiffType)
        DiffDrawUtil.createInlineHighlighter(editor2,
                                             startOffset2 + fragment.startOffset2,
                                             startOffset2 + fragment.endOffset2,
                                             innerDiffType)

      }
    }
  }

  @RequiresEdt
  private fun createToolbarActions(): List<AnAction> {
    val group = mutableListOf<AnAction>()
    group.add(ToggleAutoScrollAction(textSettings))
    group.add(editorSettingsAction)
    group.add(Separator.getInstance())
    group.add(ActionManager.getInstance().getAction(IdeActions.DIFF_VIEWER_TOOLBAR))
    return group
  }

  private inner class MyPrevNextDifferenceIterable : PrevNextDifferenceIterableBase<PatchSideChange>() {
    override fun getChanges(): List<PatchSideChange> = patchChanges

    override fun getEditor(): EditorEx = currentEditor

    override fun getStartLine(change: PatchSideChange): Int {
      return currentSide.select(change.range.start1, change.range.start2)
    }

    override fun getEndLine(change: PatchSideChange): Int {
      return currentSide.select(change.range.end1, change.range.end2)
    }
  }

  private inner class MySyncScrollable : BaseSyncScrollable() {
    override fun isSyncScrollEnabled(): Boolean {
      return textSettings.isEnableSyncScroll ||
             textSettings.isEnableAligningChangesMode
    }

    override fun forceSyncVerticalScroll(): Boolean {
      return alignedDiffModel.needAlignChanges()
    }

    override fun processHelper(helper: ScrollHelper) {
      if (!helper.process(0, 0)) return
      for (change in patchChanges) {
        if (!helper.process(change.range.start1, change.range.start2)) return
        if (!helper.process(change.range.end1, change.range.end2)) return
      }
      helper.process(DiffUtil.getLineCount(editor1.getDocument()),
                     DiffUtil.getLineCount(editor2.getDocument()))
    }
  }

  private inner class MyDividerPainter : DiffSplitter.Painter,
                                         DiffDividerDrawUtil.DividerPaintable,
                                         DiffDividerDrawUtil.DividerSeparatorPaintable {
    @DirtyUI
    override fun paint(g: Graphics, divider: JComponent) {
      val gg = DiffDividerDrawUtil.getDividerGraphics(g, divider, editor1.getComponent())
      gg.color = DiffDrawUtil.getDividerColor(editor1)
      gg.fill(gg.clipBounds)
      DiffDividerDrawUtil.paintPolygons(gg, divider.width, editor1, editor2, this)
      DiffDividerDrawUtil.paintSeparators(gg, divider.width, editor1, editor2, this)
      gg.dispose()
    }

    override fun process(handler: DiffDividerDrawUtil.DividerPaintable.Handler) {
      for (change in patchChanges) {
        if (!drawDivider(change, handler)) return
      }
    }

    private fun drawDivider(change: PatchSideChange, handler: DiffDividerDrawUtil.DividerPaintable.Handler): Boolean {
      val range = change.range
      if (alignedDiffModel.needAlignChanges()) {
        return handler.processAligned(range.start1, range.end1, range.start2, range.end2, change.diffType)
      }
      else {
        return handler.process(range.start1, range.end1, range.start2, range.end2, change.diffType)
      }
    }

    override fun process(handler: DiffDividerDrawUtil.DividerSeparatorPaintable.Handler) {
      for (i in 0 until separatorLines1.size) {
        if (!handler.process(separatorLines1.getInt(i), separatorLines2.getInt(i), false)) break
      }
    }
  }

  private inner class PatchAlignedDiffModel : AlignedDiffModelBase(diffRequest, diffContext, component, editor1, editor2, syncScrollable) {
    override fun getDiffChanges(): List<AlignableChange> = patchChanges
  }

  private inner class MyFocusOppositePaneAction(scrollToPosition: Boolean) : FocusOppositePaneAction(scrollToPosition) {
    override fun actionPerformed(e: AnActionEvent) {
      val currentSide: Side = currentSide
      val targetSide = currentSide.other()

      val currentEditor: EditorEx = currentSide.select(editors)
      val targetEditor: EditorEx = targetSide.select(editors)

      if (myScrollToPosition) {
        val position: LineCol = transferPosition(currentSide, LineCol.fromCaret(currentEditor))
        targetEditor.getCaretModel().moveToOffset(position.toOffset(targetEditor))
      }

      focusTrackerSupport.setCurrentSide(targetSide)
      targetEditor.getScrollingModel().scrollToCaret(ScrollType.MAKE_VISIBLE)

      DiffUtil.requestFocus(project, preferredFocusedComponent)
    }

    fun transferPosition(baseSide: Side, position: LineCol): LineCol {
      val line: Int = syncScrollSupport.getScrollable().transfer(baseSide, position.line)
      return LineCol(line, position.column)
    }
  }

  private inner class MyVisibleAreaListener : VisibleAreaListener {
    override fun visibleAreaChanged(e: VisibleAreaEvent) {
      syncScrollSupport.visibleAreaChanged(e)
      contentPanel.repaint()
    }
  }
}
