// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.codeWithMe.ClientId
import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider
import com.intellij.diff.util.*
import com.intellij.diff.util.Range
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diff.DiffBundle
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.ex.MarkupModelEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.*
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.ui.DirtyUI
import com.intellij.ui.InplaceButton
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.CommonProcessors.FindProcessor
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.awt.event.MouseEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

@ApiStatus.Internal
object LocalTrackerDiffUtil {
  @JvmStatic
  fun computeDifferences(tracker: LineStatusTracker<*>?,
                         document1: Document,
                         document2: Document,
                         activeChangelistId: String,
                         allowExcludeChangesFromCommit: Boolean,
                         textDiffProvider: TwosideTextDiffProvider,
                         indicator: ProgressIndicator,
                         handler: LocalTrackerDiffHandler): Runnable {
    if (tracker is SimpleLocalLineStatusTracker) {
      // partial changes are disabled for file (ex: it is marked as "unmodified")
      return handler.fallback()
    }

    val partialTracker = tracker as? PartialLocalLineStatusTracker
    if (partialTracker == null || document2 != tracker.document) {
      return handler.error() // DiffRequest is out of date
    }

    indicator.checkCanceled()
    val data = runReadAction {
      partialTracker.readLock {
        if (partialTracker.isReleased) return@readLock TrackerData.Released

        val affectedChangelistIds = partialTracker.getAffectedChangeListsIds()

        val ranges = partialTracker.getRanges()
        if (ranges == null) {
          val hasPendingUpdate = LineStatusTrackerManager.getInstanceImpl(tracker.project).hasPendingUpdate(tracker.document)
          TrackerData.Invalid(affectedChangelistIds, hasPendingUpdate)
        }
        else {
          val localText = document2.immutableCharSequence
          val vcsText = document1.immutableCharSequence
          val trackerVcsText = partialTracker.vcsDocument.immutableCharSequence

          val diffData = TrackerDiffData(ranges, localText, vcsText, trackerVcsText)
          TrackerData.Valid(affectedChangelistIds, diffData)
        }
      }
    }

    return when (data) {
      is TrackerData.Released -> handler.error() // DiffRequest is out of date
      is TrackerData.Invalid -> {
        if (data.affectedChangelist.singleOrNull() == activeChangelistId) {
          if (data.isLoading) {
            // tracker is waiting for initialisation
            // there are only one changelist, so it's safe to fallback to default logic
            handler.fallbackWithProgress()
          }
          else {
            handler.fallback() // ex: file is unchanged
          }
        } else handler.retryLater()
      }
      is TrackerData.Valid -> {
        handleValidData(data, handler, textDiffProvider, indicator, activeChangelistId, allowExcludeChangesFromCommit)
      }
    }
  }

  private fun handleValidData(
    data: TrackerData.Valid,
    handler: LocalTrackerDiffHandler,
    textDiffProvider: TwosideTextDiffProvider,
    indicator: ProgressIndicator,
    activeChangelistId: String,
    allowExcludeChangesFromCommit: Boolean,
  ): Runnable {
    val diffData = data.diffData
    val ranges = diffData.ranges
    val isContentsEqual = ranges.isEmpty()
    val texts = arrayOf(diffData.vcsText, diffData.localText)

    if (!StringUtil.equals(diffData.vcsText, diffData.trackerVcsText)) {
      return handler.error() // DiffRequest is out of date
    }

    if (textDiffProvider.isHighlightingDisabled) {
      return handler.done(isContentsEqual, texts, emptyList())
    }

    val lineOffsets1 = LineOffsetsUtil.create(diffData.vcsText)
    val lineOffsets2 = LineOffsetsUtil.create(diffData.localText)

    val linesRanges = ranges.map { Range(it.vcsLine1, it.vcsLine2, it.line1, it.line2) }
    val newFragments = textDiffProvider.compare(diffData.vcsText, diffData.localText, linesRanges, indicator)!!

    val toggleableLineRanges = mutableListOf<ToggleableLineRange>()
    for (i in ranges.indices) {
      val localRange = ranges[i]
      val lineRange = linesRanges[i]

      val rangesFragmentData = LineFragmentData(activeChangelistId, localRange.changelistId, localRange.exclusionState,
                                                localRange.clientIds)
      if (rangesFragmentData.isPartiallyExcluded() && allowExcludeChangesFromCommit) {
        val fragment = ComparisonManagerImpl.createLineFragment(lineRange.start1, lineRange.end1,
                                                                lineRange.start2, lineRange.end2,
                                                                lineOffsets1, lineOffsets2)
        toggleableLineRanges += ToggleableLineRange(lineRange, listOf(fragment), rangesFragmentData)
      }
      else {
        toggleableLineRanges += ToggleableLineRange(lineRange, newFragments[i], rangesFragmentData)
      }
    }
    return handler.done(isContentsEqual, texts, toggleableLineRanges)
  }

