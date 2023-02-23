// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.Side
import com.intellij.ide.DataManager
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
import com.intellij.openapi.diff.DefaultFlagsProvider
import com.intellij.openapi.diff.DefaultLineFlags
import com.intellij.openapi.diff.LineStatusMarkerDrawUtil
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vcs.VcsBundle
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.ChangeListWorker
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vcs.ex.LineStatusTrackerBlockOperations.Companion.isSelectedByLine
import com.intellij.openapi.vcs.impl.ActiveChangeListTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.components.DropDownLink
import com.intellij.util.EventDispatcher
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.WeakList
import com.intellij.util.ui.JBUI
import com.intellij.vcsUtil.VcsUtil
import org.jetbrains.annotations.CalledInAny
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Point
import java.lang.ref.WeakReference
import java.util.*
import javax.swing.JComponent
import javax.swing.JPanel

/**
 * Tracker that is used for "Partial Changelist" and "Partial Commit" features, allowing to commit a subset of file's changed lines.
 *
 * The tracker stores changelists ids, see [com.intellij.openapi.vcs.changes.ChangeListWorker.PartialChangeTracker].
 *
 * @see com.intellij.openapi.vcs.impl.PartialChangesUtil
 * @see LineStatusTrackerManager.arePartialChangelistsEnabled
 */
interface PartialLocalLineStatusTracker : LineStatusTracker<LocalRange> {
  fun getAffectedChangeListsIds(): List<String>

  fun moveToChangelist(range: Range, changelist: LocalChangeList)
  fun moveToChangelist(lines: BitSet, changelist: LocalChangeList)


  fun getExcludedFromCommitState(changelistId: String): ExclusionState

  fun setExcludedFromCommit(isExcluded: Boolean)
  fun setExcludedFromCommit(changelistId: String, isExcluded: Boolean)
  fun setExcludedFromCommit(range: Range, isExcluded: Boolean)
  fun setExcludedFromCommit(lines: BitSet, isExcluded: Boolean)


  /**
   * @return `false` if file can be committed as is
   */
  fun hasPartialChangesToCommit(): Boolean

  @RequiresEdt
  fun handlePartialCommit(side: Side, changelistIds: List<String>, honorExcludedFromCommit: Boolean): PartialCommitHelper
  fun getChangesToBeCommitted(side: Side, changelistIds: List<String>, honorExcludedFromCommit: Boolean): String?
  fun getPartialCommitContent(changelistIds: List<String>, honorExcludedFromCommit: Boolean): PartialCommitContent?
  fun rollbackChanges(changelistsIds: List<String>, honorExcludedFromCommit: Boolean)


  fun addListener(listener: Listener, disposable: Disposable)

  open class ListenerAdapter : Listener
  interface Listener : EventListener {
    fun onBecomingValid(tracker: PartialLocalLineStatusTracker) = Unit

    @CalledInAny
    fun onChangeListsChange(tracker: PartialLocalLineStatusTracker) = Unit

    @CalledInAny
    fun onChangeListMarkerChange(tracker: PartialLocalLineStatusTracker) = Unit

    fun onExcludedFromCommitChange(tracker: PartialLocalLineStatusTracker) = Unit
  }
}

abstract class PartialCommitHelper(val content: String) {
  @RequiresEdt
  abstract fun applyChanges()
}

class PartialCommitContent(val vcsContent: CharSequence, val currentContent: CharSequence, val rangesToCommit: List<LocalRange>)

enum class ExclusionState { ALL_INCLUDED, ALL_EXCLUDED, PARTIALLY, NO_CHANGES }

class LocalRange(line1: Int, line2: Int, vcsLine1: Int, vcsLine2: Int, innerRanges: List<InnerRange>?,
                 val changelistId: String, val isExcludedFromCommit: Boolean)
  : Range(line1, line2, vcsLine1, vcsLine2, innerRanges)


