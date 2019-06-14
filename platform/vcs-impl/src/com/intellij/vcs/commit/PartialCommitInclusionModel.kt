// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.vcs.commit

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.LocalChangeList
import com.intellij.openapi.vcs.changes.ui.BaseInclusionModel
import com.intellij.openapi.vcs.changes.ui.PartiallyExcludedFilesStateHolder
import com.intellij.openapi.vcs.ex.PartialLocalLineStatusTracker
import com.intellij.openapi.vcs.impl.PartialChangesUtil
import com.intellij.openapi.vcs.impl.PartialChangesUtil.convertExclusionState
import com.intellij.openapi.vcs.impl.PartialChangesUtil.getPartialTracker
import com.intellij.util.ui.ThreeStateCheckBox

class PartialCommitInclusionModel(
  private val project: Project,
  changeList: LocalChangeList
) : BaseInclusionModel(), Disposable {

  var changeList: LocalChangeList = changeList
    set(value) {
      field = value
      stateHolder.setChangelistId(value.id)
    }

  private val stateHolder = StateHolder()

  init {
    Disposer.register(this, stateHolder)
  }

  override fun getInclusion(): Set<Any> = stateHolder.getIncludedSet()
  override fun getInclusionState(item: Any): ThreeStateCheckBox.State = convertExclusionState(stateHolder.getExclusionState(item))
  override fun isInclusionEmpty(): Boolean = getInclusion().isEmpty()

  override fun addInclusion(items: Collection<Any>) = stateHolder.includeElements(items)
  override fun removeInclusion(items: Collection<Any>) = stateHolder.excludeElements(items)
  override fun setInclusion(items: Collection<Any>) = stateHolder.setIncludedElements(items)
  override fun retainInclusion(items: Collection<Any>) {
    val toRemove = getInclusion() - items
    if (toRemove.isNotEmpty()) removeInclusion(toRemove)
  }

  override fun clearInclusion() {
    if (getInclusion().isNotEmpty()) setInclusion(emptySet())
  }

  override fun dispose() = Unit

  private inner class StateHolder : PartiallyExcludedFilesStateHolder<Any>(project, changeList.id) {
    override val trackableElements: Sequence<Any> get() = changeList.changes.asSequence()

    override fun findElementFor(tracker: PartialLocalLineStatusTracker): Any? =
      changeList.changes.find { tracker.virtualFile == PartialChangesUtil.getVirtualFile(it) }

    override fun findTrackerFor(element: Any): PartialLocalLineStatusTracker? =
      (element as? Change)?.let { getPartialTracker(project, it) }

    override fun updateExclusionStates() {
      super.updateExclusionStates()
      fireInclusionChanged()
    }
  }
}