// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.ExclusionState
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import it.unimi.dsi.fastutil.Hash
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenCustomHashMap
import it.unimi.dsi.fastutil.objects.ObjectOpenCustomHashSet

abstract class PartiallyExcludedFilesStateHolder<T>(
  project: Project,
  private val hashingStrategy: Hash.Strategy<T>? = null
) : Disposable {
  private fun PartialLocalLineStatusTracker.setExcludedFromCommit(element: T, isExcluded: Boolean) =
    getChangeListId(element)?.let { setExcludedFromCommit(it, isExcluded) }

  private fun PartialLocalLineStatusTracker.getExcludedFromCommitState(element: T): ExclusionState =
    getChangeListId(element)?.let { getExcludedFromCommitState(it) } ?: ExclusionState.NO_CHANGES

  protected val myUpdateQueue =
    MergingUpdateQueue(PartiallyExcludedFilesStateHolder::class.java.name, 300, true, MergingUpdateQueue.ANY_COMPONENT, this)

  private val myIncludedElements: MutableSet<T> = if (hashingStrategy == null) HashSet() else ObjectOpenCustomHashSet(hashingStrategy)
  private val myTrackerExclusionStates: MutableMap<T, ExclusionState> = if (hashingStrategy == null) HashMap() else Object2ObjectOpenCustomHashMap<T, ExclusionState>(hashingStrategy)

  init {
    MyTrackerManagerListener().install(project)
  }

  override fun dispose() = Unit

  protected abstract val trackableElements: Sequence<T>
  protected abstract fun getChangeListId(element: T): String?
  protected abstract fun findElementFor(tracker: PartialLocalLineStatusTracker, changeListId: String): T?
  protected abstract fun findTrackerFor(element: T): PartialLocalLineStatusTracker?

  private val trackers
    get() = trackableElements.mapNotNull { element -> findTrackerFor(element)?.let { tracker -> element to tracker } }

  @RequiresEdt
  open fun updateExclusionStates() {
    myTrackerExclusionStates.clear()

    trackers.forEach { (element, tracker) ->
      val state = tracker.getExcludedFromCommitState(element)
      if (state != ExclusionState.NO_CHANGES) myTrackerExclusionStates[element] = state
    }
  }

  fun getExclusionState(element: T): ExclusionState =
    myTrackerExclusionStates[element] ?: if (element in myIncludedElements) ExclusionState.ALL_INCLUDED else ExclusionState.ALL_EXCLUDED

  private fun scheduleExclusionStatesUpdate() {
    myUpdateQueue.queue(DisposableUpdate.createDisposable(myUpdateQueue, "updateExcludedFromCommit") { updateExclusionStates() })
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

    override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
      if (tracker !is PartialLocalLineStatusTracker) return

      tracker.getAffectedChangeListsIds().forEach { changeListId ->
        findElementFor(tracker, changeListId)?.let { element -> tracker.setExcludedFromCommit(element, element !in myIncludedElements) }
      }
      tracker.addListener(trackerListener, disposable)
    }

    override fun onTrackerRemoved(tracker: LineStatusTracker<*>) {
      if (tracker !is PartialLocalLineStatusTracker) return

      tracker.getAffectedChangeListsIds().forEach { changeListId ->
        val element = findElementFor(tracker, changeListId) ?: return@forEach

        myTrackerExclusionStates -= element
        val exclusionState = tracker.getExcludedFromCommitState(element)
        if (exclusionState != ExclusionState.NO_CHANGES) {
          if (exclusionState != ExclusionState.ALL_EXCLUDED) myIncludedElements += element else myIncludedElements -= element
        }

        scheduleExclusionStatesUpdate()
      }
    }
  }

  fun getIncludedSet(): Set<T> {
    val set: MutableSet<T> = if (hashingStrategy == null) HashSet(myIncludedElements) else ObjectOpenCustomHashSet(myIncludedElements, hashingStrategy)
    myTrackerExclusionStates.forEach { (element, state) ->
      if (state == ExclusionState.ALL_EXCLUDED) set -= element else set += element
    }
    return set
  }

  fun setIncludedElements(elements: Collection<T>) {
    val set: MutableSet<T> = if (hashingStrategy == null) HashSet(elements) else ObjectOpenCustomHashSet(elements, hashingStrategy)
    trackers.forEach { (element, tracker) ->
      tracker.setExcludedFromCommit(element, element !in set)
    }

    myIncludedElements.clear()
    myIncludedElements += elements

    updateExclusionStates()
  }

  fun includeElements(elements: Collection<T>) {
    elements.forEach { findTrackerFor(it)?.setExcludedFromCommit(it, false) }
    myIncludedElements += elements

    updateExclusionStates()
  }

  fun excludeElements(elements: Collection<T>) {
    elements.forEach { findTrackerFor(it)?.setExcludedFromCommit(it, true) }
    myIncludedElements -= elements

    updateExclusionStates()
  }
}