class ChangelistsLocalLineStatusTracker internal constructor(project: Project,
                                                             document: Document,
                                                             virtualFile: VirtualFile
) : LocalLineStatusTrackerImpl<LocalRange>(project, document, virtualFile),
    PartialLocalLineStatusTracker,
    ChangeListWorker.PartialChangeTracker {
  private val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
  private val lstManager = LineStatusTrackerManager.getInstanceImpl(project)
  private val activeChangeListTracker = ActiveChangeListTracker.getInstance(project)
  private val undoManager = UndoManager.getInstance(project)

  private val undoStateRecordingEnabled = Registry.`is`("vcs.enable.partial.changelists.undo")
  private val redoStateRecordingEnabled = Registry.`is`("vcs.enable.partial.changelists.redo")

  override val renderer: MyLineStatusMarkerRenderer = MyLineStatusMarkerRenderer(this)

  private var defaultMarker: ChangeListMarker

  private var initialChangeListId: String? = null // Initial state from ChangeListWorker or DefaultChangelist if initialized by typing
  private var lastKnownTrackerChangeListId: String? = null // Track changelist for a file without changed lines. Ex: executable bit change.
  private val affectedChangeLists = HashSet<String>()

  private var hasUndoInCommand: Boolean = false

  private val initialExcludeState = mutableMapOf<ChangeListMarker, Boolean>()

  private val undoableActions: WeakList<MyUndoableAction> = WeakList()

  init {
    defaultMarker = ChangeListMarker(changeListManager.defaultChangeList)
    affectedChangeLists.add(defaultMarker.changelistId)

    if (undoStateRecordingEnabled) {
      document.addDocumentListener(MyUndoDocumentListener(), disposable)
      project.messageBus.connect(disposable).subscribe(CommandListener.TOPIC, MyUndoCommandListener())
      Disposer.register(disposable, Disposable { dropExistingUndoActions() })
    }

    documentTracker.addHandler(PartialDocumentTrackerHandler())
    assert(blocks.isEmpty())
  }

  override fun toRange(block: Block): LocalRange = LocalRange(block.start, block.end, block.vcsStart, block.vcsEnd, block.innerRanges,
                                                              block.marker.changelistId, block.excludedFromCommit)

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

  @RequiresEdt
  override fun setBaseRevision(vcsContent: CharSequence) {
    val changelistId = if (!isInitialized) initialChangeListId else null
    initialChangeListId = null

    setBaseRevisionContent(vcsContent) {
      if (changelistId != null) {
        activeChangeListTracker.runUnderChangeList(changelistId) {
          documentTracker.writeLock {
            documentTracker.updateFrozenContentIfNeeded()
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

  @RequiresEdt
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
      if (undoManager.isUndoOrRedoInProgress) return
      if (CommandProcessor.getInstance().currentCommand == null) return
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

  @RequiresEdt
  private fun registerUndoAction(undo: Boolean) {
    val undoState = collectRangeStates()
    val action = MyUndoableAction(project, document, undoState, undo)
    undoManager.undoableActionPerformed(action)
    undoableActions.add(action)
  }

  private inner class PartialDocumentTrackerHandler : DocumentTracker.Handler {
    override fun onRangeRefreshed(before: Block, after: List<Block>) {
      val isExcludedFromCommit = before.excludedFromCommit
      val marker = before.marker
      for (block in after) {
        block.excludedFromCommit = isExcludedFromCommit
        block.marker = marker
      }
    }

    override fun onRangesChanged(before: List<Block>, after: Block) {
      val activeMarker = activeChangeListTracker.getActiveChangeListId()?.let { ChangeListMarker(it) }
                         ?: defaultMarker

      if (before.isEmpty()) {
        val changeListBlocks = blocks.filter { it.marker == activeMarker }
        // default value for new blocks: include only if all changed blocks from this changelist are included
        after.excludedFromCommit = changeListBlocks.isEmpty() || changeListBlocks.any { it.excludedFromCommit }
      }
      else {
        after.excludedFromCommit = before.all { it.excludedFromCommit }
      }

      val affectedMarkers = before.map { it.marker }.distinct()
      if (affectedMarkers.isEmpty()) {
        // put new blocks into original changelist when initializing base revision
        // put new blocks into default changelist otherwise
        after.marker = activeMarker
      }
      else if (affectedMarkers.size == 1) {
        // put block into same changelist on consensus
        after.marker = affectedMarkers.single()
      }
      else {
        // conflict - put block into default changelist, notify user
        after.marker = defaultMarker
        lstManager.notifyInactiveRangesDamaged(virtualFile)
      }
    }

    override fun onRangeShifted(before: Block, after: Block) {
      after.excludedFromCommit = before.excludedFromCommit
      after.marker = before.marker
    }

    override fun mergeRanges(block1: Block, block2: Block, merged: Block): Boolean {
      if (block1.marker == block2.marker &&
          block1.excludedFromCommit == block2.excludedFromCommit) {
        merged.marker = block1.marker
        merged.excludedFromCommit = block1.excludedFromCommit
        return true
      }

      if (block1.range.isEmpty) {
        merged.marker = block2.marker
        merged.excludedFromCommit = block2.excludedFromCommit
        return true
      }
      if (block2.range.isEmpty) {
        merged.marker = block1.marker
        merged.excludedFromCommit = block1.excludedFromCommit
        return true
      }

      return false
    }

    override fun afterBulkRangeChange(isDirty: Boolean) {
      updateAffectedChangeLists()
      fireExcludedFromCommitChanged()
    }

    override fun onUnfreeze() {
      if (initialExcludeState.isNotEmpty()) {
        blocks.forEach { block -> initialExcludeState[block.marker]?.let { block.excludedFromCommit = it } }
        initialExcludeState.clear()
      }

      if (isValid()) eventDispatcher.multicaster.onBecomingValid(this@ChangelistsLocalLineStatusTracker)
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

  internal fun hasPendingPartialState(): Boolean {
    return documentTracker.readLock {
      initialExcludeState.isNotEmpty() ||
      initialChangeListId != null && !affectedChangeListsIds.contains(initialChangeListId)
    }
  }

  override fun hasPartialChangesToCommit(): Boolean {
    return documentTracker.readLock {
      affectedChangeLists.size > 1 || blocks.any { it.excludedFromCommit }
    }
  }

  @RequiresEdt
  override fun handlePartialCommit(side: Side, changelistIds: List<String>, honorExcludedFromCommit: Boolean): PartialCommitHelper {
    val toCommitCondition = createToCommitCondition(changelistIds, honorExcludedFromCommit)

    val contentToCommit = documentTracker.writeLock {
      documentTracker.updateFrozenContentIfNeeded()
      documentTracker.getContentWithPartiallyAppliedBlocks(side, toCommitCondition)
      ?: run {
        LOG.warn("handlePartialCommit - frozen tracker: $this")
        documentTracker.getContent(side).toString()
      }
    }

    return object : PartialCommitHelper(contentToCommit) {
      override fun applyChanges() {
        if (isReleased) return

        val success = updateDocument(side) { doc ->
          documentTracker.doFrozen(side) {
            documentTracker.partiallyApplyBlocks(side, toCommitCondition)

            doc.setText(contentToCommit)
          }
        }

        if (!success) {
          LOG.warn("Can't update document state on partial commit: $virtualFile")
        }
      }
    }
  }

  @RequiresEdt
  override fun rollbackChanges(changelistsIds: List<String>, honorExcludedFromCommit: Boolean) {
    val toCommitCondition = createToCommitCondition(changelistsIds, honorExcludedFromCommit)
    runBulkRollback(toCommitCondition)
  }

  override fun getChangesToBeCommitted(side: Side, changelistIds: List<String>, honorExcludedFromCommit: Boolean): String? {
    return documentTracker.readLock {
      if (!isValid() || documentTracker.isFrozen()) return@readLock null
      val toCommitCondition = createToCommitCondition(changelistIds, honorExcludedFromCommit)
      return@readLock documentTracker.getContentWithPartiallyAppliedBlocks(side, toCommitCondition)
                      ?: run {
                        LOG.warn("getChangesToBeCommitted - frozen tracker: $this")
                        documentTracker.getContent(side).toString()
                      }
    }
  }

  override fun getPartialCommitContent(changelistIds: List<String>, honorExcludedFromCommit: Boolean): PartialCommitContent? {
    return documentTracker.readLock {
      if (!isValid()) return@readLock null

      val toCommitCondition = createToCommitCondition(changelistIds, honorExcludedFromCommit)
      val vcsContent = documentTracker.getContent(Side.LEFT)
      val currentContent = documentTracker.getContent(Side.RIGHT)
      val ranges = blocks.filter { !it.range.isEmpty }.filter(toCommitCondition).map { toRange(it) }

      PartialCommitContent(vcsContent, currentContent, ranges)
    }
  }

  private fun createToCommitCondition(changelistsIds: List<String>, honorExcludedFromCommit: Boolean): (Block) -> Boolean {
    val idsSet = changelistsIds.toSet()
    return {
      idsSet.contains(it.marker.changelistId) &&
      (!honorExcludedFromCommit || !it.excludedFromCommit)
    }
  }

  protected class MyLineStatusMarkerRenderer(override val tracker: ChangelistsLocalLineStatusTracker) :
    LocalLineStatusTrackerImpl.LocalLineStatusMarkerRenderer(tracker) {

    override fun paint(editor: Editor, g: Graphics) {
      val flagsProvider = MyFlagsProvider(tracker.defaultMarker.changelistId)
      LineStatusMarkerDrawUtil.paintDefault(editor, g, myTracker, flagsProvider, 0)
    }

    class MyFlagsProvider(private val defaultChangelistId: String) : DefaultFlagsProvider() {
      override fun getFlags(range: Range): DefaultLineFlags {
        val ignored = range is LocalRange && range.changelistId != defaultChangelistId
        return if (ignored) DefaultLineFlags.IGNORED else DefaultLineFlags.DEFAULT
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
        group.add(Separator(VcsBundle.message("ex.changelists")))
        for (changeList in changeLists) {
          group.add(MoveToChangeListAction(editor, range, mousePosition, changeList))
        }
        group.add(Separator.getInstance())
      }
      group.add(MoveToAnotherChangeListAction(editor, range, mousePosition))


      val link = DropDownLink(rangeList.name) { linkLabel ->
        val dataContext = DataManager.getInstance().getDataContext(linkLabel)
        JBPopupFactory.getInstance()
          .createActionGroupPopup(null, group, dataContext, JBPopupFactory.ActionSelectionAid.SPEEDSEARCH, false)
      }

      val moveChangesShortcutSet = ActionManager.getInstance().getAction("Vcs.MoveChangedLinesToChangelist").shortcutSet
      object : DumbAwareAction() {
        override fun actionPerformed(e: AnActionEvent) {
          link.doClick()
        }
      }.registerCustomShortcutSet(moveChangesShortcutSet, editor.component, disposable)

      val shortcuts = moveChangesShortcutSet.shortcuts
      if (shortcuts.isNotEmpty()) {
        link.toolTipText = VcsBundle.message("ex.move.lines.to.another.changelist.0", KeymapUtil.getShortcutText(shortcuts.first()))
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
        templatePresentation.text = VcsBundle.message("ex.new.changelist")
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
        templatePresentation.setText(StringUtil.trimMiddle(changelist.name, 60), false)
      }

      override fun isEnabled(editor: Editor, range: Range): Boolean = range is LocalRange

      override fun actionPerformed(editor: Editor, range: Range) {
        tracker.moveToChangelist(range, changelist)
        reopenRange(editor, range, mousePosition)
      }
    }
  }


  @RequiresEdt
  override fun moveToChangelist(range: Range, changelist: LocalChangeList) {
    val newRange = blockOperations.findBlock(range)
    if (newRange != null) {
      moveToChangelist({ it == newRange }, changelist)
    }
  }

  @RequiresEdt
  override fun moveToChangelist(lines: BitSet, changelist: LocalChangeList) {
    moveToChangelist({ it.isSelectedByLine(lines) }, changelist)
  }

  @RequiresEdt
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

  @RequiresEdt
  override fun setExcludedFromCommit(isExcluded: Boolean) {
    affectedChangeLists.forEach { setExcludedFromCommit(it, isExcluded) }
  }

  override fun setExcludedFromCommit(changelistId: String, isExcluded: Boolean) {
    val marker = ChangeListMarker(changelistId)
    setExcludedFromCommit({ it.marker == marker }, isExcluded)

    if (!isOperational()) initialExcludeState[marker] = isExcluded
  }

  override fun setExcludedFromCommit(range: Range, isExcluded: Boolean) {
    val newRange = blockOperations.findBlock(range)
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

  internal fun resetExcludedFromCommitMarkers() {
    setExcludedFromCommit(false)
    dropExistingUndoActions()
  }

  @RequiresReadLock
  internal fun storeTrackerState(): FullState {
    return documentTracker.readLock {
      val vcsContent = documentTracker.getContent(Side.LEFT)
      val currentContent = documentTracker.getContent(Side.RIGHT)

      val rangeStates = collectRangeStates()

      FullState(virtualFile, rangeStates, vcsContent.toString(), currentContent.toString())
    }
  }

  @RequiresEdt
  internal fun restoreState(state: State): Boolean {
    if (state is FullState) {
      return restoreFullState(state)
    }
    else {
      return restoreState(state.ranges)
    }
  }

  @RequiresReadLock
  private fun collectRangeStates(): List<RangeState> {
    return documentTracker.readLock {
      blocks.map { RangeState(it.range, it.marker.changelistId, it.excludedFromCommit) }
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

  @RequiresEdt
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
      states[i].excludedFromCommit?.let { block.excludedFromCommit = it }
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
    val changelistId: String,
    val excludedFromCommit: Boolean? = null // should not be persisted
  )

  protected data class ChangeListMarker(val changelistId: String) {
    constructor(changelist: LocalChangeList) : this(changelist.id)
  }


  private data class BlockData(var innerRanges: List<Range.InnerRange>? = null,
                               var marker: ChangeListMarker? = null,
                               var excludedFromCommit: Boolean = true)

  private val Block.ourData: BlockData
    get() {
      if (data == null) data = BlockData()
      return data as BlockData
    }

  override var Block.innerRanges: List<Range.InnerRange>?
    get() = this.ourData.innerRanges
    set(value) {
      this.ourData.innerRanges = value
    }

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
    internal fun createTracker(project: Project,
                               document: Document,
                               virtualFile: VirtualFile): ChangelistsLocalLineStatusTracker {
      return ChangelistsLocalLineStatusTracker(project, document, virtualFile)
    }
  }
}
