// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.diff.DiffContext
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.fragmented.*
import com.intellij.diff.util.*
import com.intellij.diff.util.DiffDrawUtil.LineHighlighterBuilder
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
import com.intellij.openapi.vcs.changes.actions.diff.lst.UnifiedLocalChangeListDiffViewer.UnifiedLocalFragmentBuilder.LocalUnifiedDiffState
import com.intellij.openapi.vcs.changes.actions.diff.lst.UnifiedLocalChangeListDiffViewer.UnifiedLocalFragmentBuilder.ToggleRangeArea
import com.intellij.openapi.vcs.ex.RangeExclusionState
import com.intellij.openapi.vcs.ex.countAffectedVisibleChanges
import com.intellij.openapi.vcs.ex.createClientIdGutterIconRenderer
import com.intellij.util.containers.addIfNotNull
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.JComponent

@ApiStatus.Internal
class UnifiedLocalChangeListDiffViewer(context: DiffContext,
                                       private val localRequest: LocalChangeListDiffRequest)
  : UnifiedDiffViewer(context, localRequest.request) {
  private val isAllowExcludeChangesFromCommit: Boolean =
    DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT, context)

  private val trackerActionProvider: LocalTrackerActionProvider
  private val excludeAllCheckboxPanel = ExcludeAllCheckboxPanel(this, editor)
  private val gutterCheckboxMouseMotionListener: GutterCheckboxMouseMotionListener

  private val viewerHighlighters: MutableList<RangeHighlighter> = mutableListOf()

  init {
    trackerActionProvider = MyLocalTrackerActionProvider(this, localRequest, isAllowExcludeChangesFromCommit)
    excludeAllCheckboxPanel.init(localRequest, isAllowExcludeChangesFromCommit)

    installTrackerListener(this, localRequest)

    gutterCheckboxMouseMotionListener = GutterCheckboxMouseMotionListener()
    gutterCheckboxMouseMotionListener.install()

    for (action in createTrackerShortcutOnlyActions(trackerActionProvider)) {
      DiffUtil.registerAction(action, myPanel)
    }
  }

  override fun createTitles(): JComponent {
    val titles = super.createTitles()

    val titleWithCheckbox = JBUI.Panels.simplePanel()
    if (titles != null) titleWithCheckbox.addToCenter(titles)
    titleWithCheckbox.addToLeft(excludeAllCheckboxPanel)
    return titleWithCheckbox
  }

  override fun createEditorPopupActions(): List<AnAction> {
    return super.createEditorPopupActions() +
           createTrackerEditorPopupActions(trackerActionProvider)
  }

  override fun createUi(change: UnifiedDiffChange): UnifiedDiffChangeUi {
    if (change is MyUnifiedDiffChange) return MyUnifiedDiffChangeUi(this, change)
    return super.createUi(change)
  }

  override fun getStatusTextMessage(): @Nls String? {
    val allChanges = diffChanges
    if (isAllowExcludeChangesFromCommit && allChanges != null) {
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
      localRequest.lineStatusTracker,
      document1,
      document2,
      localRequest.changelistId,
      isAllowExcludeChangesFromCommit,
      myTextDiffProvider,
      indicator,
      MyLocalTrackerDiffHandler(document1, document2, indicator))
  }

  private inner class MyLocalTrackerDiffHandler(private val document1: Document,
                                                private val document2: Document,
                                                private val progressIndicator: ProgressIndicator) : LocalTrackerDiffHandler {

    override fun done(isContentsEqual: Boolean,
                      texts: Array<CharSequence>,
                      toggleableLineRanges: List<ToggleableLineRange>): Runnable {
      val builder = runReadAction {
        progressIndicator.checkCanceled()
        UnifiedLocalFragmentBuilder(document1, document2, myMasterSide, isAllowExcludeChangesFromCommit).exec(toggleableLineRanges)
      }

      val applyChanges = apply(builder, texts, progressIndicator)
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
      return superComputeDifferences(progressIndicator)
    }

    override fun fallbackWithProgress(): Runnable {
      val callback = superComputeDifferences(progressIndicator)
      return Runnable {
        callback.run()
        statusPanel.setBusy(true)
      }
    }

    override fun error(): Runnable {
      return applyErrorNotification()
    }
  }

  private class UnifiedLocalFragmentBuilder(document1: Document,
                                            document2: Document,
                                            masterSide: Side,
                                            val allowExcludeChangesFromCommit: Boolean
  ) : UnifiedFragmentBuilder(document1, document2, masterSide) {
    private val rangeAreas = mutableListOf<ToggleRangeArea>()

    fun exec(toggleableLineRanges: List<ToggleableLineRange>): LocalUnifiedDiffState {
      for (toggleableRange in toggleableLineRanges) {
        val data = toggleableRange.fragmentData
        val lineRange = toggleableRange.lineRange
        val isSkipped = data.isSkipped()
        val isExcluded = data.isExcluded(allowExcludeChangesFromCommit)

        val rangeStart = processEquals(lineRange.start1 - 1, lineRange.start2 - 1)
        for (fragment in toggleableRange.fragments) {
          val blockLineRange = processChanged(fragment.asLineRange())

          val change = MyUnifiedDiffChange(blockLineRange.blockStart, blockLineRange.insertedStart, blockLineRange.blockEnd,
                                           fragment, isExcluded, isSkipped,
                                           data.changelistId, data.isPartiallyExcluded(), data.exclusionState)
          reportChange(change)
        }
        val rangeEnd = processEquals(lineRange.end1 - 1, lineRange.end2 - 1)

        rangeAreas.add(ToggleRangeArea(rangeStart, rangeEnd))
      }
      finishDocuments()

      return LocalUnifiedDiffState(masterSide, textBuilder, changes, ranges,
                                   convertorBuilder1.build(), convertorBuilder2.build(),
                                   changedLines, rangeAreas)
    }

    class LocalUnifiedDiffState(masterSide: Side,
                                text: CharSequence,
                                changes: List<UnifiedDiffChange>,
                                ranges: List<HighlightRange>,
                                convertor1: LineNumberConvertor,
                                convertor2: LineNumberConvertor,
                                changedLines: List<LineRange>,
                                val rangeAreas: List<ToggleRangeArea>)
      : UnifiedDiffState(masterSide, text, changes, ranges, convertor1, convertor2, changedLines)

    /**
     * Affected lines in [myEditor], even if change is fully ignored.
     */
    class ToggleRangeArea(val line1: Int, val line2: Int)
  }

  override fun onAfterRediff() {
    super.onAfterRediff()
    excludeAllCheckboxPanel.refresh()
  }

  override fun clearDiffPresentation() {
    super.clearDiffPresentation()

    for (operation in viewerHighlighters) {
      operation.dispose()
    }
    viewerHighlighters.clear()

    gutterCheckboxMouseMotionListener.destroyHoverHighlighter()
  }

  private fun applyGutterOperations(builder: LocalUnifiedDiffState,
                                    toggleableLineRanges: List<ToggleableLineRange>): Runnable {
    return Runnable {
      toggleableLineRanges.forEachIndexed { index, toggleableLineRange ->
        val rangeArea = builder.rangeAreas[index]
        if (isAllowExcludeChangesFromCommit) {
          viewerHighlighters.addAll(createGutterToggleRenderers(builder, toggleableLineRange, rangeArea))
        }
        viewerHighlighters.addIfNotNull(createClientIdHighlighter(toggleableLineRange, rangeArea))
      }

      if (!viewerHighlighters.isEmpty()) {
        editor.gutterComponentEx.revalidateMarkup()
      }
    }
  }

  private fun createGutterToggleRenderers(builder: LocalUnifiedDiffState,
                                          toggleableLineRange: ToggleableLineRange,
                                          rangeArea: ToggleRangeArea): List<RangeHighlighter> {
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
      result.addIfNotNull(createToggleAreaThumb(toggleableLineRange, rangeArea))
    }
    return result
  }

  private fun createBlockCheckboxToggleHighlighter(builder: LocalUnifiedDiffState,
                                                   toggleableLineRange: ToggleableLineRange): RangeHighlighter {
    val side = Side.RIGHT
    val line = getSingleCheckBoxLine(toggleableLineRange, side)
    val isExcludedFromCommit = toggleableLineRange.fragmentData.exclusionState is RangeExclusionState.Excluded

    val lineConvertor = side.selectNotNull(builder.convertor1, builder.convertor2)
    val editorLine = lineConvertor.convertApproximateInv(line)

    return createCheckboxToggle(editor, editorLine, isExcludedFromCommit) {
      toggleBlockExclusion(trackerActionProvider, line, isExcludedFromCommit)
    }
  }

  private fun createLineCheckboxToggleHighlighter(builder: LocalUnifiedDiffState,
                                                  line: Int, side: Side, isExcludedFromCommit: Boolean): RangeHighlighter {
    val lineConvertor = side.selectNotNull(builder.convertor1, builder.convertor2)
    val editorLine = lineConvertor.convertApproximateInv(line)

    return createCheckboxToggle(editor, editorLine, isExcludedFromCommit) {
      toggleLinePartialExclusion(trackerActionProvider, line, side, isExcludedFromCommit)
    }
  }

  private fun createToggleAreaThumb(toggleableLineRange: ToggleableLineRange, rangeArea: ToggleRangeArea): RangeHighlighter? {
    val line1 = rangeArea.line1
    val line2 = rangeArea.line2
    if (line1 < 0 || line2 < 0 || line2 < line1 || line2 > DiffUtil.getLineCount(myDocument)) {
      LOG.warn("Failed to show toggle area thumb")
      return null
    }

    val localDocumentLine = toggleableLineRange.lineRange.start1
    val isExcludedFromCommit = toggleableLineRange.fragmentData.exclusionState is RangeExclusionState.Excluded
    return createToggleAreaThumb(editor, line1, line2) {
      toggleBlockExclusion(trackerActionProvider, localDocumentLine, isExcludedFromCommit)
    }
  }

  private fun createClientIdHighlighter(range: ToggleableLineRange,
                                        rangeArea: ToggleRangeArea): RangeHighlighter? {
    val clientIds = range.fragmentData.clientIds
    if (clientIds.isEmpty()) return null

    val iconRenderer = createClientIdGutterIconRenderer(localRequest.project, clientIds)
    if (iconRenderer == null) return null

    val line1 = rangeArea.line1
    val line2 = rangeArea.line2
    if (line1 < 0 || line2 < 0 || line2 < line1 || line2 > DiffUtil.getLineCount(myDocument)) {
      LOG.warn("Failed to client area renderer")
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

  private class MyUnifiedDiffChangeUi(val viewer: UnifiedLocalChangeListDiffViewer,
                                      val change: MyUnifiedDiffChange
  ) : UnifiedDiffChangeUi(viewer, change) {

    override fun installHighlighter() {
      if (change.isPartiallyExcluded && viewer.isAllowExcludeChangesFromCommit) {
        assert(myHighlighters.isEmpty() && myOperations.isEmpty())

        val deletionStart = change.deletedRange.start
        val additionStart = change.insertedRange.start

        val exclusionState = change.exclusionState as RangeExclusionState.Partial
        exclusionState.iterateDeletionOffsets { start: Int, end: Int, isIncluded: Boolean ->
          myHighlighters.addAll(
            LineHighlighterBuilder(myEditor,
                                   start + deletionStart,
                                   end + deletionStart,
                                   TextDiffType.DELETED)
              .withExcludedInEditor(change.isSkipped)
              .withExcludedInGutter(!isIncluded)
              .done())
        }
        exclusionState.iterateAdditionOffsets { start: Int, end: Int, isIncluded: Boolean ->
          myHighlighters.addAll(
            LineHighlighterBuilder(myEditor,
                                   start + additionStart,
                                   end + additionStart,
                                   TextDiffType.INSERTED)
              .withExcludedInEditor(change.isSkipped)
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

  private class MyLocalTrackerActionProvider(override val viewer: UnifiedLocalChangeListDiffViewer,
                                             override val localRequest: LocalChangeListDiffRequest,
                                             override val allowExcludeChangesFromCommit: Boolean
  ) : LocalTrackerActionProvider {

    override fun getSelectedTrackerChanges(e: AnActionEvent): List<LocalTrackerChange>? {
      if (!viewer.isContentGood) return null

      return viewer.selectedChanges.filterIsInstance<MyUnifiedDiffChange>().map {
        LocalTrackerChange(viewer.transferLineFromOneside(Side.RIGHT, it.line1),
                           viewer.transferLineFromOneside(Side.RIGHT, it.line2),
                           it.changelistId,
                           it.exclusionState)
      }
    }

    override fun getSelectedTrackerLines(e: AnActionEvent): SelectedTrackerLine? {
      if (!viewer.isContentGood) return null

      val deletions = BitSet()
      val additions = BitSet()
      @Suppress("SSBasedInspection") // allow IntStream usage in Kotlin
      DiffUtil.getSelectedLines(viewer.editor).stream().forEach { line: Int ->
        val line1 = viewer.transferLineFromOnesideStrict(Side.LEFT, line)
        if (line1 != -1) deletions.set(line1)
        val line2 = viewer.transferLineFromOnesideStrict(Side.RIGHT, line)
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

      val change = changes.find { it.line1 <= editorLine && it.line2 > editorLine } as? MyUnifiedDiffChange

      if (change == null ||
          change.isPartiallyExcluded ||
          localRequest.changelistId != change.changelistId) {
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
        toggleLinePartialExclusion(trackerActionProvider, line, side, isExcludedFromCommit)
        destroyHoverHighlighter()
      }
    }

    private inner class MyGutterMouseListener : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        if (!isAllowExcludeChangesFromCommit) {
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
