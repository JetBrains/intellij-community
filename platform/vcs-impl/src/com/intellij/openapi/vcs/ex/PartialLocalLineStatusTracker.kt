// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.Side
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Separator
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.ChangeListWorker
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.labels.ActionGroupLink
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.WeakList
import com.intellij.util.ui.JBUI
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.CalledInAwt
import java.awt.BorderLayout
import java.awt.Point
import java.lang.ref.WeakReference
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel
import kotlin.collections.HashSet

interface PartialLocalLineStatusTracker : LineStatusTracker<LocalRange> {
  fun getAffectedChangeListsIds(): List<String>

  fun moveToChangelist(range: Range, changelist: LocalChangeList)
  fun moveToChangelist(lines: BitSet, changelist: LocalChangeList)


  fun getExcludedFromCommitState(changelistId: String): ExclusionState

  fun setExcludedFromCommit(isExcluded: Boolean)
  fun setExcludedFromCommit(range: Range, isExcluded: Boolean)
  fun setExcludedFromCommit(lines: BitSet, isExcluded: Boolean)


  fun hasPartialChangesToCommit(): Boolean

  fun handlePartialCommit(side: Side, changelistIds: List<String>, honorExcludedFromCommit: Boolean): PartialCommitHelper
  fun rollbackChangelistChanges(changelistsIds: List<String>, rollbackRangesExcludedFromCommit: Boolean)


  fun addListener(listener: Listener, disposable: Disposable)

  open class ListenerAdapter : Listener
  interface Listener : EventListener {
    fun onBecomingValid(tracker: PartialLocalLineStatusTracker) {}
    fun onChangeListsChange(tracker: PartialLocalLineStatusTracker) {}
    fun onChangeListMarkerChange(tracker: PartialLocalLineStatusTracker) {}
    fun onExcludedFromCommitChange(tracker: PartialLocalLineStatusTracker) {}
  }
}

abstract class PartialCommitHelper(val content: String) {
  @CalledInAwt
  abstract fun applyChanges()
}

enum class ExclusionState { ALL_INCLUDED, ALL_EXCLUDED, PARTIALLY, NO_CHANGES }

class LocalRange(line1: Int, line2: Int, vcsLine1: Int, vcsLine2: Int, innerRanges: List<InnerRange>?,
                 val changelistId: String, val isExcludedFromCommit: Boolean)
  : Range(line1, line2, vcsLine1, vcsLine2, innerRanges)


