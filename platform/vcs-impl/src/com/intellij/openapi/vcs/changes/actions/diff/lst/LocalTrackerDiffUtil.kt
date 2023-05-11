// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.diff.comparison.ComparisonManagerImpl
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.fragments.LineFragmentImpl
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.text.LineOffsetsUtil
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider
import com.intellij.diff.util.DiffDrawUtil
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Range
import com.intellij.diff.util.Side
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
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.LineMarkerRenderer
import com.intellij.openapi.editor.markup.RangeHighlighter
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.ex.*
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.ui.DirtyUI
import com.intellij.ui.InplaceButton
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.Nls
import java.awt.*
import java.awt.event.ComponentAdapter
import java.awt.event.ComponentEvent
import java.util.*
import javax.swing.Icon
import javax.swing.JPanel
import kotlin.math.max
import kotlin.math.min

object LocalTrackerDiffUtil {
  @JvmStatic
  fun computeDifferences(tracker: LineStatusTracker<*>?,
                         document1: Document,
                         document2: Document,
                         activeChangelistId: String,
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
        val isReleased = partialTracker.isReleased
        val isOperational = partialTracker.isOperational()
        val affectedChangelistIds = partialTracker.getAffectedChangeListsIds()

        if (!isOperational) {
          TrackerData(isReleased, affectedChangelistIds, null)
        }
        else {
          val ranges = partialTracker.getRanges()

          val localText = document2.immutableCharSequence
          val vcsText = document1.immutableCharSequence
          val trackerVcsText = partialTracker.vcsDocument.immutableCharSequence

          val diffData = TrackerDiffData(ranges, localText, vcsText, trackerVcsText)
          TrackerData(isReleased, affectedChangelistIds, diffData)
        }
      }
    }

    if (data.isReleased) {
      return handler.error() // DiffRequest is out of date
    }

    val diffData = data.diffData
    if (diffData?.ranges == null) {
      if (data.affectedChangelist.size == 1 && data.affectedChangelist.contains(activeChangelistId)) {
        // tracker is waiting for initialisation
        // there are only one changelist, so it's safe to fallback to default logic
        return handler.fallbackWithProgress()
      }

      return handler.retryLater()
    }

    val ranges = diffData.ranges
    val isContentsEqual = ranges.isEmpty()
    val texts = arrayOf(diffData.vcsText, diffData.localText)

    if (!StringUtil.equals(diffData.vcsText, diffData.trackerVcsText)) {
      return handler.error() // DiffRequest is out of date
    }

    if (textDiffProvider.isHighlightingDisabled) {
      return handler.done(isContentsEqual, texts, emptyList())
    }


    val lstRanges = mutableListOf<LocalRange>()
    val linesRanges = mutableListOf<Range>()
    val rangesFragmentData = mutableListOf<LineFragmentData>()

    for (localRange in ranges) {
      when (val exclusionState = localRange.exclusionState) {
        RangeExclusionState.Included, RangeExclusionState.Excluded -> {
          val isExcludedFromCommit = exclusionState == RangeExclusionState.Excluded
          lstRanges += localRange
          linesRanges += Range(localRange.vcsLine1, localRange.vcsLine2, localRange.line1, localRange.line2)
          rangesFragmentData += LineFragmentData(activeChangelistId, isExcludedFromCommit, localRange.changelistId, null)
        }
        is RangeExclusionState.Partial -> {
          exclusionState.iterateDeletionOffsets { start, end, isIncluded ->
            for (i in start until end) {
              lstRanges += localRange
              linesRanges += Range(localRange.vcsLine1 + i, localRange.vcsLine1 + i + 1, localRange.line1, localRange.line1)
              rangesFragmentData += LineFragmentData(activeChangelistId, !isIncluded, localRange.changelistId, Side.LEFT)
            }
          }
          exclusionState.iterateAdditionOffsets { start, end, isIncluded ->
            for (i in start until end) {
              lstRanges += localRange
              linesRanges += Range(localRange.vcsLine2, localRange.vcsLine2, localRange.line1 + i, localRange.line1 + i + 1)
              rangesFragmentData += LineFragmentData(activeChangelistId, !isIncluded, localRange.changelistId, Side.RIGHT)
            }
          }
        }
      }
    }

    val lineOffsets1 = LineOffsetsUtil.create(diffData.vcsText)
    val lineOffsets2 = LineOffsetsUtil.create(diffData.localText)
    val newFragments = textDiffProvider.compare(diffData.vcsText, diffData.localText, linesRanges, indicator)!!

    val toggleableLineRanges = mutableListOf<ToggleableLineRange>()
    for (i in lstRanges.indices) {
      val lineFragmentData = rangesFragmentData[i]
      if (newFragments[i].isEmpty() && lineFragmentData.isPartiallyExcluded()) {
        // never ignore lines with per-line checkboxes
        val range = linesRanges[i]
        val fragment = ComparisonManagerImpl.createLineFragment(range.start1, range.end1, range.start2, range.end2,
                                                                lineOffsets1, lineOffsets2)
        val fallbackFragments = listOf(LineFragmentImpl(fragment, emptyList()))
        toggleableLineRanges += ToggleableLineRange(lstRanges[i], fallbackFragments, lineFragmentData)
      }
      else {
        toggleableLineRanges += ToggleableLineRange(lstRanges[i], newFragments[i], lineFragmentData)
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
    val lineRange: com.intellij.openapi.vcs.ex.Range,
    val fragments: List<LineFragment>,
    val fragmentData: LineFragmentData
  )

  data class LineFragmentData(val activeChangelistId: String,
                              val isExcludedFromCommit: Boolean,
                              val changelistId: String,
                              val partialExclusionSide: Side?) {
    fun isFromActiveChangelist() = changelistId == activeChangelistId
    fun isSkipped() = !isFromActiveChangelist()
    fun isExcluded(allowExcludeChangesFromCommit: Boolean) = !isFromActiveChangelist() ||
                                                             allowExcludeChangesFromCommit && isExcludedFromCommit

    fun isPartiallyExcluded(): Boolean = partialExclusionSide != null
  }

  private data class TrackerData(val isReleased: Boolean,
                                 val affectedChangelist: List<String>,
                                 val diffData: TrackerDiffData?)

  private data class TrackerDiffData(val ranges: List<LocalRange>?,
                                     val localText: CharSequence,
                                     val vcsText: CharSequence,
                                     val trackerVcsText: CharSequence)


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
  fun toggleRangeAtLine(provider: LocalTrackerActionProvider, line: Int, fragmentData: LineFragmentData) {
    val tracker = provider.localRequest.partialTracker ?: return

    if (fragmentData.isPartiallyExcluded()) {
      val lines = BitSet()
      lines.set(line)
      tracker.setPartiallyExcludedFromCommit(lines, fragmentData.partialExclusionSide!!, !fragmentData.isExcludedFromCommit)
    }
    else {
      val range = tracker.getRangeForLine(line) ?: return
      tracker.setExcludedFromCommit(range, !fragmentData.isExcludedFromCommit)
    }

    provider.viewer.rediff()
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
    return lines1.nextClearBit(lineRange.vcsLine1) != lineRange.vcsLine2 ||
           lines2.nextClearBit(lineRange.line1) != lineRange.line2
  }

  @JvmStatic
  fun createToggleAreaThumb(editor: EditorEx, line1: Int, line2: Int): RangeHighlighter {
    val range = DiffUtil.getLinesRange(editor.document, line1, line2)
    val checkboxHighlighter = editor.markupModel.addRangeHighlighter(null, range.startOffset, range.endOffset,
                                                                     DiffDrawUtil.LST_LINE_MARKER_LAYER,
                                                                     HighlighterTargetArea.LINES_IN_RANGE)
    checkboxHighlighter.lineMarkerRenderer = ToggleAreaThumbRenderer()
    return checkboxHighlighter
  }

  private class ToggleAreaThumbRenderer : LineMarkerRenderer {
    override fun paint(editor: Editor, g: Graphics, r: Rectangle) {
      val gutter = (editor as EditorEx).gutterComponentEx
      val width = JBUIScale.scale(3)
      val x = gutter.whitespaceSeparatorOffset - width
      g.color = editor.colorsScheme.getColor(EditorColors.DIFF_BLOCK_AREA_HIGHLIGHT_MARKER) ?: return
      g.fillRect(x, r.y, width, r.height)
    }
  }

  @JvmStatic
  fun createTrackerActions(provider: LocalTrackerActionProvider): List<AnAction> {
    return listOf(MoveSelectedChangesToAnotherChangelistAction(provider),
                  PartiallyExcludeSelectedLinesFromCommitAction(provider, false),
                  PartiallyExcludeSelectedLinesFromCommitAction(provider, true))
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
      val isPartialBlockSelection = affectedRanges.isEmpty() || affectedRanges.any { isPartiallySelected(it, selectedLines) }

      if (isPartialBlockSelection) {
        e.presentation.text = when {
          isExclude -> ActionsBundle.message("action.Vcs.Diff.ExcludeChangedLinesFromCommit.text")
          else -> ActionsBundle.message("action.Vcs.Diff.IncludeChangedLinesIntoCommit.text")
        }
      }
      else {
        e.presentation.text = when {
          isExclude -> VcsBundle.message("changes.ExcludeChangedLinesFromCommit.chunks.action.text")
          else -> VcsBundle.message("changes.IncludeChangedLinesIntoCommit.chunks.action.text")
        }
      }
      e.presentation.isEnabled = affectedRanges.any {
        if (isExclude) {
          !it.exclusionState.isFullyExcluded
        }
        else {
          !it.exclusionState.isFullyIncluded
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
          selectedLines.localLines.nextClearBit(range.line1) < range.line2) {
        return true
      }
      if (selectedLines.vcsLines != null && range.vcsLine1 != range.vcsLine2 &&
          selectedLines.vcsLines.nextClearBit(range.vcsLine1) < range.vcsLine2) {
        return true
      }
      return false
    }
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


  abstract class LocalTrackerActionProvider(val viewer: DiffViewerBase,
                                            val localRequest: LocalChangeListDiffRequest,
                                            val allowExcludeChangesFromCommit: Boolean) {
    abstract fun getSelectedTrackerChanges(e: AnActionEvent): List<LocalTrackerChange>?
    abstract fun getSelectedTrackerLines(e: AnActionEvent): SelectedTrackerLine?
  }

  class SelectedTrackerLine(
    val vcsLines: BitSet?,
    val localLines: BitSet?
  )

  class LocalTrackerChange(val startLine: Int, val endLine: Int, val changelistId: String, val isExcludedFromCommit: Boolean)

  private val LocalChangeListDiffRequest.partialTracker get() = lineStatusTracker as? PartialLocalLineStatusTracker
}
