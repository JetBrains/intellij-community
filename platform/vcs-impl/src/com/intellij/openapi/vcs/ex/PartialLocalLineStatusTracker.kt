/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.openapi.vcs.ex

import com.intellij.diff.util.Side
import com.intellij.ide.file.BatchFileChangeListener
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.IdeActions
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.CommandEvent
import com.intellij.openapi.command.CommandListener
import com.intellij.openapi.command.CommandProcessor
import com.intellij.openapi.command.undo.BasicUndoableAction
import com.intellij.openapi.command.undo.UndoManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.ChangeListManager
import com.intellij.openapi.vcs.changes.ChangeListManagerImpl
import com.intellij.openapi.vcs.changes.ChangeListWorker
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.ex.DocumentTracker.Block
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker.LocalRange
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.EventDispatcher
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.containers.WeakList
import com.intellij.util.ui.JBUI
import org.jetbrains.annotations.CalledInAwt
import java.awt.BorderLayout
import java.awt.Graphics
import java.awt.Point
import java.lang.ref.WeakReference
import java.util.*
import java.util.concurrent.atomic.AtomicInteger
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel
import kotlin.collections.HashSet

class PartialLocalLineStatusTracker(project: Project,
                                    document: Document,
                                    virtualFile: VirtualFile,
                                    mode: Mode
) : LineStatusTracker<LocalRange>(project, document, virtualFile, mode), ChangeListWorker.PartialChangeTracker {
  private val changeListManager = ChangeListManagerImpl.getInstanceImpl(project)
  private val lstManager = LineStatusTrackerManager.getInstance(project) as LineStatusTrackerManager
  private val undoManager = UndoManager.getInstance(project)

  override val renderer = MyLineStatusMarkerRenderer(this)

  private var defaultMarker: ChangeListMarker
  private var currentMarker: ChangeListMarker? = null

  private val affectedChangeLists = HashSet<String>()

  private val batchChangeTaskCounter: AtomicInteger = AtomicInteger()
  private var hasUndoInCommand: Boolean = false

  private val undoableActions: WeakList<MyUndoableAction> = WeakList()

  init {
    defaultMarker = ChangeListMarker(changeListManager.defaultChangeList)
    affectedChangeLists.add(defaultMarker.changelistId)

    val connection = application.messageBus.connect(disposable)
    connection.subscribe(BatchFileChangeListener.TOPIC, MyBatchFileChangeListener())

    document.addDocumentListener(MyUndoDocumentListener(), disposable)
    CommandProcessor.getInstance().addCommandListener(MyUndoCommandListener(), disposable)
    Disposer.register(disposable, Disposable { dropExistingUndoActions() })

    assert(blocks.isEmpty())
  }

  override fun Block.toRange(): LocalRange = LocalRange(this.start, this.end, this.vcsStart, this.vcsEnd, this.innerRanges,
                                                        this.marker.changelistId)

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

    if (newIds.isEmpty()) {
      if (affectedChangeLists.size == 1) {
        newIds.add(affectedChangeLists.single())
      }
      else {
        newIds.add(defaultMarker.changelistId)
      }
    }

    oldIds.addAll(affectedChangeLists)

    affectedChangeLists.clear()
    affectedChangeLists.addAll(newIds)

    if (oldIds != newIds) {
      if (notifyChangeListManager) {
        // It's OK to call this under documentTracker.writeLock, as this method will not grab CLM lock.
        changeListManager.notifyChangelistsChanged()
      }

      eventDispatcher.multicaster.onChangelistsChange()
    }
  }

  @CalledInAwt
  fun setBaseRevision(vcsContent: CharSequence, changelistId: String?) {
    currentMarker = if (changelistId != null) ChangeListMarker(changelistId) else null
    try {
      setBaseRevision(vcsContent)
      dropExistingUndoActions()
    }
    finally {
      currentMarker = null
    }

    if (isValid()) eventDispatcher.multicaster.onBecomingValid()
  }


  override fun initChangeTracking(defaultId: String, changelistsIds: List<String>) {
    documentTracker.writeLock {
      defaultMarker = ChangeListMarker(defaultId)

      val idsSet = changelistsIds.toSet()
      moveMarkers({ !idsSet.contains(it.changelistId) }, defaultMarker)
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

      moveMarkers({ it.changelistId == listId }, defaultMarker)

      if (affectedChangeLists.size == 1 && affectedChangeLists.contains(listId)) {
        affectedChangeLists.clear()
        affectedChangeLists.add(defaultMarker.changelistId)
      }
    }
  }

  override fun moveChanges(fromListId: String, toListId: String) {
    documentTracker.writeLock {
      if (!affectedChangeLists.contains(fromListId)) return@writeLock

      moveMarkers({ it.changelistId == fromListId }, ChangeListMarker(toListId))
    }
  }

  override fun moveChangesTo(toListId: String) {
    documentTracker.writeLock {
      moveMarkers({ true }, ChangeListMarker(toListId))
    }
  }

  private fun moveMarkers(condition: (ChangeListMarker) -> Boolean, toMarker: ChangeListMarker) {
    val affectedBlocks = mutableListOf<Block>()

    for (block in blocks) {
      if (condition(block.marker)) {
        block.marker = toMarker
        affectedBlocks.add(block)
      }
    }

    dropExistingUndoActions()
    updateAffectedChangeLists(false) // no need to notify CLM, as we're inside it's action

    for (block in affectedBlocks) {
      updateHighlighter(block)
    }
  }


  private inner class MyUndoDocumentListener : DocumentListener {
    override fun beforeDocumentChange(event: DocumentEvent?) {
      if (hasUndoInCommand) return
      if (undoManager.isRedoInProgress || undoManager.isUndoInProgress) return
      hasUndoInCommand = true

      registerUndoAction(true)
    }
  }

  private inner class MyUndoCommandListener : CommandListener {
    override fun beforeCommandFinished(event: CommandEvent?) {
      if (hasUndoInCommand) {
        hasUndoInCommand = false

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

  private inner class MyBatchFileChangeListener : BatchFileChangeListener {
    override fun batchChangeStarted(eventProject: Project) {
      if (eventProject != project) return
      if (batchChangeTaskCounter.getAndIncrement() == 0) {
        documentTracker.freeze(Side.LEFT)
        documentTracker.freeze(Side.RIGHT)
      }
    }

    override fun batchChangeCompleted(eventProject: Project) {
      if (eventProject != project) return
      application.invokeLater(
        {
          if (batchChangeTaskCounter.decrementAndGet() == 0) {
            documentTracker.unfreeze(Side.LEFT)
            documentTracker.unfreeze(Side.RIGHT)
          }
        }, ModalityState.any())
    }
  }

  private inner class PartialDocumentTrackerHandler : LineStatusTrackerBase<LocalRange>.MyDocumentTrackerHandler() {
    override fun onRangeAdded(block: Block) {
      super.onRangeAdded(block)

      if (block.ourData.marker == null) { // do not override markers, that are set via other methods of this listener
        block.marker = defaultMarker
      }
    }

    override fun onRangeRefreshed(before: Block, after: List<Block>) {
      super.onRangeRefreshed(before, after)

      val marker = before.marker
      for (block in after) {
        block.marker = marker
      }
    }

    override fun onRangesChanged(before: List<Block>, after: Block) {
      super.onRangesChanged(before, after)

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

      after.marker = before.marker
    }

    override fun onRangesMerged(range1: Block, range2: Block, merged: Block): Boolean {
      val superMergeable = super.onRangesMerged(range1, range2, merged)

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

    override fun afterRefresh() {
      super.afterRefresh()

      updateAffectedChangeLists()
    }

    override fun afterRangeChange() {
      super.afterRangeChange()

      updateAffectedChangeLists()
    }

    override fun afterExplicitChange() {
      super.afterExplicitChange()

      updateAffectedChangeLists()
    }

    override fun onUnfreeze(side: Side) {
      super.onUnfreeze(side)

      if (isValid()) eventDispatcher.multicaster.onBecomingValid()
    }
  }


  fun getPartiallyAppliedContent(side: Side, changelistIds: List<String>): String {
    return runReadAction {
      val markers = changelistIds.mapTo(HashSet()) { ChangeListMarker(it) }
      documentTracker.getContentWithPartiallyAppliedBlocks(side) { markers.contains(it.marker) }
    }
  }

  @CalledInAwt
  fun handlePartialCommit(side: Side, changelistId: String): PartialCommitHelper {
    val marker = ChangeListMarker(changelistId)

    val contentToCommit = documentTracker.getContentWithPartiallyAppliedBlocks(side) { it.marker == marker }

    return object : PartialCommitHelper(contentToCommit) {
      override fun applyChanges() {
        if (isReleased) return

        val success = updateDocument(side) { doc ->
          documentTracker.doFrozen(side) {
            documentTracker.partiallyApplyBlocks(side, { it.marker == marker }, { _, _ -> })

            doc.setText(contentToCommit)
          }
        }

        if (!success) {
          LOG.warn("Can't update document state on partial commit: $virtualFile")
        }
      }
    }
  }

  abstract class PartialCommitHelper(val content: String) {
    @CalledInAwt abstract fun applyChanges()
  }

  @CalledInAwt
  fun rollbackChangelistChanges(changelistId: String) {
    runBulkRollback { it.marker.changelistId == changelistId }
  }


  protected class MyLineStatusMarkerRenderer(override val tracker: PartialLocalLineStatusTracker) :
    LineStatusTracker.LocalLineStatusMarkerRenderer(tracker) {

    override fun paint(editor: Editor, range: Range, g: Graphics) {
      if (range !is LocalRange ||
          range.changelistId == tracker.defaultMarker.changelistId) {
        super.paint(editor, range, g)
      } else {
        paintIgnoredRange(g, editor, range)
      }
    }

    override fun createAdditionalInfoPanel(editor: Editor, range: Range): JComponent? {
      if (range !is LocalRange) return null

      val list = ChangeListManager.getInstance(tracker.project).getChangeList(range.changelistId) ?: return null

      val panel = JPanel(BorderLayout())
      panel.add(JLabel(list.name), BorderLayout.CENTER)
      panel.border = JBUI.Borders.emptyLeft(5)
      panel.isOpaque = false
      return panel
    }

    override fun createToolbarActions(editor: Editor, range: Range, mousePosition: Point?): List<AnAction> {
      val actions = ArrayList<AnAction>()
      actions.addAll(super.createToolbarActions(editor, range, mousePosition))
      actions.add(SetChangeListAction(editor, range, mousePosition))
      return actions
    }

    private inner class SetChangeListAction(editor: Editor, range: Range, val mousePosition: Point?)
      : RangeMarkerAction(editor, range, IdeActions.MOVE_TO_ANOTHER_CHANGE_LIST) {
      override fun isEnabled(editor: Editor, range: Range): Boolean = range is LocalRange

      override fun actionPerformed(editor: Editor, range: Range) {
        MoveChangesLineStatusAction.moveToAnotherChangelist(tracker, range as LocalRange)

        val newRange = tracker.findRange(range)
        if (newRange != null) tracker.renderer.showHintAt(editor, newRange, mousePosition)
      }
    }
  }


  @CalledInAwt
  fun moveToChangelist(range: Range, changelist: LocalChangeList) {
    documentTracker.writeLock {
      val block = findBlock(range)
      if (block != null) moveToChangelist(listOf(block), changelist)
    }
  }

  @CalledInAwt
  fun moveToChangelist(lines: BitSet, changelist: LocalChangeList) {
    documentTracker.writeLock {
      moveToChangelist(blocks.filter { it.isSelectedByLine(lines) }, changelist)
    }
  }

  @CalledInAwt
  private fun moveToChangelist(blocks: List<Block>, changelist: LocalChangeList) {
    val newMarker = ChangeListMarker(changelist)
    for (block in blocks) {
      if (block.marker != newMarker) {
        block.marker = newMarker
        updateHighlighter(block)
      }
    }

    dropExistingUndoActions()
    updateAffectedChangeLists()
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
    val idToMarker = ContainerUtil.newMapFromKeys(changelistIds.iterator(), { ChangeListMarker(it) })

    assert(blocks.size == states.size)
    blocks.forEachIndexed { i, block ->
      block.marker = idToMarker[states[i].changelistId] ?: defaultMarker
      updateHighlighter(block)
    }

    updateAffectedChangeLists()
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
        if (tracker is PartialLocalLineStatusTracker) {
          tracker.restoreState(rangeStates)
        }
      }
    }
  }


  private val eventDispatcher = EventDispatcher.create(Listener::class.java)

  fun addListener(listener: Listener, disposable: Disposable) {
    eventDispatcher.addListener(listener, disposable)
  }

  interface Listener : EventListener {
    @CalledInAwt
    fun onBecomingValid() {
    }

    @CalledInAwt
    fun onChangelistsChange() {
    }
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

  class LocalRange(line1: Int, line2: Int, vcsLine1: Int, vcsLine2: Int, innerRanges: List<InnerRange>?,
                   val changelistId: String)
    : Range(line1, line2, vcsLine1, vcsLine2, innerRanges)

  protected data class ChangeListMarker(val changelistId: String) {
    constructor(changelist: LocalChangeList) : this(changelist.id)
  }

  protected data class MyBlockData(var marker: ChangeListMarker? = null) : LineStatusTrackerBase.BlockData()

  override fun createBlockData(): BlockData = MyBlockData()
  override val Block.ourData: MyBlockData get() = getBlockData(this) as MyBlockData

  private var Block.marker: ChangeListMarker
    get() = this.ourData.marker!! // can be null in MyLineTrackerListener, until `onBlockAdded` is called
    set(value) {
      this.ourData.marker = value
    }

  companion object {
    @JvmStatic
    fun createTracker(project: Project,
                      document: Document,
                      virtualFile: VirtualFile,
                      mode: Mode): PartialLocalLineStatusTracker {
      return PartialLocalLineStatusTracker(project, document, virtualFile, mode)
    }

    @JvmStatic
    fun createTracker(project: Project,
                      document: Document,
                      virtualFile: VirtualFile,
                      mode: Mode,
                      events: List<DocumentEvent>): PartialLocalLineStatusTracker {
      val tracker = createTracker(project, document, virtualFile, mode)

      for (event in events.reversed()) {
        tracker.updateDocument(Side.LEFT) { vcsDocument ->
          vcsDocument.replaceString(event.offset, event.offset + event.newLength, event.oldFragment)
        }
      }

      return tracker
    }
  }
}