class ChangelistsLocalLineStatusTracker(project: Project,
                                        document: Document,
                                        virtualFile: VirtualFile,
                                        mode: Mode
) : LocalLineStatusTracker<LocalRange>(project, document, virtualFile, mode),
    PartialLocalLineStatusTracker,
    ChangeListWorker.PartialChangeTracker {
  private val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
  private val lstManager = LineStatusTrackerManager.getInstance(project) as LineStatusTrackerManager
  private val undoManager = UndoManager.getInstance(project)

  private val undoStateRecordingEnabled = Registry.`is`("vcs.enable.partial.changelists.undo")
  private val redoStateRecordingEnabled = Registry.`is`("vcs.enable.partial.changelists.redo")

  override val renderer: MyLineStatusMarkerRenderer = MyLineStatusMarkerRenderer(this)

  private var defaultMarker: ChangeListMarker
  private var currentMarker: ChangeListMarker? = null

  private var initialChangeListId: String? = null
  private var lastKnownTrackerChangeListId: String? = null
  private val affectedChangeLists = HashSet<String>()

  private var hasUndoInCommand: Boolean = false

  private var shouldInitializeWithExcludedFromCommit: Boolean = false

  private val undoableActions: WeakList<MyUndoableAction> = WeakList()

  init {
    defaultMarker = ChangeListMarker(changeListManager.defaultChangeList)
    affectedChangeLists.add(defaultMarker.changelistId)

    if (undoStateRecordingEnabled) {
      document.addDocumentListener(MyUndoDocumentListener(), disposable)
      project.messageBus.connect(disposable).subscribe(CommandListener.TOPIC, MyUndoCommandListener())
      Disposer.register(disposable, Disposable { dropExistingUndoActions() })
    }

    assert(blocks.isEmpty())
  }

  override fun Block.toRange(): LocalRange = LocalRange(this.start, this.end, this.vcsStart, this.vcsEnd, this.innerRanges,
                                                        this.marker.changelistId, this.excludedFromCommit)

  override fun createDocumentTrackerHandler(): DocumentTracker.Handler = PartialDocumentTrackerHandler()

  override fun getAffectedChangeListsIds(): List<String> {
    return documentTracker.readLock {
      assert(!affectedChangeLists.isEmpty())
      affectedChangeLists.toList()
    }
  }

  private fun updateAffectedChangeLists(notifyChangeListManager: Boolean = true) {
    val oldIds = HashSet<String>()
    val newIds = HashSet<String>()

    for (block in blocks) {
      newIds.add(block.marker.changelistId)
    }

    if (!isInitialized) initialChangeListId?.let { newIds.add(it) }

    if (newIds.isEmpty()) {
      val listId = lastKnownTrackerChangeListId ?: defaultMarker.changelistId
      newIds.add(listId)
    }

    if (newIds.size == 1) {
      lastKnownTrackerChangeListId = newIds.single()
    }

    oldIds.addAll(affectedChangeLists)

    affectedChangeLists.clear()
    affectedChangeLists.addAll(newIds)

    if (oldIds != newIds) {
      if (notifyChangeListManager) {
        // It's OK to call this under documentTracker.writeLock, as this method will not grab CLM lock.
        changeListManager.notifyChangelistsChanged(VcsUtil.getFilePath(virtualFile), oldIds.toList(), newIds.toList())
      }

      eventDispatcher.multicaster.onChangeListsChange(this)
    }

    eventDispatcher.multicaster.onChangeListMarkerChange(this)
  }

  @CalledInAwt
  override fun setBaseRevision(vcsContent: CharSequence) {
    val changelistId = if (!isInitialized) initialChangeListId else null
    initialChangeListId = null

    setBaseRevision(vcsContent) {
      if (changelistId != null) {
        changeListManager.executeUnderDataLock {
          if (changeListManager.getChangeList(changelistId) != null) {
            documentTracker.writeLock {
              currentMarker = ChangeListMarker(changelistId)
              documentTracker.updateFrozenContentIfNeeded()
              currentMarker = null
            }
          }
        }
      }
    }

    documentTracker.writeLock {
      updateAffectedChangeLists()
    }

    dropExistingUndoActions()
    if (isValid()) eventDispatcher.multicaster.onBecomingValid(this)
  }

  @CalledInAwt
  fun replayChangesFromDocumentEvents(events: List<DocumentEvent>) {
    if (events.isEmpty() || !blocks.isEmpty()) return
    updateDocument(Side.LEFT) { vcsDocument ->
      for (event in events.reversed()) {
        vcsDocument.replaceString(event.offset, event.offset + event.newLength, event.oldFragment)
      }
    }
  }


  override fun initChangeTracking(defaultId: String, changelistsIds: List<String>, fileChangelistId: String?) {
    documentTracker.writeLock {
      defaultMarker = ChangeListMarker(defaultId)

      if (!isInitialized) initialChangeListId = fileChangelistId

      lastKnownTrackerChangeListId = fileChangelistId

      val idsSet = changelistsIds.toSet()
      moveMarkers({ !idsSet.contains(it.marker.changelistId) }, defaultMarker)

      // no need to notify CLM, as we're inside it's action
      updateAffectedChangeLists(false)
    }
  }

  override fun defaultListChanged(oldListId: String, newListId: String) {
    documentTracker.writeLock {
      defaultMarker = ChangeListMarker(newListId)
    }
  }

  override fun changeListRemoved(listId: String) {
    documentTracker.writeLock {
      if (!affectedChangeLists.contains(listId)) return@writeLock

      if (!isInitialized && initialChangeListId == listId) initialChangeListId = null

      if (lastKnownTrackerChangeListId == listId) lastKnownTrackerChangeListId = null

      moveMarkers({ it.marker.changelistId == listId }, defaultMarker)

      updateAffectedChangeLists(false)
    }
  }

  override fun moveChanges(fromListId: String, toListId: String) {
    documentTracker.writeLock {
      if (!affectedChangeLists.contains(fromListId)) return@writeLock

      if (!isInitialized && initialChangeListId == fromListId) initialChangeListId = toListId

      if (lastKnownTrackerChangeListId == fromListId) lastKnownTrackerChangeListId = toListId

      moveMarkers({ it.marker.changelistId == fromListId }, ChangeListMarker(toListId))

      updateAffectedChangeLists(false)
    }
  }

  override fun moveChangesTo(toListId: String) {
    documentTracker.writeLock {
      if (!isInitialized) initialChangeListId = toListId

      lastKnownTrackerChangeListId = toListId

      moveMarkers({ true }, ChangeListMarker(toListId))

      updateAffectedChangeLists(false)
    }
  }

  private fun moveMarkers(condition: (Block) -> Boolean, toMarker: ChangeListMarker) {
    val affectedBlocks = mutableListOf<Block>()

    for (block in blocks) {
      if (block.marker != toMarker &&
          condition(block)) {
        block.marker = toMarker
        affectedBlocks.add(block)
      }
    }

    if (!affectedBlocks.isEmpty()) {
      dropExistingUndoActions()

      updateHighlighters()
    }
  }


  private inner class MyUndoDocumentListener : DocumentListener {
    override fun beforeDocumentChange(event: DocumentEvent) {
      if (hasUndoInCommand) return
      if (undoManager.isRedoInProgress || undoManager.isUndoInProgress) return
      hasUndoInCommand = true

      registerUndoAction(true)
    }
  }

  private inner class MyUndoCommandListener : CommandListener {
    override fun commandStarted(event: CommandEvent) {
      if (!CommandProcessor.getInstance().isUndoTransparentActionInProgress) {
        hasUndoInCommand = false
      }
    }

    override fun commandFinished(event: CommandEvent) {
      if (!CommandProcessor.getInstance().isUndoTransparentActionInProgress) {
        hasUndoInCommand = false
      }
    }

    override fun undoTransparentActionStarted() {
      if (CommandProcessor.getInstance().currentCommand == null) {
        hasUndoInCommand = false
      }
    }

    override fun undoTransparentActionFinished() {
      if (CommandProcessor.getInstance().currentCommand == null) {
        hasUndoInCommand = false
      }
    }


    override fun beforeCommandFinished(event: CommandEvent) {
      registerRedoAction()
    }

    override fun beforeUndoTransparentActionFinished() {
      registerRedoAction()
    }

    private fun registerRedoAction() {
      if (hasUndoInCommand && redoStateRecordingEnabled) {
        registerUndoAction(false)
      }
    }
  }

  private fun dropExistingUndoActions() {
    val actions = undoableActions.copyAndClear()
    for (action in actions) {
      action.drop()
    }
  }

  @CalledInAwt
  private fun registerUndoAction(undo: Boolean) {
    val undoState = collectRangeStates()
    val action = MyUndoableAction(project, document, undoState, undo)
    undoManager.undoableActionPerformed(action)
    undoableActions.add(action)
  }

  private inner class PartialDocumentTrackerHandler : LineStatusTrackerBase<LocalRange>.MyDocumentTrackerHandler() {
    override fun onRangeRefreshed(before: Block, after: List<Block>) {
      super.onRangeRefreshed(before, after)

      val isExcludedFromCommit = before.excludedFromCommit
      val marker = before.marker
      for (block in after) {
        block.excludedFromCommit = isExcludedFromCommit
        block.marker = marker
      }
    }

    override fun onRangesChanged(before: List<Block>, after: Block) {
      super.onRangesChanged(before, after)

      after.excludedFromCommit = mergeExcludedFromCommitRanges(before)

      val affectedMarkers = before.map { it.marker }.distinct()

      val _currentMarker = currentMarker
      if (affectedMarkers.isEmpty() && _currentMarker != null) {
        after.marker = _currentMarker
      }
      else if (affectedMarkers.size == 1) {
        after.marker = affectedMarkers.single()
      }
      else {
        if (!affectedMarkers.isEmpty()) {
          lstManager.notifyInactiveRangesDamaged(virtualFile)
        }
        after.marker = defaultMarker
      }
    }

    override fun onRangeShifted(before: Block, after: Block) {
      super.onRangeShifted(before, after)

      after.excludedFromCommit = before.excludedFromCommit
      after.marker = before.marker
    }

    override fun onRangesMerged(range1: Block, range2: Block, merged: Block): Boolean {
      val superMergeable = super.onRangesMerged(range1, range2, merged)

      merged.excludedFromCommit = mergeExcludedFromCommitRanges(listOf(range1, range2))

      if (range1.marker == range2.marker) {
        merged.marker = range1.marker
        return superMergeable
      }

      if (range1.range.isEmpty || range2.range.isEmpty) {
        if (range1.range.isEmpty && range2.range.isEmpty) {
          merged.marker = defaultMarker
        }
        else if (range1.range.isEmpty) {
          merged.marker = range2.marker
        }
        else {
          merged.marker = range1.marker
        }
        return superMergeable
      }

      return false
    }

    override fun afterRangeChange() {
      super.afterRangeChange()

      updateAffectedChangeLists()
      fireExcludedFromCommitChanged()
    }

    override fun afterBulkRangeChange() {
      super.afterBulkRangeChange()

      blocks.forEach {
        // do not override markers, that are set via other methods of this listener
        if (it.ourData.marker == null) {
          it.marker = defaultMarker
        }
      }

      updateAffectedChangeLists()
      fireExcludedFromCommitChanged()
    }

    override fun onUnfreeze() {
      super.onUnfreeze()

      if (shouldInitializeWithExcludedFromCommit) {
        shouldInitializeWithExcludedFromCommit = false
        for (block in blocks) {
          block.excludedFromCommit = true
        }
      }

      if (isValid()) eventDispatcher.multicaster.onBecomingValid(this@ChangelistsLocalLineStatusTracker)
    }

    private fun mergeExcludedFromCommitRanges(ranges: List<DocumentTracker.Block>): Boolean {
      if (ranges.isEmpty()) return false
      return ranges.all { it.excludedFromCommit }
    }
  }


  internal fun hasPartialState(): Boolean {
    return documentTracker.readLock {
      if (affectedChangeListsIds.size > 1) return@readLock true

      var hasIncluded = false
      var hasExcluded = false
      blocks.forEach {
        if (it.excludedFromCommit) {
          hasExcluded = true
        }
        else {
          hasIncluded = true
        }
      }
      return@readLock hasIncluded && hasExcluded
    }
  }

  override fun hasPartialChangesToCommit(): Boolean {
    return documentTracker.readLock {
      affectedChangeLists.size > 1 || blocks.any { it.excludedFromCommit }
    }
  }

  @CalledInAwt
  override fun handlePartialCommit(side: Side, changelistIds: List<String>, honorExcludedFromCommit: Boolean): PartialCommitHelper {
    val markers = changelistIds.mapTo(HashSet()) { ChangeListMarker(it) }
    val toCommitCondition: (Block) -> Boolean = {
      markers.contains(it.marker) &&
      (!honorExcludedFromCommit || !it.excludedFromCommit)
    }

    val contentToCommit = documentTracker.getContentWithPartiallyAppliedBlocks(side, toCommitCondition)

    return object : PartialCommitHelper(contentToCommit) {
      override fun applyChanges() {
        if (isReleased) return

        val success = updateDocument(side) { doc ->
          documentTracker.doFrozen(side) {
            documentTracker.partiallyApplyBlocks(side, toCommitCondition, { _, _ -> })

            doc.setText(contentToCommit)
          }
        }

        if (!success) {
          LOG.warn("Can't update document state on partial commit: $virtualFile")
        }
      }
    }
  }

  @CalledInAwt
  override fun rollbackChangelistChanges(changelistsIds: List<String>, rollbackRangesExcludedFromCommit: Boolean) {
    val idsSet = changelistsIds.toSet()
    runBulkRollback {
      idsSet.contains(it.marker.changelistId) &&
      (rollbackRangesExcludedFromCommit || !it.excludedFromCommit)
    }
  }


  protected class MyLineStatusMarkerRenderer(override val tracker: ChangelistsLocalLineStatusTracker) :
    LocalLineStatusTracker.LocalLineStatusMarkerRenderer(tracker) {

    override fun createMerger(editor: Editor): VisibleRangeMerger {
      return object : VisibleRangeMerger(editor) {
        override fun isIgnored(range: Range): Boolean {
          return range is LocalRange && range.changelistId != tracker.defaultMarker.changelistId
        }
      }
    }

    override fun createAdditionalInfoPanel(editor: Editor,
                                           range: Range,
                                           mousePosition: Point?,
                                           disposable: Disposable): JComponent? {
      if (range !is LocalRange) return null

      val changeLists = ChangeListManager.getInstance(tracker.project).changeLists
      val rangeList = changeLists.find { it.id == range.changelistId } ?: return null

      val group = DefaultActionGroup()
      if (changeLists.size > 1) {
        group.add(Separator("Changelists"))
        for (changeList in changeLists) {
          group.add(MoveToChangeListAction(editor, range, mousePosition, changeList))
        }
        group.add(Separator.getInstance())
      }
      group.add(MoveToAnotherChangeListAction(editor, range, mousePosition))


      val link = ActionGroupLink(rangeList.name, null, group)

      val moveChangesShortcutSet = ActionManager.getInstance().getAction("Vcs.MoveChangedLinesToChangelist").shortcutSet
      object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
          link.linkLabel.doClick()
        }
      }.registerCustomShortcutSet(moveChangesShortcutSet, editor.component, disposable)

      val shortcuts = moveChangesShortcutSet.shortcuts
      if (shortcuts.isNotEmpty()) {
        link.linkLabel.toolTipText = "Move lines to another changelist (${KeymapUtil.getShortcutText(shortcuts.first())})"
      }

      val panel = JPanel(BorderLayout())
      panel.add(link, BorderLayout.CENTER)
      panel.border = JBUI.Borders.emptyLeft(7)
      panel.isOpaque = false
      return panel
    }

    private inner class MoveToAnotherChangeListAction(editor: Editor, range: Range, val mousePosition: Point?)
      : RangeMarkerAction(editor, range, null) {
      init {
        templatePresentation.text = "New Changelist..."
      }

      override fun isEnabled(editor: Editor, range: Range): Boolean = range is LocalRange

      override fun actionPerformed(editor: Editor, range: Range) {
        MoveChangesLineStatusAction.moveToAnotherChangelist(tracker, range as LocalRange)
        reopenRange(editor, range, mousePosition)
      }
    }

    private inner class MoveToChangeListAction(editor: Editor, range: Range, val mousePosition: Point?, val changelist: LocalChangeList)
      : RangeMarkerAction(editor, range, null) {
      init {
        templatePresentation.text = StringUtil.trimMiddle(changelist.name, 60)
      }

      override fun isEnabled(editor: Editor, range: Range): Boolean = range is LocalRange

      override fun actionPerformed(editor: Editor, range: Range) {
        tracker.moveToChangelist(range, changelist)
        reopenRange(editor, range, mousePosition)
      }
    }

    private fun reopenRange(editor: Editor, range: Range, mousePosition: Point?) {
      val newRange = tracker.findRange(range)
      if (newRange != null) tracker.renderer.showHintAt(editor, newRange, mousePosition)
    }
  }


  @CalledInAwt
  override fun moveToChangelist(range: Range, changelist: LocalChangeList) {
    val newRange = findBlock(range)
    if (newRange != null) {
      moveToChangelist({ it == newRange }, changelist)
    }
  }

  @CalledInAwt
  override fun moveToChangelist(lines: BitSet, changelist: LocalChangeList) {
    moveToChangelist({ it.isSelectedByLine(lines) }, changelist)
  }

  @CalledInAwt
  private fun moveToChangelist(condition: (Block) -> Boolean, changelist: LocalChangeList) {
    changeListManager.executeUnderDataLock {
      if (changeListManager.getChangeList(changelist.id) == null) return@executeUnderDataLock
      val newMarker = ChangeListMarker(changelist)

      documentTracker.writeLock {
        moveMarkers(condition, newMarker)
        updateAffectedChangeLists()
      }
    }
  }


  override fun getExcludedFromCommitState(changelistId: String): ExclusionState {
    val marker = ChangeListMarker(changelistId)
    var hasIncluded = false
    var hasExcluded = false
    documentTracker.readLock {
      for (block in blocks) {
        if (block.marker == marker) {
          if (block.excludedFromCommit) {
            hasExcluded = true
          }
          else {
            hasIncluded = true
          }
        }
      }
    }

    if (!hasExcluded && !hasIncluded) return ExclusionState.NO_CHANGES
    if (hasExcluded && hasIncluded) return ExclusionState.PARTIALLY
    if (hasExcluded) return ExclusionState.ALL_EXCLUDED
    return ExclusionState.ALL_INCLUDED // no changes - all included
  }

  @CalledInAwt
  override fun setExcludedFromCommit(isExcluded: Boolean) {
    setExcludedFromCommit({ true }, isExcluded)

    if (!isOperational() || !isExcluded) shouldInitializeWithExcludedFromCommit = isExcluded
  }

  override fun setExcludedFromCommit(range: Range, isExcluded: Boolean) {
    val newRange = findBlock(range)
    setExcludedFromCommit({ it == newRange }, isExcluded)
  }

  override fun setExcludedFromCommit(lines: BitSet, isExcluded: Boolean) {
    setExcludedFromCommit({ it.isSelectedByLine(lines) }, isExcluded)
  }

  private fun setExcludedFromCommit(condition: (Block) -> Boolean, isExcluded: Boolean) {
    documentTracker.writeLock {
      for (block in blocks) {
        if (condition(block)) {
          block.excludedFromCommit = isExcluded
        }
      }
    }
    fireExcludedFromCommitChanged()
  }

  private fun fireExcludedFromCommitChanged() {
    eventDispatcher.multicaster.onExcludedFromCommitChange(this)
  }


  @CalledInAwt
  internal fun storeTrackerState(): FullState {
    return documentTracker.readLock {
      val vcsContent = documentTracker.getContent(Side.LEFT)
      val currentContent = documentTracker.getContent(Side.RIGHT)

      val rangeStates = collectRangeStates()

      FullState(virtualFile, rangeStates, vcsContent.toString(), currentContent.toString())
    }
  }

  @CalledInAwt
  internal fun restoreState(state: State): Boolean {
    if (state is FullState) {
      return restoreFullState(state)
    }
    else {
      return restoreState(state.ranges)
    }
  }

  @CalledInAwt
  private fun collectRangeStates(): List<RangeState> {
    return documentTracker.readLock {
      blocks.map { RangeState(it.range, it.marker.changelistId) }
    }
  }

  private fun restoreFullState(state: FullState): Boolean {
    var success = false
    documentTracker.doFrozen {
      // ensure that changelist can't disappear in the middle of operation
      changeListManager.executeUnderDataLock {
        documentTracker.writeLock {
          success = documentTracker.setFrozenState(state.vcsContent, state.currentContent, state.ranges.map { it.range })
          if (success) {
            restoreChangelistsState(state.ranges)
          }
        }
      }

      if (success) {
        updateDocument(Side.LEFT) {
          vcsDocument.setText(state.vcsContent)
        }
      }
    }
    return success
  }

  @CalledInAwt
  private fun restoreState(states: List<RangeState>): Boolean {
    var success = false
    documentTracker.doFrozen {
      // ensure that changelist can't disappear in the middle of operation
      changeListManager.executeUnderDataLock {
        documentTracker.writeLock {
          success = documentTracker.setFrozenState(states.map { it.range })
          if (success) {
            restoreChangelistsState(states)
          }
        }
      }
    }
    return success
  }

  private fun restoreChangelistsState(states: List<RangeState>) {
    val changelistIds = changeListManager.changeLists.map { it.id }
    val idToMarker = ContainerUtil.newMapFromKeys(changelistIds.iterator()) { ChangeListMarker(it) }

    assert(blocks.size == states.size)
    blocks.forEachIndexed { i, block ->
      block.marker = idToMarker[states[i].changelistId] ?: defaultMarker
    }

    updateAffectedChangeLists()
    updateHighlighters()
  }


  private class MyUndoableAction(
    project: Project,
    document: Document,
    var states: List<RangeState>?,
    val undo: Boolean
  ) : BasicUndoableAction(document) {
    val projectRef: WeakReference<Project> = WeakReference(project)

    fun drop() {
      states = null
    }

    override fun undo() {
      if (undo) restore()
    }

    override fun redo() {
      if (!undo) restore()
    }

    private fun restore() {
      val document = affectedDocuments!!.single().document
      val project = projectRef.get()
      val rangeStates = states
      if (document != null && project != null && rangeStates != null) {
        val tracker = LineStatusTrackerManager.getInstance(project).getLineStatusTracker(document)
        if (tracker is ChangelistsLocalLineStatusTracker) {
          tracker.restoreState(rangeStates)
        }
      }
    }
  }


  private val eventDispatcher = EventDispatcher.create(PartialLocalLineStatusTracker.Listener::class.java)
  override fun addListener(listener: PartialLocalLineStatusTracker.Listener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }


  internal class FullState(virtualFile: VirtualFile,
                           ranges: List<RangeState>,
                           val vcsContent: String,
                           val currentContent: String)
    : State(virtualFile, ranges)

  internal open class State(
    val virtualFile: VirtualFile,
    val ranges: List<RangeState>
  )

  internal class RangeState(
    val range: com.intellij.diff.util.Range,
    val changelistId: String
  )

  protected data class ChangeListMarker(val changelistId: String) {
    constructor(changelist: LocalChangeList) : this(changelist.id)
  }


  protected data class MyBlockData(var marker: ChangeListMarker? = null,
                                   var excludedFromCommit: Boolean = false
  ) : LineStatusTrackerBase.BlockData()

  override fun createBlockData(): BlockData = MyBlockData()
  override val Block.ourData: MyBlockData get() = getBlockData(this) as MyBlockData

  private var Block.marker: ChangeListMarker
    get() = this.ourData.marker!! // can be null in MyLineTrackerListener, until `onBlockAdded` is called
    set(value) {
      this.ourData.marker = value
    }

  private var Block.excludedFromCommit: Boolean
    get() = this.ourData.excludedFromCommit
    set(value) {
      this.ourData.excludedFromCommit = value
    }


  companion object {
    @JvmStatic
    fun createTracker(project: Project,
                      document: Document,
                      virtualFile: VirtualFile,
                      mode: Mode): ChangelistsLocalLineStatusTracker {
      return ChangelistsLocalLineStatusTracker(project, document, virtualFile, mode)
    }
  }
}
