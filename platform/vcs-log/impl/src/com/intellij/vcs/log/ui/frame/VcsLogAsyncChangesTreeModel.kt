// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.log.ui.frame

import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.actions.diff.ChangeDiffRequestProducer
import com.intellij.openapi.vcs.changes.ui.ChangeDiffRequestChain
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserNode.ValueTag
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.TagChangesBrowserNode
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import com.intellij.util.EventDispatcher
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.vcs.log.CommitId
import com.intellij.vcs.log.Hash
import com.intellij.vcs.log.VcsFullCommitDetails
import com.intellij.vcs.log.VcsLogBundle
import com.intellij.vcs.log.VcsShortCommitDetails
import com.intellij.vcs.log.data.VcsLogData
import com.intellij.vcs.log.history.FileHistoryUtil
import com.intellij.vcs.log.impl.MainVcsLogUiProperties
import com.intellij.vcs.log.impl.MergedChange
import com.intellij.vcs.log.impl.MergedChangeDiffRequestProvider
import com.intellij.vcs.log.impl.VcsLogUiProperties
import com.intellij.vcs.log.impl.VcsLogUiProperties.PropertiesChangeListener
import com.intellij.vcs.log.impl.VcsLogUiProperties.VcsLogUiProperty
import com.intellij.vcs.log.ui.frame.VcsLogChangesTreeComponents.getText
import com.intellij.vcs.log.util.VcsLogUtil
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.EventListener
import java.util.concurrent.atomic.AtomicReference
import javax.swing.tree.DefaultTreeModel

