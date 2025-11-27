// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.ExclusionState
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.HashingStrategy
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

@ApiStatus.Internal
abstract class PartiallyExcludedFilesStateHolder<T>(
  project: Project,
  private val hashingStrategy: HashingStrategy<T>
) : Disposable {
  private fun PartialLocalLineStatusTracker.setExcludedFromCommit(element: T, isExcluded: Boolean) {
    getChangeListId(element)?.let { setExcludedFromCommit(it, isExcluded) }
  }

  private fun PartialLocalLineStatusTracker.getExcludedFromCommitState(element: T): ExclusionState {
    return getChangeListId(element)?.let { getExcludedFromCommitState(it) } ?: ExclusionState.NO_CHANGES
  }

  protected val updateQueue =
    MergingUpdateQueue(PartiallyExcludedFilesStateHolder::class.java.name, 300, true, MergingUpdateQueue.ANY_COMPONENT, this)

  private val lock = ReentrantReadWriteLock()
  private val includedElements = createElementsSet()
  private val trackerExclusionStates = CollectionFactory.createCustomHashingStrategyMap<T, ExclusionState>(hashingStrategy)

  init {
    MyTrackerManagerListener().install(project)
  }

  private fun createElementsSet(elements: Collection<T> = emptyList()): MutableSet<T> {
    return CollectionFactory.createCustomHashingStrategySet(hashingStrategy).also { it.addAll(elements) }
  }

  override fun dispose() {
  }

  protected abstract val trackableElements: Sequence<T>
  protected abstract fun getChangeListId(element: T): String?
  protected abstract fun findElementFor(tracker: PartialLocalLineStatusTracker, changeListId: String): T?
  protected abstract fun findTrackerFor(element: T): PartialLocalLineStatusTracker?
  protected abstract fun fireInclusionChanged()

  private val trackers: Sequence<Pair<T, PartialLocalLineStatusTracker>>
    get() = trackableElements.mapNotNull { element -> findTrackerFor(element)?.let { tracker -> element to tracker } }

  private fun scheduleExclusionStatesUpdate() {
    updateQueue.queue(DisposableUpdate.createDisposable(updateQueue, "updateExcludedFromCommit") { updateExclusionStates() })
  }

  private inner class MyTrackerListener : PartialLocalLineStatusTracker.ListenerAdapter() {
    override fun onExcludedFromCommitChange(tracker: PartialLocalLineStatusTracker) = scheduleExclusionStatesUpdate()
    override fun onChangeListMarkerChange(tracker: PartialLocalLineStatusTracker) = scheduleExclusionStatesUpdate()
  }

  private inner class MyTrackerManagerListener : LineStatusTrackerManager.ListenerAdapter() {
    private val trackerListener = MyTrackerListener()
    private val disposable get() = this@PartiallyExcludedFilesStateHolder

    @RequiresEdt
    fun install(project: Project) {
      with(LineStatusTrackerManager.getInstanceImpl(project)) {
        addTrackerListener(this@MyTrackerManagerListener, disposable)
        getTrackers().filterIsInstance<PartialLocalLineStatusTracker>().forEach { it.addListener(trackerListener, disposable) }
      }
    }

    @RequiresEdt
    override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
      if (tracker !is PartialLocalLineStatusTracker) return

      tracker.getAffectedChangeListsIds().forEach { changeListId ->
        findElementFor(tracker, changeListId)?.let { element -> tracker.setExcludedFromCommit(element, element !in includedElements) }
      }
      tracker.addListener(trackerListener, disposable)
    }

    @RequiresEdt
    override fun onTrackerRemoved(tracker: LineStatusTracker<*>) {
      if (tracker !is PartialLocalLineStatusTracker) return

      for (changeListId in tracker.getAffectedChangeListsIds()) {
        val element = findElementFor(tracker, changeListId) ?: continue

        val exclusionState = tracker.getExcludedFromCommitState(element)

        lock.write {
          trackerExclusionStates -= element
          if (exclusionState != ExclusionState.NO_CHANGES) {
            if (exclusionState != ExclusionState.ALL_EXCLUDED) {
              includedElements += element
            }
            else {
              includedElements -= element
            }
          }
        }
      }

      scheduleExclusionStatesUpdate()
    }
  }

  fun getIncludedSet(): Set<T> {
    lock.read {
      val set: MutableSet<T> = createElementsSet(includedElements)
      trackerExclusionStates.forEach { (element, state) ->
        if (state == ExclusionState.ALL_EXCLUDED) set -= element else set += element
      }
      return set
    }
  }

  fun getExclusionState(element: T): ExclusionState {
    lock.read {
      val trackerState = trackerExclusionStates[element]
      if (trackerState != null) return trackerState
      val isIncluded = element in includedElements
      return if (isIncluded) ExclusionState.ALL_INCLUDED else ExclusionState.ALL_EXCLUDED
    }
  }

  @RequiresEdt
  fun updateExclusionStates() {
    val newTrackerStates = computeTrackerExclusionStates()

    lock.write {
      trackerExclusionStates.clear()
      trackerExclusionStates.putAll(newTrackerStates)
    }

    fireInclusionChanged()
  }

  /**
   * Must be called outside [lock] to prevent deadlocks through [LineStatusTrackerManager.LOCK]
   */
  @RequiresEdt
  private fun computeTrackerExclusionStates(): Map<T, ExclusionState> {
    val result = CollectionFactory.createCustomHashingStrategyMap<T, ExclusionState>(hashingStrategy)
    trackers.forEach { (element, tracker) ->
      val state = tracker.getExcludedFromCommitState(element)
      if (state != ExclusionState.NO_CHANGES) result[element] = state
    }
    return result
  }

  @RequiresEdt
  fun setIncludedElements(elements: Collection<T>) {
    val set: MutableSet<T> = createElementsSet(elements)
    trackers.forEach { (element, tracker) ->
      tracker.setExcludedFromCommit(element, element !in set)
    }

    val newTrackerStates = computeTrackerExclusionStates()

    lock.write {
      includedElements.clear()
      includedElements += elements
      trackerExclusionStates.clear()
      trackerExclusionStates.putAll(newTrackerStates)
    }

    fireInclusionChanged()
  }

  @RequiresEdt
  fun includeElements(elements: Collection<T>) {
    elements.forEach { findTrackerFor(it)?.setExcludedFromCommit(it, false) }

    val newTrackerStates = computeTrackerExclusionStates()

    lock.write {
      includedElements += elements
      trackerExclusionStates.clear()
      trackerExclusionStates.putAll(newTrackerStates)
    }

    fireInclusionChanged()
  }

  @RequiresEdt
  fun excludeElements(elements: Collection<T>) {
    for (element in elements) {
      findTrackerFor(element)?.setExcludedFromCommit(element, true)
    }

    val newTrackerStates = computeTrackerExclusionStates()

    lock.write {
      for (element in elements) {
        includedElements.remove(element)
      }
      trackerExclusionStates.clear()
      trackerExclusionStates.putAll(newTrackerStates)
    }

    fireInclusionChanged()
  }

  @RequiresEdt
  fun retainElements(elements: Collection<T>) {
    val toRetain = createElementsSet(elements)

    trackers.forEach { (element, tracker) ->
      if (!toRetain.contains(element)) {
        tracker.setExcludedFromCommit(element, true)
      }
    }

    val newTrackerStates = computeTrackerExclusionStates()

    lock.write {
      includedElements.retainAll(toRetain)
      trackerExclusionStates.clear()
      trackerExclusionStates.putAll(newTrackerStates)
    }

    fireInclusionChanged()
  }
}
