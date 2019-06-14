// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.ex.ExclusionState
import com.intellij.openapi.vcs.ex.LineStatusTracker
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.LineStatusTrackerManager
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import gnu.trove.THashSet
import org.jetbrains.annotations.CalledInAwt
import java.util.*

abstract class PartiallyExcludedFilesStateHolder<T>(project: Project, private var myChangelistId: String) : Disposable {
  protected val myUpdateQueue =
    MergingUpdateQueue(PartiallyExcludedFilesStateHolder::class.java.name, 300, true, MergingUpdateQueue.ANY_COMPONENT, this)

  private val myIncludedElements = THashSet<T>()
  private val myTrackerExclusionStates = HashMap<T, ExclusionState>()

  init {
    MyTrackerManagerListener().install(project)
  }

  override fun dispose() = Unit

  protected abstract val trackableElements: Sequence<T>
  protected abstract fun findElementFor(tracker: PartialLocalLineStatusTracker): T?
  protected abstract fun findTrackerFor(element: T): PartialLocalLineStatusTracker?

  private val trackers
    get() = trackableElements.mapNotNull { element -> findTrackerFor(element)?.let { tracker -> element to tracker } }

  fun setChangelistId(changelistId: String) {
    myChangelistId = changelistId
    updateExclusionStates()
  }

  @CalledInAwt
  open fun updateExclusionStates() {
    myTrackerExclusionStates.clear()

    trackers.forEach { (element, tracker) ->
      val state = tracker.getExcludedFromCommitState(myChangelistId)
      if (state != ExclusionState.NO_CHANGES) myTrackerExclusionStates[element] = state
    }
  }

  fun getExclusionState(element: T): ExclusionState =
    myTrackerExclusionStates[element] ?: if (element in myIncludedElements) ExclusionState.ALL_INCLUDED else ExclusionState.ALL_EXCLUDED

  private fun scheduleExclusionStatesUpdate() {
    myUpdateQueue.queue(Update.create("updateExcludedFromCommit") { updateExclusionStates() })
  }

  private inner class MyTrackerListener : PartialLocalLineStatusTracker.ListenerAdapter() {
    override fun onExcludedFromCommitChange(tracker: PartialLocalLineStatusTracker) = scheduleExclusionStatesUpdate()
    override fun onChangeListMarkerChange(tracker: PartialLocalLineStatusTracker) = scheduleExclusionStatesUpdate()
  }

  private inner class MyTrackerManagerListener : LineStatusTrackerManager.ListenerAdapter() {
    private val trackerListener = MyTrackerListener()
    private val disposable get() = this@PartiallyExcludedFilesStateHolder

    @CalledInAwt
    fun install(project: Project) {
      with(LineStatusTrackerManager.getInstanceImpl(project)) {
        addTrackerListener(this@MyTrackerManagerListener, disposable)
        getTrackers().filterIsInstance<PartialLocalLineStatusTracker>().forEach { it.addListener(trackerListener, disposable) }
      }
    }

    override fun onTrackerAdded(tracker: LineStatusTracker<*>) {
      if (tracker !is PartialLocalLineStatusTracker) return

      findElementFor(tracker)?.let { element -> tracker.setExcludedFromCommit(element !in myIncludedElements) }
      tracker.addListener(trackerListener, disposable)
    }

    override fun onTrackerRemoved(tracker: LineStatusTracker<*>) {
      if (tracker !is PartialLocalLineStatusTracker) return

      findElementFor(tracker)?.let { element ->
        myTrackerExclusionStates -= element

        val exclusionState = tracker.getExcludedFromCommitState(myChangelistId)
        if (exclusionState != ExclusionState.NO_CHANGES) {
          if (exclusionState != ExclusionState.ALL_EXCLUDED) myIncludedElements += element else myIncludedElements -= element
        }

        scheduleExclusionStatesUpdate()
      }
    }
  }

  fun getIncludedSet(): Set<T> {
    val set = HashSet(myIncludedElements)
    myTrackerExclusionStates.forEach { (element, state) ->
      if (state == ExclusionState.ALL_EXCLUDED) set -= element else set += element
    }
    return set
  }

  fun setIncludedElements(elements: Collection<T>) {
    val set = HashSet(elements)
    trackers.forEach { (element, tracker) ->
      tracker.setExcludedFromCommit(element !in set)
    }

    myIncludedElements.clear()
    myIncludedElements += elements

    updateExclusionStates()
  }

  fun includeElements(elements: Collection<T>) {
    elements.forEach { findTrackerFor(it)?.setExcludedFromCommit(false) }
    myIncludedElements += elements

    updateExclusionStates()
  }

  fun excludeElements(elements: Collection<T>) {
    elements.forEach { findTrackerFor(it)?.setExcludedFromCommit(true) }
    myIncludedElements -= elements

    updateExclusionStates()
  }
}
