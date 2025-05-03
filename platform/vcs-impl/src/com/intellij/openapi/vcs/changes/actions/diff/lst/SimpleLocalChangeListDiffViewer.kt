// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.actions.diff.lst

import com.intellij.diff.DiffContext
import com.intellij.diff.fragments.LineFragment
import com.intellij.diff.tools.simple.SimpleDiffChange
import com.intellij.diff.tools.simple.SimpleDiffChangeUi
import com.intellij.diff.tools.simple.SimpleDiffViewer
import com.intellij.diff.tools.util.DiffNotifications.createError
import com.intellij.diff.util.*
import com.intellij.diff.util.DiffDrawUtil.LineHighlighterBuilder
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.application.ApplicationManager
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
class SimpleLocalChangeListDiffViewer(context: DiffContext,
                                      private val localRequest: LocalChangeListDiffRequest
) : SimpleDiffViewer(context, localRequest.request) {

  private val isAllowExcludeChangesFromCommit: Boolean = DiffUtil.isUserDataFlagSet(LocalChangeListDiffTool.ALLOW_EXCLUDE_FROM_COMMIT,
                                                                                    context)

  private val trackerActionProvider: LocalTrackerActionProvider
  private val excludeAllCheckboxPanel: ExcludeAllCheckboxPanel
  private val gutterCheckboxMouseMotionListener: GutterCheckboxMouseMotionListener

  private val localHighlighters = mutableListOf<RangeHighlighter>()

  init {
    trackerActionProvider = MyLocalTrackerActionProvider(this, localRequest, isAllowExcludeChangesFromCommit)
    excludeAllCheckboxPanel = ExcludeAllCheckboxPanel(this, editor2)
    excludeAllCheckboxPanel.init(localRequest, isAllowExcludeChangesFromCommit)

    installTrackerListener(this, localRequest)

    gutterCheckboxMouseMotionListener = GutterCheckboxMouseMotionListener()
    gutterCheckboxMouseMotionListener.install()

    for (action in createTrackerShortcutOnlyActions(trackerActionProvider)) {
      DiffUtil.registerAction(action, myPanel)
    }
  }

  override fun createTitles(): List<JComponent> {
    val titles = super.createTitles()
    assert(titles.size == 2)

    val titleWithCheckbox = JBUI.Panels.simplePanel()
    if (titles[1] != null) titleWithCheckbox.addToCenter(titles[1])
    titleWithCheckbox.addToLeft(excludeAllCheckboxPanel)

    return Arrays.asList(titles[0], titleWithCheckbox)
  }

  override fun createEditorPopupActions(): List<AnAction> {
    return super.createEditorPopupActions() +
           createTrackerEditorPopupActions(trackerActionProvider)
  }

  override fun createUi(change: SimpleDiffChange): SimpleDiffChangeUi {
    if (change is MySimpleDiffChange) return MySimpleDiffChangeUi(this, change)
    return super.createUi(change)
  }

  override fun getStatusTextMessage(): @Nls String? {
    if (isAllowExcludeChangesFromCommit) {
      var totalCount = 0
      var includedIntoCommitCount = 0
      var excludedCount = 0

      for (change in diffChanges) {
        val exclusionState = if (change is MySimpleDiffChange) {
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
    return computeDifferences(
      localRequest.lineStatusTracker,
      content1.document,
      content2.document,
      localRequest.changelistId,
      isAllowExcludeChangesFromCommit,
      myTextDiffProvider,
      indicator,
      MyLocalTrackerDiffHandler(indicator)
    )
  }

  private inner class MyLocalTrackerDiffHandler(private val progressIndicator: ProgressIndicator) : LocalTrackerDiffHandler {

    override fun done(isContentsEqual: Boolean,
                      areVCSBoundedActionsDisabled: Boolean,
                      texts: Array<CharSequence>,
                      toggleableLineRanges: List<ToggleableLineRange>): Runnable {

      val changes = mutableListOf<SimpleDiffChange>()

      for (toggleableLineRange in toggleableLineRanges) {
        val data = toggleableLineRange.fragmentData
        val isSkipped = data.isSkipped()
        val isExcluded = data.isExcluded(isAllowExcludeChangesFromCommit)

        for (fragment in toggleableLineRange.fragments) {
          changes.add(MySimpleDiffChange(changes.size, fragment, isExcluded, isSkipped,
                                         data.changelistId, data.isPartiallyExcluded(), data.exclusionState))
        }
      }

      val applyChanges = apply(changes, isContentsEqual)
      val applyGutterExcludeOperations = applyGutterOperations(toggleableLineRanges, areVCSBoundedActionsDisabled)

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
      return applyNotification(createError())
    }
  }

  override fun onAfterRediff() {
    super.onAfterRediff()
    excludeAllCheckboxPanel.refresh()
  }

  override fun clearDiffPresentation() {
    super.clearDiffPresentation()

    for (operation in localHighlighters) {
      operation.dispose()
    }
    localHighlighters.clear()

    gutterCheckboxMouseMotionListener.destroyHoverHighlighter()
  }

  private fun applyGutterOperations(toggleableLineRanges: List<ToggleableLineRange>, areVCSBoundedActionsDisabled : Boolean): Runnable {
    return Runnable {
      if (areVCSBoundedActionsDisabled) return@Runnable
      if (isAllowExcludeChangesFromCommit) {
        for (toggleableLineRange in toggleableLineRanges) {
          localHighlighters.addAll(createGutterToggleRenderers(toggleableLineRange))
        }
      }

      for (range in toggleableLineRanges) {
        localHighlighters.addIfNotNull(createClientIdHighlighter(range))
      }

      if (!localHighlighters.isEmpty()) {
        editor1.gutterComponentEx.revalidateMarkup()
        editor2.gutterComponentEx.revalidateMarkup()
      }
    }
  }

  private fun createGutterToggleRenderers(toggleableLineRange: ToggleableLineRange): List<RangeHighlighter> {
    val fragmentData = toggleableLineRange.fragmentData
    if (!fragmentData.isFromActiveChangelist()) return emptyList()

    val result = mutableListOf<RangeHighlighter>()
    val exclusionState = fragmentData.exclusionState
    if (fragmentData.isPartiallyExcluded()) {
      val partialExclusionState = exclusionState as RangeExclusionState.Partial
      val lineRange = toggleableLineRange.lineRange

      partialExclusionState.iterateDeletionOffsets { start: Int, end: Int, isIncluded: Boolean ->
        for (i in start until end) {
          result.add(createLineCheckboxToggleHighlighter(i + lineRange.start1, Side.LEFT, !isIncluded))
        }
      }
      partialExclusionState.iterateAdditionOffsets { start: Int, end: Int, isIncluded: Boolean ->
        for (i in start until end) {
          result.add(createLineCheckboxToggleHighlighter(i + lineRange.start2, Side.RIGHT, !isIncluded))
        }
      }
    }
    else {
      result.add(createBlockCheckboxToggleHighlighter(toggleableLineRange))
    }

    if (shouldShowToggleAreaThumb(toggleableLineRange)) {
      result.add(createToggleAreaThumb(toggleableLineRange, Side.LEFT))
      result.add(createToggleAreaThumb(toggleableLineRange, Side.RIGHT))
    }

    return result
  }

  private fun createBlockCheckboxToggleHighlighter(toggleableLineRange: ToggleableLineRange): RangeHighlighter {
    val side = Side.RIGHT
    val line = getSingleCheckBoxLine(toggleableLineRange, side)
    val isExcludedFromCommit = toggleableLineRange.fragmentData.exclusionState is RangeExclusionState.Excluded

    return createCheckboxToggle(getEditor(side), line, isExcludedFromCommit) {
      toggleBlockExclusion(trackerActionProvider, line, isExcludedFromCommit)
    }
  }

  private fun createLineCheckboxToggleHighlighter(line: Int, side: Side, isExcludedFromCommit: Boolean): RangeHighlighter {
    return createCheckboxToggle(getEditor(side), line, isExcludedFromCommit) {
      toggleLinePartialExclusion(trackerActionProvider, line, side, isExcludedFromCommit)
    }
  }

  private fun createToggleAreaThumb(toggleableLineRange: ToggleableLineRange, side: Side): RangeHighlighter {
    val editor = getEditor(side)
    val lineRange = toggleableLineRange.lineRange
    val line1 = side.select(lineRange.start1, lineRange.start2)
    val line2 = side.select(lineRange.end1, lineRange.end2)
    val isExcludedFromCommit = toggleableLineRange.fragmentData.exclusionState is RangeExclusionState.Excluded
    return createToggleAreaThumb(editor, line1, line2) {
      toggleBlockExclusion(trackerActionProvider, lineRange.start1, isExcludedFromCommit)
    }
  }

  private fun createClientIdHighlighter(range: ToggleableLineRange): RangeHighlighter? {
    val clientIds = range.fragmentData.clientIds
    if (clientIds.isEmpty()) return null

    val iconRenderer = createClientIdGutterIconRenderer(localRequest.project, clientIds)
    if (iconRenderer == null) return null

    val lineRange = range.lineRange
    val side = Side.fromLeft(lineRange.start2 == lineRange.end2)
    val line1 = side.select(lineRange.start1, lineRange.start2)
    val line2 = side.select(lineRange.end1, lineRange.end2)

    val editor = getEditor(side)
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

  class MySimpleDiffChange internal constructor(index: Int,
                                                fragment: LineFragment,
                                                isExcluded: Boolean,
                                                isSkipped: Boolean,
                                                val changelistId: @NonNls String,
                                                val isPartiallyExcluded: Boolean,
                                                val exclusionState: RangeExclusionState
  ) : SimpleDiffChange(index, fragment, isExcluded, isSkipped)

  private class MySimpleDiffChangeUi(val viewer: SimpleLocalChangeListDiffViewer,
                                     val change: MySimpleDiffChange
  ) : SimpleDiffChangeUi(viewer, change) {

    override fun installHighlighter(previousChange: SimpleDiffChange?) {

      if (change.isPartiallyExcluded && viewer.isAllowExcludeChangesFromCommit) {
        assert(myHighlighters.isEmpty() && myOperations.isEmpty())

        val changeStart1 = change.getStartLine(Side.LEFT)
        val changeStart2 = change.getStartLine(Side.RIGHT)

        val exclusionState = change.exclusionState as RangeExclusionState.Partial
        exclusionState.iterateDeletionOffsets { start: Int, end: Int, isIncluded: Boolean ->
          myHighlighters.addAll(
            LineHighlighterBuilder(viewer.getEditor(Side.LEFT),
                                   start + changeStart1,
                                   end + changeStart1,
                                   TextDiffType.DELETED)
              .withExcludedInEditor(change.isSkipped)
              .withExcludedInGutter(!isIncluded)
              .withAlignedSides(viewer.needAlignChanges())
              .done())
        }
        exclusionState.iterateAdditionOffsets { start: Int, end: Int, isIncluded: Boolean ->
          myHighlighters.addAll(
            LineHighlighterBuilder(viewer.getEditor(Side.RIGHT),
                                   start + changeStart2,
                                   end + changeStart2,
                                   TextDiffType.INSERTED)
              .withExcludedInEditor(change.isSkipped)
              .withExcludedInGutter(!isIncluded)
              .withAlignedSides(viewer.needAlignChanges())
              .done())
        }

        if (exclusionState.deletionsCount == 0) {
          myHighlighters.addAll(
            LineHighlighterBuilder(viewer.getEditor(Side.LEFT),
                                   changeStart1,
                                   changeStart1,
                                   TextDiffType.INSERTED)
              .withExcludedInEditor(change.isSkipped)
              .withExcludedInGutter(false)
              .withAlignedSides(viewer.needAlignChanges())
              .done())
        }
        if (exclusionState.additionsCount == 0) {
          myHighlighters.addAll(
            LineHighlighterBuilder(viewer.getEditor(Side.RIGHT),
                                   changeStart2,
                                   changeStart2,
                                   TextDiffType.DELETED)
              .withExcludedInEditor(change.isSkipped)
              .withExcludedInGutter(false)
              .withAlignedSides(viewer.needAlignChanges())
              .done())
        }

        // do not draw ">>"
        // doInstallActionHighlighters();
      }
      else {
        super.installHighlighter(previousChange)
      }
    }

    override fun drawDivider(handler: DiffDividerDrawUtil.DividerPaintable.Handler): Boolean {
      if (change.isPartiallyExcluded && viewer.isAllowExcludeChangesFromCommit) {
        val startLine1 = change.getStartLine(Side.LEFT)
        val endLine1 = change.getEndLine(Side.LEFT)
        val startLine2 = change.getStartLine(Side.RIGHT)
        val endLine2 = change.getEndLine(Side.RIGHT)

        if (viewer.needAlignChanges()) {
          if (startLine1 != endLine1) {
            if (!handler.processAligned(startLine1, endLine1, startLine2, startLine2, TextDiffType.DELETED)) {
              return false
            }
          }
          if (startLine2 != endLine2) {
            if (!handler.processAligned(endLine1, endLine1, startLine2, endLine2, TextDiffType.INSERTED)) {
              return false
            }
          }
          return true
        }
        else {
          if (startLine1 != endLine1) {
            if (!handler.processExcludable(startLine1, endLine1, startLine2, startLine2, TextDiffType.DELETED,
                                           change.isExcluded, change.isSkipped)) {
              return false
            }
          }
          if (startLine2 != endLine2) {
            if (!handler.processExcludable(endLine1, endLine1, startLine2, endLine2, TextDiffType.INSERTED,
                                           change.isExcluded, change.isSkipped)) {
              return false
            }
          }
          return true
        }
      }
      else {
        return super.drawDivider(handler)
      }
    }
  }

  private class MyLocalTrackerActionProvider(override val viewer: SimpleLocalChangeListDiffViewer,
                                             override val localRequest: LocalChangeListDiffRequest,
                                             override val allowExcludeChangesFromCommit: Boolean
  ) : LocalTrackerActionProvider {

    override fun getSelectedTrackerChanges(e: AnActionEvent): List<LocalTrackerChange>? {
      val editor = e.getData(CommonDataKeys.EDITOR)
      val side = Side.fromValue(viewer.editors, editor)
      if (side == null) return null

      return viewer.getSelectedChanges(side)
        .filterIsInstance<MySimpleDiffChange>()
        .map {
          LocalTrackerChange(it.getStartLine(Side.RIGHT),
                             it.getEndLine(Side.RIGHT),
                             it.changelistId,
                             it.exclusionState)
        }
    }

    override fun getSelectedTrackerLines(e: AnActionEvent): SelectedTrackerLine? {
      val editor = e.getData(CommonDataKeys.EDITOR)
      val side = Side.fromValue(viewer.editors, editor)
      if (editor == null || side == null) return null

      val selectedLines = DiffUtil.getSelectedLines(editor)
      if (side.isLeft) {
        return SelectedTrackerLine(selectedLines, null)
      }
      else {
        return SelectedTrackerLine(null, selectedLines)
      }
    }
  }

  private inner class GutterCheckboxMouseMotionListener {
    private var myHighlighter: RangeHighlighter? = null

    fun install() {
      for (side in Side.entries) {
        val listener = MyGutterMouseListener(side)
        getEditor(side).gutterComponentEx.addMouseListener(listener)
        getEditor(side).gutterComponentEx.addMouseMotionListener(listener)
      }
    }

    fun destroyHoverHighlighter() {
      if (myHighlighter != null) {
        myHighlighter!!.dispose()
        myHighlighter = null
      }
    }

    private fun updateHoverHighlighter(side: Side, line: Int) {
      val change = diffChanges.find { it.getStartLine(side) <= line && it.getEndLine(side) > line } as? MySimpleDiffChange

      if (change == null ||
          change.isPartiallyExcluded ||
          localRequest.changelistId != change.changelistId) {
        destroyHoverHighlighter()
        return
      }

      val editor = getEditor(side)
      if (hasIconHighlighters(myProject, editor, line)) {
        if (myHighlighter != null && editor.document.getLineNumber(myHighlighter!!.startOffset) != line) {
          destroyHoverHighlighter()
        }
        return
      }

      destroyHoverHighlighter()

      val isExcludedFromCommit = change.exclusionState is RangeExclusionState.Excluded
      myHighlighter = createCheckboxToggle(editor, line, isExcludedFromCommit) {
        toggleLinePartialExclusion(trackerActionProvider, line, side, isExcludedFromCommit)
        destroyHoverHighlighter()
      }
    }

    private inner class MyGutterMouseListener(private val mySide: Side) : MouseAdapter() {
      override fun mouseMoved(e: MouseEvent) {
        if (!isAllowExcludeChangesFromCommit || myTextDiffProvider.areVCSBoundedActionsDisabled()) {
          destroyHoverHighlighter()
          return
        }

        val editor = getEditor(mySide)
        val gutter = editor.gutterComponentEx
        val xOffset = if (DiffUtil.isMirrored(editor)) gutter.width - e.x else e.x
        if (xOffset < gutter.iconAreaOffset || xOffset > gutter.iconAreaOffset + gutter.iconsAreaWidth) {
          destroyHoverHighlighter()
          return
        }

        val position = editor.xyToLogicalPosition(e.point)
        updateHoverHighlighter(mySide, position.line)
      }

      override fun mouseExited(e: MouseEvent) {
        destroyHoverHighlighter()
      }
    }
  }
}
