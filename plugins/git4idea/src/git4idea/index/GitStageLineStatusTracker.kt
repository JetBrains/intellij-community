// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.diff.DiffApplicationSettings
import com.intellij.diff.DiffContentFactory
import com.intellij.diff.DiffManager
import com.intellij.diff.comparison.ByWord
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.diff.comparison.IndicatorCancellationChecker
import com.intellij.diff.contents.DiffContent
import com.intellij.diff.fragments.DiffFragment
import com.intellij.diff.requests.SimpleDiffRequest
import com.intellij.diff.util.*
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.util.BackgroundTaskUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.VerticalSeparatorComponent
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vcs.ex.Range
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorTextField
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.labels.LinkLabel
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.PeekableIteratorWrapper
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.UIUtil
import git4idea.GitUtil
import git4idea.i18n.GitBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.*
import java.util.*
import javax.swing.*
import kotlin.math.max

class GitStageLineStatusTracker(
  override val project: Project,
  override val virtualFile: VirtualFile,
  override val document: Document
) : LocalLineStatusTracker<StagedRange> {
  override val vcsDocument: Document = LineStatusTrackerBase.createVcsDocument(document)
  var stagedDocument: Document = LineStatusTrackerBase.createVcsDocument(document)
    private set

  override val disposable: Disposable = Disposer.newDisposable()
  private val LOCK: DocumentTracker.Lock = DocumentTracker.Lock()

  private val blockOperations: LineStatusTrackerBlockOperations<StagedRange, StagedRange> = MyBlockOperations(LOCK)
  private val stagedBlockOperations: LineStatusTrackerBlockOperations<Range, BlockI> = MyStagedBlockOperations(LOCK)
  private val stagedTracker: DocumentTracker
  private val unstagedTracker: DocumentTracker

  override var isReleased: Boolean = false
    private set

  protected var isInitialized: Boolean = false
    private set

  private val renderer = MyLineStatusMarkerPopupRenderer(this)

  // FIXME
  override var mode: LocalLineStatusTracker.Mode = LocalLineStatusTracker.Mode(true, true, false)

  init {
    stagedTracker = DocumentTracker(vcsDocument, stagedDocument, LOCK)
    Disposer.register(disposable, stagedTracker)

    unstagedTracker = DocumentTracker(stagedDocument, document, LOCK)
    Disposer.register(disposable, unstagedTracker)

    stagedTracker.addHandler(MyDocumentTrackerHandler(false))
    unstagedTracker.addHandler(MyDocumentTrackerHandler(true))
  }


  @RequiresEdt
  fun setBaseRevision(vcsContent: CharSequence, newStagedDocument: Document) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (isReleased) return

    if (stagedDocument != newStagedDocument) {
      stagedTracker.replaceDocument(Side.RIGHT, newStagedDocument)
      unstagedTracker.replaceDocument(Side.LEFT, newStagedDocument)
      stagedDocument = newStagedDocument
    }

    stagedTracker.doFrozen(Side.LEFT) {
      updateDocument(ThreeSide.LEFT, null) {
        vcsDocument.setText(vcsContent)
      }
    }

    if (!isInitialized) {
      isInitialized = true
      updateHighlighters()
    }
  }

  @RequiresEdt
  fun dropBaseRevision() {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (isReleased) return

    isInitialized = false
    updateHighlighters()
  }

  override fun release() {
    val runnable = Runnable {
      if (isReleased) return@Runnable
      isReleased = true

      Disposer.dispose(disposable)
    }

    if (!ApplicationManager.getApplication().isDispatchThread || LOCK.isHeldByCurrentThread) {
      ApplicationManager.getApplication().invokeLater(runnable)
    }
    else {
      runnable.run()
    }
  }

  @RequiresEdt
  private fun updateDocument(side: ThreeSide, commandName: @NlsContexts.Command String?, task: (Document) -> Unit): Boolean {
    val affectedDocument = side.selectNotNull(vcsDocument, stagedDocument, document)
    return LineStatusTrackerBase.updateDocument(project, affectedDocument, commandName, task)
  }


  private var cachedBlocks: List<StagedRange>? = null
  private val blocks: List<StagedRange>
    get() {
      var blocks = cachedBlocks
      if (blocks == null) {
        blocks = BlockMerger(stagedTracker.blocks, unstagedTracker.blocks).run()
        cachedBlocks = blocks
      }
      return blocks
    }

  private fun updateHighlighters() {
    renderer.scheduleUpdate()
  }

  override fun isOperational(): Boolean = LOCK.read {
    return isInitialized && !isReleased
  }

  override fun isValid(): Boolean = LOCK.read {
    isOperational() && !stagedTracker.isFrozen() && !unstagedTracker.isFrozen()
  }

  override fun getRanges(): List<StagedRange>? = blockOperations.getRanges()
  override fun findRange(range: Range): StagedRange? = blockOperations.findRange(range)
  override fun getNextRange(line: Int): StagedRange? = blockOperations.getNextRange(line)
  override fun getPrevRange(line: Int): StagedRange? = blockOperations.getPrevRange(line)
  override fun getRangesForLines(lines: BitSet): List<StagedRange>? = blockOperations.getRangesForLines(lines)
  override fun getRangeForLine(line: Int): StagedRange? = blockOperations.getRangeForLine(line)
  override fun isLineModified(line: Int): Boolean = blockOperations.isLineModified(line)
  override fun isRangeModified(startLine: Int, endLine: Int): Boolean = blockOperations.isRangeModified(startLine, endLine)
  override fun transferLineFromVcs(line: Int, approximate: Boolean): Int = blockOperations.transferLineFromVcs(line, approximate)
  override fun transferLineToVcs(line: Int, approximate: Boolean): Int = blockOperations.transferLineToVcs(line, approximate)

  fun transferLineFromLocalToStaged(line: Int, approximate: Boolean): Int = stagedBlockOperations.transferLineToVcs(line, approximate)
  fun transferLineFromStagedToLocal(line: Int, approximate: Boolean): Int = stagedBlockOperations.transferLineFromVcs(line, approximate)

  override fun scrollAndShowHint(range: Range, editor: Editor) {
    renderer.scrollAndShow(editor, range)
  }

  override fun showHint(range: Range, editor: Editor) {
    renderer.showAfterScroll(editor, range)
  }

  override fun <T> readLock(task: () -> T): T {
    LOCK.read {
      return task()
    }
  }

  override fun doFrozen(task: Runnable) {
    stagedTracker.doFrozen {
      unstagedTracker.doFrozen {
        task.run()
      }
    }
  }

  override fun freeze() {
    unstagedTracker.freeze(Side.LEFT)
    unstagedTracker.freeze(Side.RIGHT)
    stagedTracker.freeze(Side.LEFT)
    stagedTracker.freeze(Side.RIGHT)
  }

  override fun unfreeze() {
    unstagedTracker.unfreeze(Side.LEFT)
    unstagedTracker.unfreeze(Side.RIGHT)
    stagedTracker.unfreeze(Side.LEFT)
    stagedTracker.unfreeze(Side.RIGHT)
  }


  override fun rollbackChanges(range: Range) {
    val newRange = blockOperations.findBlock(range) ?: return
    runBulkRollback(listOf(newRange))
  }

  override fun rollbackChanges(lines: BitSet) {
    val toRevert = blockOperations.getRangesForLines(lines) ?: return
    runBulkRollback(toRevert)
  }

  @RequiresEdt
  private fun runBulkRollback(toRevert: List<StagedRange>) {
    if (!isValid()) return

    val filter = BlockFilter.create(toRevert, Side.RIGHT)
    updateDocument(ThreeSide.RIGHT, GitBundle.message("stage.revert.unstaged.range.command.name")) {
      unstagedTracker.partiallyApplyBlocks(Side.RIGHT) { filter.matches(it) }
    }
  }

  fun stageChanges(range: Range) {
    val newRange = blockOperations.findBlock(range) ?: return
    runBulkStage(listOf(newRange))
  }

  @RequiresEdt
  private fun runBulkStage(toRevert: List<StagedRange>) {
    if (!isValid()) return

    val filter = BlockFilter.create(toRevert, Side.RIGHT)
    updateDocument(ThreeSide.BASE, GitBundle.message("stage.add.range.command.name")) {
      unstagedTracker.partiallyApplyBlocks(Side.LEFT) { filter.matches(it) }
    }
  }

  fun unstageChanges(range: Range) {
    val newRange = blockOperations.findBlock(range) ?: return
    runBulkUnstage(listOf(newRange))
  }

  @RequiresEdt
  private fun runBulkUnstage(toRevert: List<StagedRange>) {
    if (!isValid()) return

    val filter = BlockFilter.create(toRevert, Side.LEFT)
    updateDocument(ThreeSide.BASE, GitBundle.message("stage.revert.staged.range.command.name")) {
      stagedTracker.partiallyApplyBlocks(Side.RIGHT) { filter.matches(it) }
    }
  }

  private class BlockFilter(private val bitSet1: BitSet,
                            private val bitSet2: BitSet) {
    fun matches(block: DocumentTracker.Block): Boolean {
      return matches(block, Side.LEFT, bitSet1) ||
             matches(block, Side.RIGHT, bitSet2)
    }

    private fun matches(block: DocumentTracker.Block, blockSide: Side, bitSet: BitSet): Boolean {
      val start = blockSide.select(block.range.start1, block.range.start2)
      val end = blockSide.select(block.range.end1, block.range.end2)
      val next: Int = bitSet.nextSetBit(start)
      return next != -1 && next < end
    }

    companion object {
      fun create(ranges: List<StagedRange>, targetTracker: Side): BlockFilter {
        val bitSet1 = collectAffectedLines(ranges, targetTracker.selectNotNull(ThreeSide.LEFT, ThreeSide.BASE))
        val bitSet2 = collectAffectedLines(ranges, targetTracker.selectNotNull(ThreeSide.BASE, ThreeSide.RIGHT))
        return BlockFilter(bitSet1, bitSet2)
      }

      private fun collectAffectedLines(ranges: List<StagedRange>, side: ThreeSide): BitSet {
        return BitSet().apply {
          for (stagedRange in ranges) {
            val line1 = side.select(stagedRange.vcsLine1, stagedRange.stagedLine1, stagedRange.line1)
            val line2 = side.select(stagedRange.vcsLine2, stagedRange.stagedLine2, stagedRange.line2)
            this.set(line1, line2)
          }
        }
      }
    }
  }

  private inner class MyDocumentTrackerHandler(private val unstaged: Boolean) : DocumentTracker.Handler {
    override fun afterBulkRangeChange(isDirty: Boolean) {
      cachedBlocks = null

      updateHighlighters()

      if (isOperational()) {
        if (unstaged) {
          if (unstagedTracker.blocks.isEmpty()) {
            saveDocumentWhenUnchanged(project, document)
          }
        }
        else {
          if (stagedTracker.blocks.isEmpty()) {
            saveDocumentWhenUnchanged(project, stagedDocument)
          }
        }
      }
    }

    override fun onUnfreeze(side: Side) {
      updateHighlighters()
    }
  }

  private class MyLineStatusMarkerPopupRenderer(val tracker: GitStageLineStatusTracker)
    : LineStatusMarkerPopupRenderer(tracker) {
    override fun getEditorFilter(): MarkupEditorFilter? = MarkupEditorFilterFactory.createIsNotDiffFilter()

    override fun shouldPaintGutter(): Boolean {
      return tracker.mode.isVisible
    }

    override fun shouldPaintErrorStripeMarkers(): Boolean {
      return tracker.mode.isVisible && tracker.mode.showErrorStripeMarkers
    }

    override fun paint(editor: Editor, g: Graphics) {
      val ranges = tracker.getRanges() ?: return

      val blocks: List<ChangesBlock<StageLineFlags>> = VisibleRangeMerger.merge(editor, ranges, MyLineFlagProvider, g.clipBounds)
      for (block in blocks) {
        paintStageLines(g as Graphics2D, editor, block.changes)
      }
    }

    private fun paintStageLines(g: Graphics2D, editor: Editor, block: List<ChangedLines<StageLineFlags>>) {
      val borderColor = LineStatusMarkerDrawUtil.getGutterBorderColor(editor)

      val area = LineStatusMarkerDrawUtil.getGutterArea(editor)
      val x = area.first
      val endX = area.second
      val midX = (endX + x + 3) / 2

      val y = block.first().y1
      val endY = block.last().y2

      for (change in block) {
        if (change.y1 != change.y2 &&
            change.flags.isUnstaged) {
          val start = change.y1
          val end = change.y2
          val gutterColor = LineStatusMarkerDrawUtil.getGutterColor(change.type, editor)

          if (change.flags.isStaged) {
            LineStatusMarkerDrawUtil.paintRect(g, gutterColor, null, x, start, midX, end)
          }
          else {
            LineStatusMarkerDrawUtil.paintRect(g, gutterColor, null, x, start, endX, end)
          }
        }
      }

      if (borderColor == null) {
        for (change in block) {
          if (change.y1 != change.y2 &&
              change.flags.isStaged) {
            val start = change.y1
            val end = change.y2
            val stagedBorderColor = LineStatusMarkerDrawUtil.getIgnoredGutterBorderColor(change.type, editor)

            LineStatusMarkerDrawUtil.paintRect(g, null, stagedBorderColor, x, start, endX, end)
          }
        }
      }
      else if (y != endY) {
        LineStatusMarkerDrawUtil.paintRect(g, null, borderColor, x, y, endX, endY)
      }

      for (change in block) {
        if (change.y1 == change.y2) {
          val start = change.y1
          val gutterColor = LineStatusMarkerDrawUtil.getGutterColor(change.type, editor)
          val stagedBorderColor = borderColor ?: LineStatusMarkerDrawUtil.getIgnoredGutterBorderColor(change.type, editor)

          if (change.flags.isUnstaged && change.flags.isStaged) {
            paintStripeTriangle(g, editor, gutterColor, stagedBorderColor, x, endX, start)
          }
          else if (change.flags.isStaged) {
            LineStatusMarkerDrawUtil.paintTriangle(g, editor, null, stagedBorderColor, x, endX, start)
          }
          else {
            LineStatusMarkerDrawUtil.paintTriangle(g, editor, gutterColor, borderColor, x, endX, start)
          }
        }
      }
    }

    private fun paintStripeTriangle(g: Graphics2D, editor: Editor, color: Color?, borderColor: Color?, x1: Int, x2: Int, y: Int) {
      @Suppress("NAME_SHADOWING") var y = y
      val editorScale = if (editor is EditorImpl) editor.scale else 1.0f
      val size = JBUIScale.scale(5 * editorScale).toInt()
      if (y < size) y = size
      val xPoints = intArrayOf(x1, x1, x2)
      val yPointsBorder = intArrayOf(y - size, y + size, y)
      val yPointsFill = intArrayOf(y - size, y, y)

      if (color != null) {
        g.color = color
        g.fillPolygon(xPoints, yPointsFill, xPoints.size)
      }

      if (borderColor != null) {
        g.color = borderColor
        val oldStroke = g.stroke
        g.stroke = BasicStroke(JBUIScale.scale(1).toFloat())
        g.drawPolygon(xPoints, yPointsBorder, xPoints.size)
        g.stroke = oldStroke
      }
    }

    class StageLineFlags(val isStaged: Boolean, val isUnstaged: Boolean)

    object MyLineFlagProvider : VisibleRangeMerger.FlagsProvider<StageLineFlags> {
      override fun getFlags(range: Range): StageLineFlags {
        range as StagedRange
        return StageLineFlags(range.hasStaged, range.hasUnstaged)
      }

      override fun mergeFlags(flags1: StageLineFlags, flags2: StageLineFlags): StageLineFlags =
        StageLineFlags(flags1.isStaged || flags2.isStaged,
                       flags1.isUnstaged || flags2.isUnstaged)
    }

    override fun createAdditionalInfoPanel(editor: Editor, range: Range, mousePosition: Point?, disposable: Disposable): JComponent? {
      return createStageLinksPanel(editor, range, mousePosition, disposable)
    }

    override fun showHintAt(editor: Editor, range: Range, mousePosition: Point?) {
      if (!myTracker.isValid()) return
      myTracker as GitStageLineStatusTracker
      range as StagedRange

      if (!range.hasStaged || !range.hasUnstaged) {
        super.showHintAt(editor, range, mousePosition)
        return
      }

      val disposable = Disposer.newDisposable()

      val stagedTextField = createTextField(editor, myTracker.stagedDocument, range.stagedLine1, range.stagedLine2)
      val vcsTextField = createTextField(editor, myTracker.vcsDocument, range.vcsLine1, range.vcsLine2)
      installWordDiff(editor, stagedTextField, vcsTextField, range, disposable)

      val editorsPanel = createEditorComponent(editor, stagedTextField, vcsTextField)

      val actions = createToolbarActions(editor, range, mousePosition)
      val toolbar = LineStatusMarkerPopupPanel.buildToolbar(editor, actions, disposable)

      val additionalPanel = createStageLinksPanel(editor, range, mousePosition, disposable)

      LineStatusMarkerPopupPanel.showPopupAt(editor, toolbar, editorsPanel, additionalPanel, mousePosition, disposable, null)
    }

    fun createEditorComponent(editor: Editor, stagedTextField: EditorTextField, vcsTextField: EditorTextField): JComponent {
      val stagedEditorPane = createEditorPane(editor, GitBundle.message("stage.content.staged"), stagedTextField, true)
      val vcsEditorPane = createEditorPane(editor, GitUtil.HEAD, vcsTextField, false)

      val editorsPanel = JPanel(StagePopupVerticalLayout())
      editorsPanel.add(stagedEditorPane)
      editorsPanel.add(vcsEditorPane)
      editorsPanel.background = LineStatusMarkerPopupPanel.getEditorBackgroundColor(editor)
      return editorsPanel
    }

    private fun createEditorPane(editor: Editor, @Nls text: String, textField: EditorTextField, topBorder: Boolean): JComponent {
      val label = JBLabel(text)
      label.border = JBUI.Borders.emptyBottom(2)
      label.font = UIUtil.getLabelFont(UIUtil.FontSize.SMALL)
      label.foreground = UIUtil.getLabelDisabledForeground()

      val borderColor = LineStatusMarkerPopupPanel.getBorderColor()
      val outerLineBorder = JBUI.Borders.customLine(borderColor, if (topBorder) 1 else 0, 1, 1, 1)
      val innerEmptyBorder = JBUI.Borders.empty(2)
      val border = BorderFactory.createCompoundBorder(outerLineBorder, innerEmptyBorder)

      return JBUI.Panels.simplePanel(textField)
        .addToTop(label)
        .withBackground(LineStatusMarkerPopupPanel.getEditorBackgroundColor(editor))
        .withBorder(border)
    }

    private fun createTextField(editor: Editor, document: Document, line1: Int, line2: Int): EditorTextField {
      val textRange = DiffUtil.getLinesRange(document, line1, line2)
      val content = textRange.subSequence(document.immutableCharSequence).toString()
      val textField = LineStatusMarkerPopupPanel.createTextField(editor, content)
      LineStatusMarkerPopupPanel.installBaseEditorSyntaxHighlighters(myTracker.project, textField, document, textRange, fileType)
      return textField
    }

    private fun installWordDiff(editor: Editor,
                                stagedTextField: EditorTextField,
                                vcsTextField: EditorTextField,
                                range: StagedRange,
                                disposable: Disposable) {
      myTracker as GitStageLineStatusTracker
      if (!DiffApplicationSettings.getInstance().SHOW_LST_WORD_DIFFERENCES) return
      if (!range.hasLines()) return

      val currentContent = DiffUtil.getLinesContent(myTracker.document, range.line1, range.line2)
      val stagedContent = DiffUtil.getLinesContent(myTracker.stagedDocument, range.stagedLine1, range.stagedLine2)
      val vcsContent = DiffUtil.getLinesContent(myTracker.vcsDocument, range.vcsLine1, range.vcsLine2)
      val (stagedWordDiff, vcsWordDiff) = BackgroundTaskUtil.tryComputeFast(
        { indicator ->
          val cancellationChecker = IndicatorCancellationChecker(indicator)
          Pair(if (range.hasStagedLines()) ByWord.compare(stagedContent, currentContent, ComparisonPolicy.DEFAULT, cancellationChecker) else null,
               if (range.hasVcsLines()) ByWord.compare(vcsContent, currentContent, ComparisonPolicy.DEFAULT, cancellationChecker) else null)
        }, 200) ?: return

      if (stagedWordDiff != null) {
        LineStatusMarkerPopupPanel.installPopupEditorWordHighlighters(stagedTextField, stagedWordDiff)
      }
      if (vcsWordDiff != null) {
        LineStatusMarkerPopupPanel.installPopupEditorWordHighlighters(vcsTextField, vcsWordDiff)
      }

      if (stagedWordDiff != null || vcsWordDiff != null) {
        val currentStartOffset = myTracker.document.getLineStartOffset(range.line1)
        installMasterEditorWordHighlighters(editor, currentStartOffset, stagedWordDiff.orEmpty(), vcsWordDiff.orEmpty(), disposable)
      }
    }

    private fun installMasterEditorWordHighlighters(editor: Editor,
                                                    currentStartOffset: Int,
                                                    wordDiff1: List<DiffFragment>,
                                                    wordDiff2: List<DiffFragment>,
                                                    parentDisposable: Disposable) {
      val highlighters = WordDiffMerger(editor, currentStartOffset, wordDiff1, wordDiff2).run()
      Disposer.register(parentDisposable, Disposable {
        highlighters.forEach(RangeHighlighter::dispose)
      })
    }

    private class WordDiffMerger(private val editor: Editor,
                                 private val currentStartOffset: Int,
                                 private val wordDiff1: List<DiffFragment>,
                                 private val wordDiff2: List<DiffFragment>) {
      val highlighters: MutableList<RangeHighlighter> = ArrayList()

      private var dirtyStart = -1
      private var dirtyEnd = -1
      private val affectedFragments: MutableList<DiffFragment> = mutableListOf()

      fun run(): List<RangeHighlighter> {
        val it1 = PeekableIteratorWrapper(wordDiff1.iterator())
        val it2 = PeekableIteratorWrapper(wordDiff2.iterator())

        while (it1.hasNext() || it2.hasNext()) {
          if (!it2.hasNext()) {
            handleFragment(it1.next())
            continue
          }
          if (!it1.hasNext()) {
            handleFragment(it2.next())
            continue
          }

          val fragment1 = it1.peek()
          val fragment2 = it2.peek()

          if (fragment1.startOffset2 <= fragment2.startOffset2) {
            handleFragment(it1.next())
          }
          else {
            handleFragment(it2.next())
          }
        }
        flush(Int.MAX_VALUE)

        return highlighters
      }

      private fun handleFragment(fragment: DiffFragment) {
        flush(fragment.startOffset2)
        markDirtyRange(fragment.startOffset2, fragment.endOffset2)
        affectedFragments.add(fragment)
      }

      private fun markDirtyRange(start: Int, end: Int) {
        if (dirtyEnd == -1) {
          dirtyStart = start
          dirtyEnd = end
        }
        else {
          dirtyEnd = max(dirtyEnd, end)
        }
      }

      private fun flush(nextLine: Int) {
        if (dirtyEnd != -1 && dirtyEnd < nextLine) {
          val currentStart = currentStartOffset + dirtyStart
          val currentEnd = currentStartOffset + dirtyEnd
          val type = affectedFragments.map { DiffUtil.getDiffType(it) }.distinct().singleOrNull() ?: TextDiffType.MODIFIED
          highlighters.addAll(DiffDrawUtil.createInlineHighlighter(editor, currentStart, currentEnd, type))

          dirtyStart = -1
          dirtyEnd = -1
          affectedFragments.clear()
        }
      }
    }

    private fun createStageLinksPanel(editor: Editor,
                                      range: Range,
                                      mousePosition: Point?,
                                      disposable: Disposable): JComponent? {
      if (range !is StagedRange) return null

      val panel = JPanel()
      panel.layout = BoxLayout(panel, BoxLayout.X_AXIS)
      panel.border = JBUI.Borders.emptyRight(10)
      panel.isOpaque = false

      panel.add(VerticalSeparatorComponent())
      panel.add(Box.createHorizontalStrut(JBUI.scale(10)))

      if (range.hasUnstaged) {
        val stageLink = createStageLinkButton(editor, disposable, "Git.Stage.Add",
                                              GitBundle.message("action.label.add.unstaged.range"),
                                              GitBundle.message("action.label.add.unstaged.range.tooltip")) {
          tracker.stageChanges(range)
          reopenRange(editor, range, mousePosition)
        }
        panel.add(stageLink)
      }

      if (range.hasStaged) {
        if (range.hasUnstaged) panel.add(Box.createHorizontalStrut(JBUI.scale(16)))

        val unstageLink = createStageLinkButton(editor, disposable, "Git.Stage.Reset",
                                                GitBundle.message("action.label.reset.staged.range"),
                                                GitBundle.message("action.label.reset.staged.range.tooltip")) {
          tracker.unstageChanges(range)
          reopenRange(editor, range, mousePosition)
        }
        panel.add(unstageLink)
      }

      return panel
    }

    private fun createStageLinkButton(editor: Editor,
                                      disposable: Disposable,
                                      actionId: @NonNls String,
                                      text: @Nls String,
                                      tooltipText: @Nls String,
                                      callback: () -> Unit): LinkLabel<*> {
      val shortcut = ActionManager.getInstance().getAction(actionId).shortcutSet
      val shortcuts = shortcut.shortcuts

      val link = LinkLabel.create(text, callback)
      link.toolTipText = DiffUtil.createTooltipText(tooltipText, StringUtil.nullize(KeymapUtil.getShortcutsText(shortcuts)))

      if (shortcuts.isNotEmpty()) {
        object : DumbAwareAction() {
          override fun actionPerformed(e: AnActionEvent) {
            link.doClick()
          }
        }.registerCustomShortcutSet(shortcut, editor.component, disposable)
      }
      return link
    }

    override fun createToolbarActions(editor: Editor, range: Range, mousePosition: Point?): List<AnAction> {
      val actions = ArrayList<AnAction>()
      actions.add(ShowPrevChangeMarkerAction(editor, range))
      actions.add(ShowNextChangeMarkerAction(editor, range))
      actions.add(RollbackLineStatusRangeAction(editor, range))
      actions.add(StageShowDiffAction(editor, range))
      actions.add(CopyLineStatusRangeAction(editor, range))
      actions.add(ToggleByWordDiffAction(editor, range, mousePosition))
      return actions
    }

    private inner class RollbackLineStatusRangeAction(editor: Editor, range: Range)
      : RangeMarkerAction(editor, range, IdeActions.SELECTED_CHANGES_ROLLBACK) {
      override fun isEnabled(editor: Editor, range: Range): Boolean = (range as StagedRange).hasUnstaged

      override fun actionPerformed(editor: Editor, range: Range) {
        RollbackLineStatusAction.rollback(tracker, range, editor)
      }
    }

    private inner class StageShowDiffAction(editor: Editor, range: Range)
      : RangeMarkerAction(editor, range, IdeActions.ACTION_SHOW_DIFF_COMMON), LightEditCompatible {
      override fun isEnabled(editor: Editor, range: Range): Boolean = true

      override fun actionPerformed(editor: Editor, range: Range) {
        range as StagedRange
        myTracker as GitStageLineStatusTracker

        val canExpandBefore = range.line1 != 0 && range.stagedLine1 != 0 && range.vcsLine1 != 0
        val canExpandAfter = range.line2 < DiffUtil.getLineCount(myTracker.document) &&
                             range.stagedLine2 < DiffUtil.getLineCount(myTracker.stagedDocument) &&
                             range.vcsLine2 < DiffUtil.getLineCount(myTracker.vcsDocument)

        val currentContent = createDiffContent(myTracker.document, range.line1, range.line2,
                                               canExpandBefore, canExpandAfter)
        val stagedContent = createDiffContent(myTracker.stagedDocument, range.stagedLine1, range.stagedLine2,
                                              canExpandBefore, canExpandAfter)
        val vcsContent = createDiffContent(myTracker.vcsDocument, range.vcsLine1, range.vcsLine2,
                                           canExpandBefore, canExpandAfter)

        val request = SimpleDiffRequest(
          DiffBundle.message("dialog.title.diff.for.range"),
          vcsContent, stagedContent, currentContent,
          GitUtil.HEAD, GitBundle.message("stage.content.staged"), GitBundle.message("stage.content.local"))
        request.putUserData(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_RIGHT_TO_BASE_ACTION_TEXT, GitBundle.message("action.label.add.unstaged.range"))
        request.putUserData(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_BASE_TO_RIGHT_ACTION_TEXT, DiffBundle.message("action.presentation.diff.revert.text"))
        request.putUserData(DiffUserDataKeysEx.VCS_DIFF_ACCEPT_LEFT_TO_BASE_ACTION_TEXT, GitBundle.message("action.label.reset.staged.range"))
        DiffManager.getInstance().showDiff(myTracker.project, request)
      }

      private fun createDiffContent(document: Document, line1: Int, line2: Int,
                                    canExpandBefore: Boolean, canExpandAfter: Boolean): DiffContent {
        val textRange = DiffUtil.getLinesRange(document,
                                               line1 - if (canExpandBefore) 1 else 0,
                                               line2 + if (canExpandAfter) 1 else 0)
        val content = DiffContentFactory.getInstance().create(myTracker.project, document, myTracker.virtualFile)
        return DiffContentFactory.getInstance().createFragment(myTracker.project, content, textRange)
      }
    }
  }

  private inner class MyBlockOperations(lock: DocumentTracker.Lock) : LineStatusTrackerBlockOperations<StagedRange, StagedRange>(lock) {
    override fun getBlocks(): List<StagedRange>? = if (isValid()) blocks else null
    override fun StagedRange.toRange(): StagedRange = this
  }

  private inner class MyStagedBlockOperations(lock: DocumentTracker.Lock) : LineStatusTrackerBlockOperations<Range, BlockI>(lock) {
    override fun getBlocks(): List<BlockI>? = if (isValid()) unstagedTracker.blocks else null
    override fun BlockI.toRange(): Range = Range(start, end, vcsStart, vcsEnd)
  }
}

