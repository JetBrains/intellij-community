// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package git4idea.index

import com.intellij.diff.util.Side
import com.intellij.diff.util.ThreeSide
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.WriteThread
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.editor.markup.MarkupEditorFilter
import com.intellij.openapi.editor.markup.MarkupEditorFilterFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.paint.LinePainter2D
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.containers.PeekableIteratorWrapper
import org.jetbrains.annotations.CalledInAwt
import java.awt.*
import java.util.*
import kotlin.math.max

class GitStageLineStatusTracker(
  override val project: Project,
  override val virtualFile: VirtualFile,
  override val document: Document,
  private val stagedDocument: Document
) : LocalLineStatusTracker<StagedRange> {
  override val vcsDocument: Document = LineStatusTrackerBase.createVcsDocument(document)

  override val disposable: Disposable = Disposer.newDisposable()
  private val LOCK: DocumentTracker.Lock = DocumentTracker.Lock()

  private val blockOperations: LineStatusTrackerBlockOperations<StagedRange, StagedRange> = MyBlockOperations(LOCK)
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

    stagedTracker.addHandler(MyDocumentTrackerHandler(true))
    unstagedTracker.addHandler(MyDocumentTrackerHandler(false))
  }


  @CalledInAwt
  override fun setBaseRevision(vcsContent: CharSequence) {
    ApplicationManager.getApplication().assertIsDispatchThread()
    if (isReleased) return

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

  @CalledInAwt
  override fun dropBaseRevision() {
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
      WriteThread.submit(runnable)
    }
    else {
      runnable.run()
    }
  }

  @CalledInAwt
  private fun updateDocument(side: ThreeSide, commandName: String?, task: (Document) -> Unit): Boolean {
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
    TODO("Not yet implemented")
  }

  override fun rollbackChanges(lines: BitSet) {
    TODO("Not yet implemented")
  }


  private inner class MyDocumentTrackerHandler(private val unstaged: Boolean) : DocumentTracker.Handler {
    override fun afterBulkRangeChange(isDirty: Boolean) {
      cachedBlocks = null

      updateHighlighters()
    }

    override fun onUnfreeze(side: Side) {
      updateHighlighters()
    }
  }

  private class MyLineStatusMarkerPopupRenderer(val tracker: GitStageLineStatusTracker)
    : LineStatusMarkerPopupRenderer(tracker) {
    override fun getFileType(): FileType = tracker.virtualFile.fileType

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
      val stripeThickness = JBUIScale.scale(2)

      editor as EditorImpl
      val area = LineStatusMarkerRenderer.getGutterArea(editor)
      val x = area.first
      val endX = area.second

      for (change in block) {
        if (change.line1 != change.line2) {
          val start = editor.visualLineToY(change.line1)
          val end = editor.visualLineToY(change.line2)
          val gutterColor = LineStatusMarkerRenderer.getGutterColor(change.type, editor)

          LineStatusMarkerRenderer.paintRect(g, gutterColor, null, x, start, endX, end)
          if (change.flags.isUnstaged) {
            paintThickLine(g, JBColor.RED, x, start, end, stripeThickness)
          }
          if (change.flags.isStaged) {
            paintThickLine(g, JBColor.GREEN, endX - stripeThickness, start, end, stripeThickness)
          }
        }
      }

      for (change in block) {
        if (change.line1 == change.line2) {
          val start = editor.visualLineToY(change.line1)
          val gutterColor = LineStatusMarkerRenderer.getGutterColor(change.type, editor)
          LineStatusMarkerRenderer.paintTriangle(g, editor, gutterColor, null, x, endX, start)
        }
      }
    }

    private fun paintThickLine(g: Graphics2D, color: Color?, x: Int, y1: Int, y2: Int, thickness: Int) {
      val oldStroke = g.stroke
      g.stroke = BasicStroke(thickness.toFloat())
      g.color = color
      LinePainter2D.paint(g, x.toDouble(), y1.toDouble(), x.toDouble(), y2 - 1.toDouble())
      g.stroke = oldStroke
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

    override fun createToolbarActions(editor: Editor, range: Range, mousePosition: Point?): MutableList<AnAction> {
      return mutableListOf()
    }
  }

  private inner class MyBlockOperations(lock: DocumentTracker.Lock) : LineStatusTrackerBlockOperations<StagedRange, StagedRange>(lock) {
    override fun getBlocks(): List<StagedRange>? = if (isValid()) blocks else null
    override fun StagedRange.toRange(): StagedRange = this
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