  interface LocalTrackerDiffHandler {
    fun done(isContentsEqual: Boolean,
             texts: Array<CharSequence>,
             toggleableLineRanges: List<ToggleableLineRange>): Runnable

    fun retryLater(): Runnable
    fun fallback(): Runnable
    fun fallbackWithProgress(): Runnable
    fun error(): Runnable
  }

  class ToggleableLineRange(
    val lineRange: Range,
    val fragments: List<LineFragment>,
    val fragmentData: LineFragmentData
  )

  data class LineFragmentData(
    val activeChangelistId: String,
    val changelistId: String,
    val exclusionState: RangeExclusionState,
    val clientIds: List<ClientId>
  ) {
    fun isFromActiveChangelist() = changelistId == activeChangelistId
    fun isSkipped() = !isFromActiveChangelist()
    fun isExcluded(allowExcludeChangesFromCommit: Boolean) = !isFromActiveChangelist() ||
                                                             allowExcludeChangesFromCommit && exclusionState == RangeExclusionState.Excluded

    fun isPartiallyExcluded(): Boolean = isFromActiveChangelist() && exclusionState is RangeExclusionState.Partial
  }

  private sealed interface TrackerData {
    data object Released : TrackerData

    data class Invalid(
      val affectedChangelist: List<String>,
      val isLoading: Boolean,
    ) : TrackerData

    data class Valid(
      val affectedChangelist: List<String>,
      val diffData: TrackerDiffData,
    ) : TrackerData
  }

  private data class TrackerDiffData(
    val ranges: List<LocalRange>,
    val localText: CharSequence,
    val vcsText: CharSequence,
    val trackerVcsText: CharSequence,
  )

  @JvmStatic
  fun installTrackerListener(viewer: DiffViewerBase, localRequest: LocalChangeListDiffRequest) {
    val trackerListener = MyTrackerListener(viewer)
    val lstmListener = MyLineStatusTrackerManagerListener(viewer, localRequest, trackerListener)

    LineStatusTrackerManager.getInstanceImpl(localRequest.project).addTrackerListener(lstmListener, viewer)

    val tracker = localRequest.partialTracker
    if (tracker != null) tracker.addListener(trackerListener, viewer)
  }

  private class MyTrackerListener(private val viewer: DiffViewerBase)
    : PartialLocalLineStatusTracker.ListenerAdapter() {

    override fun onBecomingValid(tracker: PartialLocalLineStatusTracker) = scheduleRediff()
    override fun onChangeListMarkerChange(tracker: PartialLocalLineStatusTracker) = scheduleRediff()
    override fun onExcludedFromCommitChange(tracker: PartialLocalLineStatusTracker) = scheduleRediff()

    private fun scheduleRediff() {
      runInEdt { viewer.scheduleRediff() }
    }
  }

