// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.diff.DiffContext
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.fragmented.*
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffDrawUtil.LineHighlighterBuilder
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Side
import com.intellij.diff.util.TextDiffType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.RangeHighlighterEx
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.ExcludeAllCheckboxPanel
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LineFragmentData
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LocalTrackerActionProvider
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LocalTrackerChange
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.LocalTrackerDiffHandler
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.SelectedTrackerLine
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.ToggleableLineRange
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.computeDifferences
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.createCheckboxToggle
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.createToggleAreaThumb
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.createTrackerEditorPopupActions
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.createTrackerShortcutOnlyActions
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.getSingleCheckBoxLine
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.getStatusText
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.hasIconHighlighters
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.installTrackerListener
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.shouldShowToggleAreaThumb
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.toggleBlockExclusion
import com.intellij.openapi.vcs.changes.actions.diff.lst.LocalTrackerDiffUtil.toggleLinePartialExclusion
import com.intellij.openapi.vcs.ex.RangeExclusionState
import com.intellij.openapi.vcs.ex.countAffectedVisibleChanges
import com.intellij.openapi.vcs.ex.createClientIdGutterIconRenderer
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JComponent

class UnifiedLocalChangeListDiffViewer(context: DiffContext,
                                       private val myLocalRequest: LocalChangeListDiffRequest)
  : UnifiedDiffViewer(context, myLocalRequest.request) {
  private val myAllowExcludeChangesFromCommit: Boolean

  private val myTrackerActionProvider: LocalTrackerActionProvider
  private val myExcludeAllCheckboxPanel = ExcludeAllCheckboxPanel(this, editor)
  private val myGutterCheckboxMouseMotionListener: GutterCheckboxMouseMotionListener

  private val myHighlighters: MutableList<RangeHighlighter> = mutableListOf()

  init {
    myAllowExcludeChangesFromCommit = DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context)
    myTrackerActionProvider = MyLocalTrackerActionProvider(this, myLocalRequest, myAllowExcludeChangesFromCommit)
    myExcludeAllCheckboxPanel.init(myLocalRequest, myAllowExcludeChangesFromCommit)

    installTrackerListener(this, myLocalRequest)

    myGutterCheckboxMouseMotionListener = GutterCheckboxMouseMotionListener()
    myGutterCheckboxMouseMotionListener.install()

    for (action in createTrackerShortcutOnlyActions(myTrackerActionProvider)) {
      DiffUtil.registerAction(action, myPanel)
    }
  }

  override fun createTitles(): JComponent {
    val titles = super.createTitles()

    val titleWithCheckbox = JBUI.Panels.simplePanel()
    if (titles != null) titleWithCheckbox.addToCenter(titles)
    titleWithCheckbox.addToLeft(myExcludeAllCheckboxPanel)
    return titleWithCheckbox
  }

  override fun createEditorPopupActions(): List<AnAction> {
    return super.createEditorPopupActions() +
           createTrackerEditorPopupActions(myTrackerActionProvider)
  }

  override fun createUi(change: UnifiedDiffChange): UnifiedDiffChangeUi {
    if (change is MyUnifiedDiffChange) return MyUnifiedDiffChangeUi(this, change)
    return super.createUi(change)
  }

  override fun getStatusTextMessage(): @Nls String? {
    val allChanges = diffChanges
    if (myAllowExcludeChangesFromCommit && allChanges != null) {
      var totalCount = 0
      var includedIntoCommitCount = 0
      var excludedCount = 0

      for (change in allChanges) {
        val exclusionState = if (change is MyUnifiedDiffChange) {
          change.exclusionState
        }
        else {
          RangeExclusionState.Included
        }

        totalCount += exclusionState.countAffectedVisibleChanges(false)
        if (change.isSkipped) {
          excludedCount += exclusionState.countAffectedVisibleChanges(false)
        }
        else {
          includedIntoCommitCount += exclusionState.countAffectedVisibleChanges(true)
        }
      }

      return getStatusText(totalCount, includedIntoCommitCount, excludedCount, myModel.isContentsEqual())
    }

    return super.getStatusTextMessage()
  }

  private fun superComputeDifferences(indicator: ProgressIndicator): Runnable {
    return super.computeDifferences(indicator)
  }

  override fun computeDifferences(indicator: ProgressIndicator): Runnable {
    val document1 = content1.document
    val document2 = content2.document

    return computeDifferences(
      myLocalRequest.lineStatusTracker,
      document1,
      document2,
      myLocalRequest.changelistId,
      myAllowExcludeChangesFromCommit,
      myTextDiffProvider,
      indicator,
      MyLocalTrackerDiffHandler(document1, document2, indicator))
  }

  private inner class MyLocalTrackerDiffHandler(private val myDocument1: Document,
                                                private val myDocument2: Document,
                                                private val myIndicator: ProgressIndicator) : LocalTrackerDiffHandler {

    override fun done(isContentsEqual: Boolean,
                      texts: Array<CharSequence>,
                      toggleableLineRanges: List<ToggleableLineRange>): Runnable {

      val fragments = mutableListOf<LineFragment>()
      val fragmentsData = mutableListOf<LineFragmentData>()

      for (range in toggleableLineRanges) {
        val rangeFragments = range.fragments
        fragments.addAll(rangeFragments)
        fragmentsData.addAll(Collections.nCopies(rangeFragments.size, range.fragmentData))
      }

      val builder = runReadAction {
        myIndicator.checkCanceled()
        MyUnifiedFragmentBuilder(fragments, fragmentsData, myDocument1, myDocument2).exec()
      }

      val applyChanges = apply(builder, texts, myIndicator)
      val applyGutterExcludeOperations = applyGutterOperations(builder, toggleableLineRanges)

      return Runnable {
        applyChanges.run()
        applyGutterExcludeOperations.run()
      }
    }

    override fun retryLater(): Runnable {
      ApplicationManager.getApplication().invokeLater { scheduleRediff() }
      throw ProcessCanceledException()
    }

    override fun fallback(): Runnable {
      return superComputeDifferences(myIndicator)
    }

    override fun fallbackWithProgress(): Runnable {
      val callback = superComputeDifferences(myIndicator)
      return Runnable {
        callback.run()
        statusPanel.setBusy(true)
      }
    }

    override fun error(): Runnable {
      return applyErrorNotification()
    }
  }

  private inner class MyUnifiedFragmentBuilder(fragments: List<LineFragment>,
                                               private val myFragmentsData: List<LineFragmentData>,
                                               document1: Document,
                                               document2: Document
  ) : UnifiedFragmentBuilder(fragments, document1, document2, myMasterSide) {

    override fun createDiffChange(blockStart: Int,
                                  insertedStart: Int,
                                  blockEnd: Int,
                                  fragmentIndex: Int): UnifiedDiffChange {
      val fragment = fragments[fragmentIndex]
      val data = myFragmentsData[fragmentIndex]
      val isSkipped = data.isSkipped()
      val isExcluded = data.isExcluded(myAllowExcludeChangesFromCommit)
      return MyUnifiedDiffChange(blockStart, insertedStart, blockEnd, fragment, isExcluded, isSkipped,
                                 data.changelistId, data.isPartiallyExcluded(), data.exclusionState)
    }
  }

  override fun onAfterRediff() {
    super.onAfterRediff()
    myExcludeAllCheckboxPanel.refresh()
  }

  override fun clearDiffPresentation() {
    super.clearDiffPresentation()

    for (operation in myHighlighters) {
      operation.dispose()
    }
    myHighlighters.clear()

    myGutterCheckboxMouseMotionListener.destroyHoverHighlighter()
  }

  private fun applyGutterOperations(builder: UnifiedDiffState,
                                    toggleableLineRanges: List<ToggleableLineRange>): Runnable {
    return Runnable {
      if (myAllowExcludeChangesFromCommit) {
        for (toggleableLineRange in toggleableLineRanges) {
          myHighlighters.addAll(createGutterToggleRenderers(builder, toggleableLineRange))
        }
      }

      for (range in toggleableLineRanges) {
        ContainerUtil.addIfNotNull(myHighlighters, createClientIdHighlighter(builder, range))
      }

      if (!myHighlighters.isEmpty()) {
        editor.gutterComponentEx.revalidateMarkup()
      }
    }
  }

  private fun createGutterToggleRenderers(builder: UnifiedDiffState,
                                          toggleableLineRange: ToggleableLineRange): List<RangeHighlighter> {
    val fragmentData = toggleableLineRange.fragmentData
    if (!fragmentData.isFromActiveChangelist()) return emptyList()

    val result = mutableListOf<RangeHighlighter>()
    val exclusionState = fragmentData.exclusionState
    if (fragmentData.isPartiallyExcluded()) {
      val partialExclusionState = exclusionState as RangeExclusionState.Partial
      val lineRange = toggleableLineRange.lineRange

      partialExclusionState.iterateDeletionOffsets { start: Int, end: Int, isIncluded: Boolean ->
        for (i in start until end) {
          result.add(createLineCheckboxToggleHighlighter(builder, i + lineRange.start1, Side.LEFT, !isIncluded))
        }
      }
      partialExclusionState.iterateAdditionOffsets { start: Int, end: Int, isIncluded: Boolean ->
        for (i in start until end) {
          result.add(createLineCheckboxToggleHighlighter(builder, i + lineRange.start2, Side.RIGHT, !isIncluded))
        }
      }
    }
    else {
      result.add(createBlockCheckboxToggleHighlighter(builder, toggleableLineRange))
    }

    if (shouldShowToggleAreaThumb(toggleableLineRange)) {
      ContainerUtil.addIfNotNull(result, createToggleAreaThumb(builder, toggleableLineRange))
    }
    return result
  }

  private fun createBlockCheckboxToggleHighlighter(builder: UnifiedDiffState,
                                                   toggleableLineRange: ToggleableLineRange): RangeHighlighter {
    val side = Side.RIGHT
    val line = getSingleCheckBoxLine(toggleableLineRange, side)
    val isExcludedFromCommit = toggleableLineRange.fragmentData.exclusionState is RangeExclusionState.Excluded

    val lineConvertor = side.selectNotNull(builder.convertor1, builder.convertor2)
    val editorLine = lineConvertor.convertApproximateInv(line)

    return createCheckboxToggle(editor, editorLine, isExcludedFromCommit) {
      toggleBlockExclusion(myTrackerActionProvider, line, isExcludedFromCommit)
    }
  }

  private fun createLineCheckboxToggleHighlighter(builder: UnifiedDiffState,
                                                  line: Int, side: Side, isExcludedFromCommit: Boolean): RangeHighlighter {
    val lineConvertor = side.selectNotNull(builder.convertor1, builder.convertor2)
    val editorLine = lineConvertor.convertApproximateInv(line)

    return createCheckboxToggle(editor, editorLine, isExcludedFromCommit) {
      toggleLinePartialExclusion(myTrackerActionProvider, line, side, isExcludedFromCommit)
    }
  }

  private fun createToggleAreaThumb(builder: UnifiedDiffState,
                                    toggleableLineRange: ToggleableLineRange): RangeHighlighter? {
    val lineRange = toggleableLineRange.lineRange
    val line1 = builder.convertor1.convertApproximateInv(lineRange.start1)
    val line2 = builder.convertor2.convertApproximateInv(lineRange.end2)
    if (line1 < 0 || line2 < 0 || line2 <= line1 || line2 > DiffUtil.getLineCount(myDocument)) {
      LOG.warn("Failed to show toggle area thumb")
      return null
    }
    val isExcludedFromCommit = toggleableLineRange.fragmentData.exclusionState is RangeExclusionState.Excluded
    return createToggleAreaThumb(editor, line1, line2) {
      toggleBlockExclusion(myTrackerActionProvider, lineRange.start1, isExcludedFromCommit)
    }
  }

  private fun createClientIdHighlighter(builder: UnifiedDiffState,
                                        range: ToggleableLineRange): RangeHighlighter? {
    val clientIds = range.fragmentData.clientIds
    if (clientIds.isEmpty()) return null

    val iconRenderer = createClientIdGutterIconRenderer(myLocalRequest.project, clientIds)
    if (iconRenderer == null) return null

    val lineRange = range.lineRange
    val line1 = builder.convertor1.convertApproximateInv(lineRange.start1)
    val line2 = builder.convertor2.convertApproximateInv(lineRange.end2)
    if (line1 < 0 || line2 < 0 || line2 <= line1 || line2 > DiffUtil.getLineCount(myDocument)) {
      LOG.warn("Failed to show toggle area thumb")
      return null
    }

    val editor = editor
    val textRange = DiffUtil.getLinesRange(editor.document, line1, line2)
    return editor.markupModel
      .addRangeHighlighterAndChangeAttributes(null,
                                              textRange.startOffset, textRange.endOffset,
                                              DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                              HighlighterTargetArea.LINES_IN_RANGE,
                                              false) { rangeHighlighterEx: RangeHighlighterEx ->
        rangeHighlighterEx.isGreedyToLeft = true
        rangeHighlighterEx.isGreedyToRight = true
        rangeHighlighterEx.gutterIconRenderer = iconRenderer
      }
  }

  private class MyUnifiedDiffChange(blockStart: Int,
                                    insertedStart: Int,
                                    blockEnd: Int,
                                    lineFragment: LineFragment,
                                    isExcluded: Boolean,
                                    isSkipped: Boolean,
                                    val changelistId: @NonNls String,
                                    val isPartiallyExcluded: Boolean,
                                    val exclusionState: RangeExclusionState
  ) : UnifiedDiffChange(blockStart, insertedStart, blockEnd, lineFragment, isExcluded, isSkipped)

  private class MyUnifiedDiffChangeUi(viewer: UnifiedLocalChangeListDiffViewer,
                                      change: MyUnifiedDiffChange) : UnifiedDiffChangeUi(viewer, change) {

    private val viewer: UnifiedLocalChangeListDiffViewer
      get() = myViewer as UnifiedLocalChangeListDiffViewer

    private val change: MyUnifiedDiffChange
      get() = myChange as MyUnifiedDiffChange

    override fun installHighlighter() {
      if (change.isPartiallyExcluded && viewer.myAllowExcludeChangesFromCommit) {
        assert(myHighlighters.isEmpty() && myOperations.isEmpty())

        val deletionStart = myChange.deletedRange.start
        val additionStart = myChange.insertedRange.start

        val exclusionState = change.exclusionState as RangeExclusionState.Partial
        exclusionState.iterateDeletionOffsets { start: Int, end: Int, isIncluded: Boolean ->
          myHighlighters.addAll(
            LineHighlighterBuilder(myEditor,
                                   start + deletionStart,
                                   end + deletionStart,
                                   TextDiffType.DELETED)
              .withExcludedInEditor(myChange.isSkipped)
              .withExcludedInGutter(!isIncluded)
              .done())
        }
        exclusionState.iterateAdditionOffsets { start: Int, end: Int, isIncluded: Boolean ->
          myHighlighters.addAll(
            LineHighlighterBuilder(myEditor,
                                   start + additionStart,
                                   end + additionStart,
                                   TextDiffType.INSERTED)
              .withExcludedInEditor(myChange.isSkipped)
              .withExcludedInGutter(!isIncluded)
              .done())
        }

        // do not draw ">>"
        // doInstallActionHighlighters();
      }
      else {
        super.installHighlighter()
      }
    }
  }

  private class MyLocalTrackerActionProvider(private val myViewer: UnifiedLocalChangeListDiffViewer,
                                             localRequest: LocalChangeListDiffRequest,
                                             allowExcludeChangesFromCommit: Boolean
  ) : LocalTrackerActionProvider(myViewer, localRequest, allowExcludeChangesFromCommit) {

    override fun getSelectedTrackerChanges(e: AnActionEvent): List<LocalTrackerChange>? {
      if (!myViewer.isContentGood) return null

      return myViewer.selectedChanges.filterIsInstance<MyUnifiedDiffChange>().map {
        LocalTrackerChange(myViewer.transferLineFromOneside(Side.RIGHT, it.line1),
                           myViewer.transferLineFromOneside(Side.RIGHT, it.line2),
                           it.changelistId,
                           it.exclusionState)
      }
    }

    override fun getSelectedTrackerLines(e: AnActionEvent): SelectedTrackerLine? {
      if (!myViewer.isContentGood) return null

      val deletions = BitSet()
      val additions = BitSet()
      @Suppress("SSBasedInspection") // allow IntStream usage in Kotlin
      DiffUtil.getSelectedLines(myViewer.editor).stream().forEach { line: Int ->
        val line1 = myViewer.transferLineFromOnesideStrict(Side.LEFT, line)
        if (line1 != -1) deletions.set(line1)
        val line2 = myViewer.transferLineFromOnesideStrict(Side.RIGHT, line)
        if (line2 != -1) additions.set(line2)
      }

      return SelectedTrackerLine(deletions, additions)
    }
  }

  private inner class GutterCheckboxMouseMotionListener {
    private var myHighlighter: RangeHighlighter? = null

    fun install() {
      val listener = MyGutterMouseListener()
      myEditor.gutterComponentEx.addMouseListener(listener)
      myEditor.gutterComponentEx.addMouseMotionListener(listener)
    }

    fun destroyHoverHighlighter() {
      if (myHighlighter != null) {
        myHighlighter!!.dispose()
        myHighlighter = null
      }
    }

    private fun updateHoverHighlighter(editorLine: Int) {
      val changes = diffChanges
      if (changes == null) {
        destroyHoverHighlighter()
        return
      }

      val change = changes.find {
        it.line1 <= editorLine && it.line2 > editorLine
      } as? MyUnifiedDiffChange

      if (change == null ||
          change.isPartiallyExcluded ||
          myLocalRequest.changelistId != change.changelistId) {
        destroyHoverHighlighter()
        return
      }

      val rightLine = transferLineFromOnesideStrict(Side.RIGHT, editorLine)
      val leftLine = transferLineFromOnesideStrict(Side.LEFT, editorLine)

      val line: Int
      val side: Side
      if (rightLine != -1) {
        line = rightLine
        side = Side.RIGHT
      }
      else if (leftLine != -1) {
        line = leftLine
        side = Side.LEFT
      }
      else {
        destroyHoverHighlighter()
        return
      }

      if (hasIconHighlighters(myProject, myEditor, editorLine)) {
        if (myHighlighter != null && myEditor.document.getLineNumber(myHighlighter!!.startOffset) != editorLine) {
          destroyHoverHighlighter()
        }
        return
      }

      destroyHoverHighlighter()

      val isExcludedFromCommit = change.exclusionState is RangeExclusionState.Excluded
      myHighlighter = createCheckboxToggle(myEditor, editorLine, isExcludedFromCommit) {
        toggleLinePartialExclusion(myTrackerActionProvider, line, side, isExcludedFromCommit)
        destroyHoverHighlighter()
      }
    }

    private inner class MyGutterMouseListener : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        if (!myAllowExcludeChangesFromCommit) {
          destroyHoverHighlighter()
          return
        }

        val gutter = myEditor.gutterComponentEx
        val xOffset = if (DiffUtil.isMirrored(myEditor)) gutter.width - e.x else e.x
        if (xOffset < gutter.iconAreaOffset || xOffset > gutter.iconAreaOffset + gutter.iconsAreaWidth) {
          destroyHoverHighlighter()
          return
        }

        val position = myEditor.xyToLogicalPosition(e.point)
        updateHoverHighlighter(position.line)
      }

      override fun mouseExited(e: MouseEvent) {
        destroyHoverHighlighter()
      }
    }
  }
}