class StagedRange(line1: Int, line2: Int,
                  val stagedLine1: Int, val stagedLine2: Int,
                  headLine1: Int, headLine2: Int,
                  val hasStaged: Boolean, val hasUnstaged: Boolean)
  : Range(line1, line2, headLine1, headLine2, null), BlockI {
  override val start: Int get() = line1
  override val end: Int get() = line2
  override val vcsStart: Int get() = vcsLine1
  override val vcsEnd: Int get() = vcsLine2
  override val isEmpty: Boolean get() = line1 == line2 && stagedLine1 == stagedLine2 && vcsLine1 == vcsLine2

  fun hasStagedLines(): Boolean = stagedLine1 != stagedLine2
}

private class BlockMerger(private val staged: List<DocumentTracker.Block>,
                          private val unstaged: List<DocumentTracker.Block>) {
  private val ranges: MutableList<StagedRange> = mutableListOf()

  private var dirtyStart = -1
  private var dirtyEnd = -1

  private var hasStaged: Boolean = false
  private var hasUnstaged: Boolean = false
  private var stagedShift: Int = 0
  private var unstagedShift: Int = 0
  private var dirtyStagedShift: Int = 0
  private var dirtyUnstagedShift: Int = 0

  fun run(): List<StagedRange> {
    val it1 = PeekableIteratorWrapper(staged.iterator())
    val it2 = PeekableIteratorWrapper(unstaged.iterator())

    while (it1.hasNext() || it2.hasNext()) {
      if (!it2.hasNext()) {
        handleStaged(it1.next())
        continue
      }
      if (!it1.hasNext()) {
        handleUnstaged(it2.next())
        continue
      }

      val block1 = it1.peek()
      val block2 = it2.peek()

      if (block1.range.start2 <= block2.range.start1) {
        handleStaged(it1.next())
      }
      else {
        handleUnstaged(it2.next())
      }
    }
    flush(Int.MAX_VALUE)

    return ranges
  }

  private fun handleStaged(block: DocumentTracker.Block) {
    val range = block.range
    flush(range.start2)

    dirtyStagedShift -= getRangeDelta(range)

    markDirtyRange(range.start2, range.end2)
    hasStaged = true
  }

  private fun handleUnstaged(block: DocumentTracker.Block) {
    val range = block.range
    flush(range.start1)

    dirtyUnstagedShift += getRangeDelta(range)

    markDirtyRange(range.start1, range.end1)
    hasUnstaged = true
  }

  private fun markDirtyRange(start: Int, end: Int) {
    if (dirtyEnd == -1) {
      dirtyStart = start
      dirtyEnd = end
    }
    else {
      dirtyEnd = max(dirtyEnd, end)
    }
  }

  private fun flush(nextLine: Int) {
    if (dirtyEnd != -1 && dirtyEnd < nextLine) {
      ranges.add(StagedRange(dirtyStart + unstagedShift, dirtyEnd + unstagedShift + dirtyUnstagedShift,
                             dirtyStart, dirtyEnd,
                             dirtyStart + stagedShift, dirtyEnd + stagedShift + dirtyStagedShift,
                             hasStaged, hasUnstaged))
      dirtyStart = -1
      dirtyEnd = -1
      hasStaged = false
      hasUnstaged = false

      stagedShift += dirtyStagedShift
      unstagedShift += dirtyUnstagedShift
      dirtyStagedShift = 0
      dirtyUnstagedShift = 0
    }
  }

  private fun getRangeDelta(range: com.intellij.diff.util.Range): Int {
    val deleted = range.end1 - range.start1
    val inserted = range.end2 - range.start2
    return inserted - deleted
  }
}
