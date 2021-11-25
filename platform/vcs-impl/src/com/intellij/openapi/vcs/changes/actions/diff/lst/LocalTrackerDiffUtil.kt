// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.util.base.DiffViewerBase
import com.intellij.diff.tools.util.text.TwosideTextDiffProvider
import com.intellij.diff.util.DiffUtil
import com.intellij.diff.util.Range
import com.intellij.icons.AllIcons
import com.intellij.idea.ActionsBundle
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.ex.EditorEx
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
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
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
      return handler.done(isContentsEqual, texts, emptyList(), emptyList())
    }


    val linesRanges = ranges.map { range -> Range(range.vcsLine1, range.vcsLine2, range.line1, range.line2) }

    val newFragments = textDiffProvider.compare(diffData.vcsText, diffData.localText, linesRanges, indicator)!!

    val fragments = mutableListOf<LineFragment>()
    val fragmentsData = mutableListOf<LineFragmentData>()

    for (i in ranges.indices) {
      val localRange = ranges[i]
      val rangeFragments = newFragments[i]

      fragments.addAll(rangeFragments)

      val fragmentData = LineFragmentData(activeChangelistId, localRange.isExcludedFromCommit, localRange.changelistId)
      repeat(rangeFragments.size) { fragmentsData.add(fragmentData) }
    }

    return handler.done(isContentsEqual, texts, fragments, fragmentsData)
  }

  interface LocalTrackerDiffHandler {
    fun done(isContentsEqual: Boolean,
             texts: Array<CharSequence>,
             fragments: List<LineFragment>,
             fragmentsData: List<LineFragmentData>): Runnable

    fun retryLater(): Runnable
    fun fallback(): Runnable
    fun fallbackWithProgress(): Runnable
    fun error(): Runnable
  }

  data class LineFragmentData(val activeChangelistId: String,
                              val isExcludedFromCommit: Boolean,
                              val changelistId: String) {
    fun isFromActiveChangelist() = changelistId == activeChangelistId
    fun isSkipped() = !isFromActiveChangelist()
    fun isExcluded(allowExcludeChangesFromCommit: Boolean) = !isFromActiveChangelist() ||
                                                             allowExcludeChangesFromCommit && isExcludedFromCommit
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
  fun toggleRangeAtLine(provider: LocalTrackerActionProvider, line: Int, isExcludedFromCommit: Boolean) {
    val tracker = provider.localRequest.partialTracker ?: return
    val range = tracker.getRangeForLine(line) ?: return

    tracker.setExcludedFromCommit(range, !isExcludedFromCommit)

    provider.viewer.rediff()
  }

  @JvmStatic
  fun createTrackerActions(provider: LocalTrackerActionProvider): List<AnAction> {
    return listOf(MoveSelectedChangesToAnotherChangelistAction(provider),
                  ExcludeSelectedChangesFromCommitAction(provider),
                  IncludeOnlySelectedChangesIntoCommitAction(provider))
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
      ActionUtil.copyFrom(this, "Vcs.Diff.ExcludeChangedLinesFromCommit")
    }

    override fun getText(changes: List<LocalTrackerChange>): String {
      val hasExcluded = changes.any { it.isExcludedFromCommit }
      return if (changes.isNotEmpty() && !hasExcluded) VcsBundle.message("changes.exclude.lines.from.commit")
      else VcsBundle.message("changes.include.lines.into.commit")
    }

    override fun doPerform(e: AnActionEvent,
                           tracker: PartialLocalLineStatusTracker,
                           changes: List<LocalTrackerChange>) {
      val selectedLines = getLocalSelectedLines(changes)

      val hasExcluded = changes.any { it.isExcludedFromCommit }
      tracker.setExcludedFromCommit(selectedLines, !hasExcluded)

      provider.viewer.rediff()
    }
  }

  private class IncludeOnlySelectedChangesIntoCommitAction(provider: LocalTrackerActionProvider)
    : MySelectedChangesActionBase(true, provider) {

    init {
      ActionUtil.copyFrom(this, "Vcs.Diff.IncludeOnlyChangedLinesIntoCommit")
    }

    override fun doPerform(e: AnActionEvent,
                           tracker: PartialLocalLineStatusTracker,
                           changes: List<LocalTrackerChange>) {
      val selectedLines = getLocalSelectedLines(changes)

      tracker.setExcludedFromCommit(activeChangelistId, true)
      tracker.setExcludedFromCommit(selectedLines, false)

      provider.viewer.rediff()
    }
  }

  private abstract class MySelectedChangesActionBase(private val forActiveChangelistOnly: Boolean,
                                                     protected val provider: LocalTrackerActionProvider) : DumbAwareAction() {

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
  }

  class LocalTrackerChange(val startLine: Int, val endLine: Int, val changelistId: String, val isExcludedFromCommit: Boolean)

  private val LocalChangeListDiffRequest.partialTracker get() = lineStatusTracker as? PartialLocalLineStatusTracker
}