  private class MyLineStatusTrackerManagerListener(private val viewer: DiffViewerBase,
                                                   private val localRequest: LocalChangeListDiffRequest,
                                                   private val trackerListener: PartialLocalLineStatusTracker.Listener)
    : LineStatusTrackerManager.ListenerAdapter() {

    override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
      if (tracker is PartialLocalLineStatusTracker && tracker.virtualFile == localRequest.virtualFile) {
        tracker.addListener(trackerListener, viewer)
        viewer.scheduleRediff()
      }
    }
  }

  @JvmStatic
  fun createCheckboxToggle(editor: EditorEx, line: Int, isExcludedFromCommit: Boolean, onClick: Runnable): RangeHighlighter {
    val offset = DiffGutterOperation.lineToOffset(editor, line)
    val icon = if (isExcludedFromCommit) AllIcons.Diff.GutterCheckBox else AllIcons.Diff.GutterCheckBoxSelected
    val checkboxHighlighter = editor.markupModel.addRangeHighlighter(null, offset, offset,
                                                                     HighlighterLayer.ADDITIONAL_SYNTAX,
                                                                     HighlighterTargetArea.LINES_IN_RANGE)
    var message = DiffBundle.message("action.presentation.diff.include.into.commit.text")
    val shortcut = ActionManager.getInstance().getKeyboardShortcut("Vcs.Diff.IncludeWholeChangedLinesIntoCommit")
    if (shortcut != null) {
      message += " (${KeymapUtil.getShortcutText(shortcut)})"
    }
    checkboxHighlighter.gutterIconRenderer = CheckboxDiffGutterRenderer(icon, message, onClick)
    return checkboxHighlighter
  }

  private class CheckboxDiffGutterRenderer(
    icon: Icon,
    tooltip: @NlsContexts.Tooltip String?,
    val onClick: Runnable
  ) : DiffGutterRenderer(icon, tooltip) {
    override fun handleMouseClick() {
      onClick.run()
    }
  }

  @JvmStatic
  fun toggleBlockExclusion(provider: LocalTrackerActionProvider, line: Int, wasExcludedFromCommit: Boolean) {
    val tracker = provider.localRequest.partialTracker ?: return

    val range = tracker.getRangeForLine(line) ?: return
    tracker.setExcludedFromCommit(range, !wasExcludedFromCommit)

    provider.viewer.rediff()
  }

  @JvmStatic
  fun toggleLinePartialExclusion(provider: LocalTrackerActionProvider, line: Int, side: Side, wasExcludedFromCommit: Boolean) {
    val tracker = provider.localRequest.partialTracker ?: return

    val lines = BitSet()
    lines.set(line)
    tracker.setPartiallyExcludedFromCommit(lines, side, !wasExcludedFromCommit)

    provider.viewer.rediff()
  }

  /**
   * @param includedIntoCommitCount Changes that belong into current changelist AND included for commit
   * @param excludedCount Changes that belong to another changelist
   */
  @JvmStatic
  fun getStatusText(totalCount: Int, includedIntoCommitCount: Int, excludedCount: Int, isContentsEqual: ThreeState): @Nls String {
    if (totalCount == 0 && isContentsEqual == ThreeState.NO) {
      return DiffBundle.message("diff.all.differences.ignored.text")
    }

    val actualChanges = totalCount - excludedCount
    var message = DiffBundle.message("diff.count.differences.status.text", totalCount - excludedCount)
    if (includedIntoCommitCount != actualChanges) message += DiffBundle.message("diff.included.count.differences.status.text",
                                                                                includedIntoCommitCount)
    if (excludedCount > 0) message += " " + DiffBundle.message("diff.inactive.count.differences.status.text", excludedCount)
    return message
  }

  @JvmStatic
  fun shouldShowToggleAreaThumb(toggleableLineRange: ToggleableLineRange): Boolean {
    if (toggleableLineRange.fragmentData.isPartiallyExcluded()) return false

    val lines1 = BitSet()
    val lines2 = BitSet()
    // do not show thumb for HighlightPolicy.BY_WORD_SPLIT mode
    for (fragment in toggleableLineRange.fragments) {
      lines1.set(fragment.startLine1, fragment.endLine1)
      lines2.set(fragment.startLine2, fragment.endLine2)
    }

    val lineRange = toggleableLineRange.lineRange
    return lines1.nextClearBit(lineRange.start1) != lineRange.end1 ||
           lines2.nextClearBit(lineRange.start2) != lineRange.end2
  }

  @JvmStatic
  fun getSingleCheckBoxLine(toggleableLineRange: ToggleableLineRange, side: Side): Int {
    val firstFragment = toggleableLineRange.fragments.firstOrNull()
    if (firstFragment != null) return side.getStartLine(firstFragment)

    val lineRange = toggleableLineRange.lineRange
    return side.select(lineRange.start1, lineRange.start2)
  }

  @JvmStatic
  fun createToggleAreaThumb(editor: EditorEx, line1: Int, line2: Int, onClick: Runnable): RangeHighlighter {
    val range = DiffUtil.getLinesRange(editor.document, line1, line2)
    val checkboxHighlighter = editor.markupModel.addRangeHighlighter(null, range.startOffset, range.endOffset,
                                                                     DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                                     HighlighterTargetArea.LINES_IN_RANGE)
    checkboxHighlighter.lineMarkerRenderer = ToggleAreaThumbRenderer(onClick)
    return checkboxHighlighter
  }

  @JvmStatic
  fun hasIconHighlighters(project: Project?, editor: EditorEx, line: Int): Boolean {
    if (hasIconHighlighters(editor, line, editor.markupModel)) return true

    val documentModel = DocumentMarkupModel.forDocument(editor.document, project, false) as MarkupModelEx
    return hasIconHighlighters(editor, line, documentModel)
  }

  private fun hasIconHighlighters(editor: EditorEx, line: Int, markupModelEx: MarkupModelEx): Boolean {
    val processor = object : FindProcessor<RangeHighlighter>() {
      override fun accept(it: RangeHighlighter): Boolean {
        return it.getGutterIconRenderer() != null &&
               it.editorFilter.avaliableIn(editor)
      }
    }

    val document = editor.getDocument()
    val start = document.getLineStartOffset(line)
    val end = document.getLineEndOffset(line)
    markupModelEx.processRangeHighlightersOverlappingWith(start, end, processor)
    return processor.isFound
  }

  private class ToggleAreaThumbRenderer(val onClick: Runnable) : LineMarkerRenderer, ActiveGutterRenderer {
    private fun getDrawArea(editor: Editor): Pair<Int, Int> {
      val gutter = (editor as EditorEx).gutterComponentEx
      val width = JBUIScale.scale(3)
      val x = gutter.whitespaceSeparatorOffset - width
      return Pair(x, x + width)
    }

    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      val (x1, x2) = getDrawArea(editor)
      g.color = editor.colorsScheme.getColor(EditorColors.DIFF_BLOCK_AREA_HIGHLIGHT_MARKER) ?: return
      g.fillRect(x1, r.y, x2 - x1, r.height)
    }

    override fun getTooltipText(): String {
      return DiffBundle.message("action.presentation.diff.include.into.commit.area.marker.text")
    }

    override fun canDoAction(editor: Editor, e: MouseEvent): Boolean {
      val (x1, x2) = getDrawArea(editor)
      return e.x in x1 until x2
    }

    override fun doAction(editor: Editor, e: MouseEvent) {
      onClick.run()
    }
  }

  @JvmStatic
  fun createTrackerEditorPopupActions(provider: LocalTrackerActionProvider): List<AnAction> {
    return listOf(MoveSelectedChangesToAnotherChangelistAction(provider),
                  PartiallyExcludeSelectedLinesFromCommitAction(provider, false),
                  PartiallyExcludeSelectedLinesFromCommitAction(provider, true))
  }

  @JvmStatic
  fun createTrackerShortcutOnlyActions(provider: LocalTrackerActionProvider): List<AnAction> {
    return listOf(ExcludeSelectedChangesFromCommitAction(provider))
  }

  private class MoveSelectedChangesToAnotherChangelistAction(provider: LocalTrackerActionProvider)
    : MySelectedChangesActionBase(false, provider) {

    init {
      copyShortcutFrom(ActionManager.getInstance().getAction("Vcs.MoveChangedLinesToChangelist"))
    }

    override fun getText(changes: List<LocalTrackerChange>): String {
      if (changes.isNotEmpty() && changes.all { !it.isFromActiveChangelist }) {
        val shortChangeListName = StringUtil.trimMiddle(provider.localRequest.changelistName, 40)
        return VcsBundle.message("changes.move.to.changelist", StringUtil.escapeMnemonics(shortChangeListName))
      }
      else {
        return ActionsBundle.message("action.ChangesView.Move.text")
      }
    }

    override fun doPerform(e: AnActionEvent,
                           tracker: PartialLocalLineStatusTracker,
                           changes: List<LocalTrackerChange>) {
      val selectedLines = getLocalSelectedLines(changes)

      if (changes.all { !it.isFromActiveChangelist }) {
        val changeList = ChangeListManager.getInstance(tracker.project).getChangeList(activeChangelistId)
        if (changeList != null) tracker.moveToChangelist(selectedLines, changeList)
      }
      else {
        MoveChangesLineStatusAction.moveToAnotherChangelist(tracker, selectedLines)
      }

      provider.viewer.rediff()
    }
  }

  private class ExcludeSelectedChangesFromCommitAction(provider: LocalTrackerActionProvider)
    : MySelectedChangesActionBase(true, provider) {

    init {
      ActionUtil.copyFrom(this, "Vcs.Diff.IncludeWholeChangedLinesIntoCommit")
    }

    override fun getText(changes: List<LocalTrackerChange>): String {
      val hasExcluded = changes.any { it.exclusionState.hasExcluded }
      return if (changes.isNotEmpty() && !hasExcluded) VcsBundle.message("changes.exclude.lines.from.commit")
      else VcsBundle.message("changes.include.lines.into.commit")
    }

    override fun doPerform(e: AnActionEvent,
                           tracker: PartialLocalLineStatusTracker,
                           changes: List<LocalTrackerChange>) {
      val selectedLines = getLocalSelectedLines(changes)

      val hasExcluded = changes.any { it.exclusionState.hasExcluded }
      tracker.setExcludedFromCommit(selectedLines, !hasExcluded)

      provider.viewer.rediff()
    }
  }

  private abstract class MySelectedChangesActionBase(private val forActiveChangelistOnly: Boolean,
                                                     protected val provider: LocalTrackerActionProvider) : DumbAwareAction() {
    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      if (forActiveChangelistOnly && !provider.allowExcludeChangesFromCommit) {
        e.presentation.isEnabledAndVisible = false
        return
      }

      if (DiffUtil.isFromShortcut(e)) {
        e.presentation.isEnabledAndVisible = true
        return
      }

      val tracker = provider.localRequest.partialTracker
      val affectedChanges = getAffectedChanges(e)
      if (tracker == null || affectedChanges == null) {
        e.presentation.isVisible = true
        e.presentation.isEnabled = false
        e.presentation.text = getText(emptyList())
        return
      }

      e.presentation.isVisible = true
      e.presentation.isEnabled = affectedChanges.isNotEmpty()
      e.presentation.text = getText(affectedChanges)
    }

    override fun actionPerformed(e: AnActionEvent) {
      val tracker = provider.localRequest.partialTracker ?: return
      val affectedChanges = getAffectedChanges(e) ?: return
      if (affectedChanges.isEmpty()) return

      doPerform(e, tracker, affectedChanges)
    }

    private fun getAffectedChanges(e: AnActionEvent): List<LocalTrackerChange>? {
      val changes = provider.getSelectedTrackerChanges(e) ?: return null
      if (forActiveChangelistOnly) {
        return changes.filter { it.isFromActiveChangelist }
      }
      else {
        return changes
      }
    }

    protected open fun getText(changes: List<LocalTrackerChange>): @Nls String {
      return templatePresentation.text
    }

    protected val LocalTrackerChange.isFromActiveChangelist get() = changelistId == activeChangelistId
    protected val activeChangelistId get() = provider.localRequest.changelistId

    @RequiresWriteLock
    protected abstract fun doPerform(e: AnActionEvent,
                                     tracker: PartialLocalLineStatusTracker,
                                     changes: List<LocalTrackerChange>)
  }

  private class PartiallyExcludeSelectedLinesFromCommitAction(
    val provider: LocalTrackerActionProvider,
    val isExclude: Boolean
  ) : DumbAwareAction() {
    init {
      ActionUtil.copyFrom(this, if (isExclude) "Vcs.Diff.ExcludeChangedLinesFromCommit" else "Vcs.Diff.IncludeChangedLinesIntoCommit")
    }

    override fun getActionUpdateThread(): ActionUpdateThread {
      return ActionUpdateThread.EDT
    }

    override fun update(e: AnActionEvent) {
      if (!provider.allowExcludeChangesFromCommit) {
        e.presentation.isEnabledAndVisible = false
        return
      }

      if (DiffUtil.isFromShortcut(e)) {
        e.presentation.isEnabledAndVisible = true
        return
      }

      val selectedLines = provider.getSelectedTrackerLines(e)
      if (selectedLines == null) {
        e.presentation.isEnabledAndVisible = false
        return
      }

      val affectedRanges = getAffectedRanges(selectedLines)
      val isPartialBlockSelection = affectedRanges.any { isPartiallySelected(it, selectedLines) }

      if (affectedRanges.isEmpty()) {
        e.presentation.text = when {
          isExclude -> ActionsBundle.message("action.Vcs.Diff.ExcludeChangedLinesFromCommit.text")
          else -> ActionsBundle.message("action.Vcs.Diff.IncludeChangedLinesIntoCommit.text")
        }
        e.presentation.isEnabled = false
      }
      else if (isPartialBlockSelection) {
        val willSplit = affectedRanges.any { it.exclusionState !is RangeExclusionState.Partial }
        val rangesCount = if (willSplit) affectedRanges.size else 0
        val selectionSize = (selectedLines.localLines?.cardinality() ?: 0) + (selectedLines.vcsLines?.cardinality() ?: 0)
        e.presentation.text = when {
          isExclude -> ActionsBundle.message("action.Vcs.Diff.ExcludeChangedLinesFromCommit.template.text", rangesCount, selectionSize)
          else -> ActionsBundle.message("action.Vcs.Diff.IncludeChangedLinesIntoCommit.template.text", rangesCount, selectionSize)
        }

        e.presentation.isEnabled = affectedRanges.any {
          val selectionState = checkPartialSelectionState(it, selectedLines)
          if (isExclude) selectionState.hasIncluded else selectionState.hasExcluded
        }
      }
      else {
        e.presentation.text = when {
          isExclude -> VcsBundle.message("changes.ExcludeChangedLinesFromCommit.chunks.action.text")
          else -> VcsBundle.message("changes.IncludeChangedLinesIntoCommit.chunks.action.text")
        }

        e.presentation.isEnabled = affectedRanges.any {
          val exclusionState = it.exclusionState
          if (isExclude) exclusionState.hasIncluded else exclusionState.hasExcluded
        }
      }
    }

    override fun actionPerformed(e: AnActionEvent) {
      val selectedLines = provider.getSelectedTrackerLines(e) ?: return
      val tracker = provider.localRequest.partialTracker ?: return
      val affectedRanges = getAffectedRanges(selectedLines)
      val isPartialBlockSelection = affectedRanges.any { isPartiallySelected(it, selectedLines) }

      if (isPartialBlockSelection) {
        if (selectedLines.vcsLines != null) tracker.setPartiallyExcludedFromCommit(selectedLines.vcsLines, Side.LEFT, isExclude)
        if (selectedLines.localLines != null) tracker.setPartiallyExcludedFromCommit(selectedLines.localLines, Side.RIGHT, isExclude)
      }
      else {
        affectedRanges.forEach { tracker.setExcludedFromCommit(it, isExclude) }
      }

      provider.viewer.rediff()
    }

    private fun getAffectedRanges(selectedLines: SelectedTrackerLine): List<LocalRange> {
      val activeChangelistId = provider.localRequest.changelistId
      val tracker = provider.localRequest.partialTracker ?: return emptyList()

      return tracker.getRanges().orEmpty()
        .filter { change -> change.changelistId == activeChangelistId }
        .filter { isSelected(it, selectedLines) }
    }

    private fun isSelected(range: LocalRange, selectedLines: SelectedTrackerLine): Boolean {
      return selectedLines.vcsLines != null && DiffUtil.isSelectedByLine(selectedLines.vcsLines, range.vcsLine1, range.vcsLine2) ||
             selectedLines.localLines != null && DiffUtil.isSelectedByLine(selectedLines.localLines, range.line1, range.line2)
    }

    private fun isPartiallySelected(range: LocalRange, selectedLines: SelectedTrackerLine): Boolean {
      if (range.exclusionState is RangeExclusionState.Partial) return true

      if (selectedLines.localLines != null && range.line1 != range.line2 &&
          !selectionCovers(selectedLines.localLines, range.line1, range.line2)) {
        return true
      }
      if (selectedLines.vcsLines != null && range.vcsLine1 != range.vcsLine2 &&
          !selectionCovers(selectedLines.vcsLines, range.vcsLine1, range.vcsLine2)) {
        return true
      }
      return false
    }

    private fun checkPartialSelectionState(range: LocalRange, selectedLines: SelectedTrackerLine): SelectionState {
      val exclusionState = range.exclusionState
      if (exclusionState !is RangeExclusionState.Partial) {
        return SelectionState(exclusionState.hasExcluded, exclusionState.hasIncluded)
      }

      var hasExcluded = false
      var hasIncluded = false
      if (selectedLines.localLines != null) {
        val changeStart = range.line1
        exclusionState.iterateAdditionOffsets { start, end, isIncluded ->
          if (selectionIntersects(selectedLines.localLines, changeStart + start, changeStart + end)) {
            if (isIncluded) {
              hasIncluded = true
            }
            else {
              hasExcluded = true
            }
          }
        }
      }

      if (selectedLines.vcsLines != null) {
        val changeStart = range.vcsLine1
        exclusionState.iterateDeletionOffsets { start, end, isIncluded ->
          if (selectionIntersects(selectedLines.vcsLines, changeStart + start, changeStart + end)) {
            if (isIncluded) {
              hasIncluded = true
            }
            else {
              hasExcluded = true
            }
          }
        }
      }

      return SelectionState(hasExcluded, hasIncluded)
    }

    private fun selectionCovers(selection: BitSet, startLine: Int, endLine: Int): Boolean {
      return selection.nextClearBit(startLine) >= endLine
    }

    private fun selectionIntersects(selection: BitSet, startLine: Int, endLine: Int): Boolean {
      val nextSetBit = selection.nextSetBit(startLine)
      return nextSetBit != -1 && nextSetBit < endLine
    }

    private class SelectionState(val hasExcluded: Boolean, val hasIncluded: Boolean)
  }

  private fun getLocalSelectedLines(changes: List<LocalTrackerChange>): BitSet {
    val selectedLines = BitSet()
    for (change in changes) {
      val startLine = change.startLine
      val endLine = change.endLine
      selectedLines.set(startLine, if (startLine == endLine) startLine + 1 else endLine)
    }
    return selectedLines
  }

  @DirtyUI
  class ExcludeAllCheckboxPanel(private val viewer: DiffViewerBase, private val editor: EditorEx)
    : JPanel(BorderLayout()) {

    private var localRequest: LocalChangeListDiffRequest? = null
    private val checkbox: InplaceButton

    private val icon: Icon?
      get() {
        val tracker = localRequest?.partialTracker
        if (tracker == null || !tracker.isValid()) return null

        when (tracker.getExcludedFromCommitState(localRequest!!.changelistId)) {
          ExclusionState.ALL_INCLUDED -> return AllIcons.Diff.GutterCheckBoxSelected
          ExclusionState.ALL_EXCLUDED -> return AllIcons.Diff.GutterCheckBox
          ExclusionState.PARTIALLY -> return AllIcons.Diff.GutterCheckBoxIndeterminate
          ExclusionState.NO_CHANGES -> return null
          else -> return null
        }
      }

    init {
      checkbox = InplaceButton(null, AllIcons.Diff.GutterCheckBox) { toggleState() }
      checkbox.cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
      checkbox.isVisible = false
      add(checkbox, BorderLayout.CENTER)

      editor.gutterComponentEx.addComponentListener(object : ComponentAdapter() {
        override fun componentResized(e: ComponentEvent?) {
          ApplicationManager.getApplication().invokeLater({ updateLayout() }, ModalityState.any())
        }
      })
    }

    fun init(localRequest: LocalChangeListDiffRequest, allowExcludeChangesFromCommit: Boolean) {
      if (allowExcludeChangesFromCommit) {
        this.localRequest = localRequest
      }
    }

    override fun doLayout() {
      val size = checkbox.preferredSize
      val gutter = editor.gutterComponentEx
      val gap = height - size.height
      val y = if (gap > JBUI.scale(8)) gap - JBUI.scale(2) else gap / 2
      val x = gutter.iconAreaOffset + 2 // "+2" from EditorGutterComponentImpl.processIconsRow
      checkbox.setBounds(min(width - AllIcons.Diff.GutterCheckBox.iconWidth, x), max(0, y), size.width, size.height)
    }

    override fun getPreferredSize(): Dimension {
      if (!checkbox.isVisible) return Dimension()
      val size = checkbox.preferredSize
      val gutter = editor.gutterComponentEx
      val gutterWidth = gutter.lineMarkerFreePaintersAreaOffset
      return Dimension(max(gutterWidth + JBUIScale.scale(2), size.width), size.height)
    }

    private fun updateLayout() {
      invalidate()
      viewer.component.validate()
      viewer.component.repaint()
    }

    private fun toggleState() {
      val tracker = localRequest?.partialTracker
      if (tracker != null && tracker.isValid()) {
        val exclusionState = tracker.getExcludedFromCommitState(localRequest!!.changelistId)
        tracker.setExcludedFromCommit(localRequest!!.changelistId, exclusionState === ExclusionState.ALL_INCLUDED)
        refresh()
        viewer.rediff()
      }
    }

    fun refresh() {
      val icon = icon
      if (icon != null) {
        checkbox.icon = icon
        checkbox.isVisible = true
      }
      else {
        checkbox.isVisible = false
      }
    }
  }


  interface LocalTrackerActionProvider {
    val viewer: DiffViewerBase
    val localRequest: LocalChangeListDiffRequest
    val allowExcludeChangesFromCommit: Boolean

    fun getSelectedTrackerChanges(e: AnActionEvent): List<LocalTrackerChange>?
    fun getSelectedTrackerLines(e: AnActionEvent): SelectedTrackerLine?
  }

  class SelectedTrackerLine(
    val vcsLines: BitSet?,
    val localLines: BitSet?
  )

  class LocalTrackerChange(val startLine: Int, val endLine: Int, val changelistId: String, val exclusionState: RangeExclusionState)

  private val LocalChangeListDiffRequest.partialTracker get() = lineStatusTracker as? PartialLocalLineStatusTracker
}