@ApiStatus.Internal
class VcsLogAsyncChangesTreeModel(
  private val logData: VcsLogData,
  private val uiProperties: VcsLogUiProperties,
  parentDisposable: Disposable,
) : SimpleAsyncChangesTreeModel() {
  private val unprocessedSelectionState = AtomicReference<SelectionState?>(null)

  // accessed from BGT in buildTreeModelSync
  @Volatile
  var changesState: ChangesState = ChangesState.Empty(false)
    private set

  val changes: List<Change>
    get() = changesState.asSafely<ChangesState.Changes>()?.changes ?: emptyList()

  // accessed from BGT in buildTreeModelSync
  @Volatile
  var affectedPaths: Collection<FilePath>? = null
    set(value) {
      field = value
      listeners.multicaster.onStateChanged()
    }

  private val listeners = EventDispatcher.create(Listener::class.java)

  // accessed from BGT in buildTreeModelSync
  @Volatile
  private var _isShowChangesFromParents: Boolean = false
    set(value) {
      if (value == field) return
      field = value
      listeners.multicaster.onStateChanged()
    }

  var isShowChangesFromParents: Boolean
    get() = _isShowChangesFromParents
    @RequiresEdt
    set(value) {
      if (uiProperties[MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS] != value) {
        uiProperties[MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS] = value
      }
    }

  // accessed from BGT in buildTreeModelSync
  @Volatile
  private var _isShowOnlyAffectedChanges: Boolean = false
    set(value) {
      if (value == field) return
      field = value
      listeners.multicaster.onStateChanged()
    }
  var isShowOnlyAffectedChanges: Boolean
    get() = _isShowOnlyAffectedChanges
    @RequiresEdt
    set(value) {
      if (uiProperties[MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES] != value) {
        uiProperties[MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES] = value
      }
    }

  init {
    fun updateUiSettings() {
      _isShowChangesFromParents = uiProperties.exists(MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS) &&
                                  uiProperties[MainVcsLogUiProperties.SHOW_CHANGES_FROM_PARENTS]
      _isShowOnlyAffectedChanges = uiProperties.exists(MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES) &&
                                   uiProperties[MainVcsLogUiProperties.SHOW_ONLY_AFFECTED_CHANGES]
    }

    val propertiesChangeListener = object : PropertiesChangeListener {
      override fun <T> onPropertyChanged(property: VcsLogUiProperty<T>) {
        updateUiSettings()
      }
    }
    uiProperties.addChangeListener(propertiesChangeListener, parentDisposable)
    updateUiSettings()

    Disposer.register(parentDisposable, Disposable { unprocessedSelectionState.set(null) })
  }

  fun addListener(disposable: Disposable, listener: Listener) {
    listeners.addListener(listener, disposable)
  }

  fun getCommitDetails(commitId: CommitId): VcsShortCommitDetails {
    val index = logData.getCommitIndex(commitId.hash, commitId.root)
    return logData.miniDetailsGetter.getCachedDataOrPlaceholder(index)
  }

  fun setSelectedDetails(details: List<VcsFullCommitDetails>) {
    val maxSize = VcsLogUtil.getMaxSize(details)
    if (maxSize > VcsLogUtil.getShownChangesLimit()) {
      updateSelectionState(SelectionState.ManyDetails(details, maxSize))
    }
    else {
      updateSelectionState(SelectionState.Details(details))
    }
  }

  fun setSelectionError() {
    updateSelectionState(SelectionState.Error)
  }

  fun setEmptySelection() {
    updateSelectionState(SelectionState.Empty)
  }

  private fun updateSelectionState(state: SelectionState) {
    unprocessedSelectionState.set(state)
    listeners.multicaster.onStateChanged()
  }

  @RequiresBackgroundThread
  override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
    val selectionState = unprocessedSelectionState.getAndSet(null)
    val state = if (selectionState != null) {
      try {
        createChangesState(selectionState).also {
          changesState = it
        }
      }
      catch (e: ProcessCanceledException) {
        unprocessedSelectionState.compareAndSet(null, selectionState)
        throw e
      }
    }
    else changesState

    if (state is ChangesState.Changes) {
      val treeModel = buildTreeModelSync(state.changes, state.changesToParents, affectedPaths, isShowOnlyAffectedChanges, isShowChangesFromParents, grouping)
      val modifiedTreeBuilder = VcsLogChangesTreeModifier.modifyTreeModelBuilder(treeModel, state)
      return modifiedTreeBuilder.build()
    }
    else {
      return TreeModelBuilder.buildEmpty()
    }
  }

  private fun createChangesState(state: SelectionState): ChangesState {
    return when (state) {
      SelectionState.Empty -> ChangesState.Empty(true)
      SelectionState.Error -> ChangesState.Error
      is SelectionState.ManyDetails -> ChangesState.ManyChanges(state.details.size, state.maxDetailsSize) {
        setSelectedDetails(state.details)
      }
      is SelectionState.Details -> {
        val roots = state.details.map(VcsFullCommitDetails::getRoot).toSet()

        val singleCommitDetail = state.details.singleOrNull()
        if (singleCommitDetail == null) {
          ChangesState.Changes(
            roots,
            VcsLogUtil.collectChanges(state.details),
            emptyMap()
          )
        }
        else {
          val changesToParents = if (singleCommitDetail.parents.size > 1) {
            singleCommitDetail.parents.indices.associate { i ->
              CommitId(singleCommitDetail.parents[i], singleCommitDetail.root) to
                ReferenceOpenHashSet(singleCommitDetail.getChanges(i))
            }
          }
          else {
            emptyMap()
          }

          ChangesState.Changes(
            roots,
            singleCommitDetail.changes.toList(),
            changesToParents
          )
        }
      }
    }
  }

  private fun buildTreeModelSync(
    changes: List<Change>,
    changesToParents: Map<CommitId, Set<Change>>,
    affectedPaths: Collection<FilePath>?,
    showOnlyAffectedChanges: Boolean,
    showChangesFromParents: Boolean,
    grouping: ChangesGroupingPolicyFactory,
  ): TreeModelBuilder {
    val changes = collectAffectedChanges(
      changes,
      affectedPaths, showOnlyAffectedChanges
    )
    val changesToParents = changesToParents.mapValues {
      collectAffectedChanges(it.value, affectedPaths, showOnlyAffectedChanges)
    }

    val builder = TreeModelBuilder(logData.project, grouping)
    builder.setChanges(changes, null)

    if (showChangesFromParents && !changesToParents.isEmpty()) {
      if (changes.isEmpty()) {
        builder.createTagNode(VcsLogBundle.message("vcs.log.changes.no.merge.conflicts.node"))
      }
      for ((commitId, changesFromParent) in changesToParents) {
        if (changesFromParent.isEmpty()) continue

        val parentNode: ChangesBrowserNode<*> = TagChangesBrowserNode(ParentTag(commitId.hash, getText(this, commitId)),
                                                                      SimpleTextAttributes.REGULAR_ATTRIBUTES, false)
        parentNode.markAsHelperNode()
        builder.insertSubtreeRoot(parentNode)
        builder.insertChanges(changesFromParent, parentNode)
      }
    }
    return builder
  }

  fun interface Listener : EventListener {
    fun onStateChanged()
  }

  private sealed interface SelectionState {
    data class Details(val details: List<VcsFullCommitDetails>) : SelectionState
    data class ManyDetails(val details: List<VcsFullCommitDetails>, val maxDetailsSize: Int) : SelectionState
    data object Empty : SelectionState
    data object Error : SelectionState
  }

  sealed interface ChangesState {
    class Changes(
      val roots: Set<VirtualFile>,
      val changes: List<Change>,
      val changesToParents: Map<CommitId, Set<Change>>,
    ) : ChangesState

    class ManyChanges(
      val size: Int,
      val maxSize: Int,
      val showAnyway: () -> Unit,
    ) : ChangesState

    class Empty(
      val resetText: Boolean,
    ) : ChangesState

    data object Error : ChangesState
  }

  companion object {
    @JvmField
    val HAS_AFFECTED_FILES: DataKey<Boolean> = DataKey.create<Boolean>("VcsLogAsyncChangesTreeModel.HasAffectedFiles")

    private fun collectAffectedChanges(
      changes: Collection<Change>,
      affectedPaths: Collection<FilePath>?,
      showOnlyAffectedSelected: Boolean,
    ): List<Change> {
      return if (!showOnlyAffectedSelected || affectedPaths == null) ArrayList(changes)
      else changes.filter { change: Change ->
        affectedPaths.any { filePath: FilePath ->
          if (filePath.isDirectory) {
            return@any FileHistoryUtil.affectsDirectory(change, filePath)
          }
          else {
            return@any FileHistoryUtil.affectsFile(change, filePath, false) ||
                       FileHistoryUtil.affectsFile(change, filePath, true)
          }
        }
      }
    }

    fun createDiffRequestProducer(
      project: Project,
      change: Change,
      context: MutableMap<Key<*>, Any>,
    ): ChangeDiffRequestChain.Producer? =
      if (change is MergedChange && change.sourceChanges.size == 2)
        MergedChangeDiffRequestProvider.MyProducer(project, change, context)
      else
        ChangeDiffRequestProducer.create(project, change, context)
  }
}

internal class ParentTag(commit: Hash, private val text: @Nls String) : ValueTag<Hash>(commit) {
  override fun toString() = text
}
