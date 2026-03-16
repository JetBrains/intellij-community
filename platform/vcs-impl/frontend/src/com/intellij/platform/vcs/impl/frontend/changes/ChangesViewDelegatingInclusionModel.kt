// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.vcs.impl.frontend.changes

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.trace
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ChangeListChange
import com.intellij.openapi.vcs.changes.ui.BaseInclusionModel
import com.intellij.openapi.vcs.ex.LocalRange
import com.intellij.openapi.vcs.ex.changesInChangeList
import com.intellij.openapi.vcs.ex.countAllChanges
import com.intellij.openapi.vcs.ex.countIncludedChanges
import com.intellij.platform.project.projectId
import com.intellij.platform.vcs.changes.ChangesUtil
import com.intellij.platform.vcs.impl.shared.changes.ChangeListsViewModel
import com.intellij.platform.vcs.impl.shared.changes.PartialChangesHolder
import com.intellij.platform.vcs.impl.shared.rpc.BackendChangesViewEvent
import com.intellij.platform.vcs.impl.shared.rpc.ChangesViewInclusionModelApi
import com.intellij.platform.vcs.impl.shared.rpc.InclusionDto
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.ui.ThreeStateCheckBox
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

private typealias RequestHandler = suspend () -> Unit

/**
 * Changes to [ChangesViewDelegatingInclusionModel] are propagated to the backend via [ChangesViewInclusionModelApi].
 * Once the backend state is applied, the new state is reported to the frontend as [BackendChangesViewEvent.InclusionChanged]
 * and set via [applyBackendState].
 */
internal class ChangesViewDelegatingInclusionModel(
  private val project: Project,
  cs: CoroutineScope,
) : BaseInclusionModel() {
  private val updateRequests =
    MutableSharedFlow<RequestHandler>(extraBufferCapacity = 64, onBufferOverflow = BufferOverflow.DROP_OLDEST)

  init {
    updateRequests.onEach { it.invoke() }.launchIn(cs)
  }

  /**
   * Note that equals and hashCode of [ChangeListChange] are seemingly broken.
   * For this reason [ChangeListChange.HASHING_STRATEGY] must be used
   */
  private var inclusionState: MutableSet<Any> = mutableSetOfChangeListChanges()

  private val changeListsViewModel = ChangeListsViewModel.getInstance(project)

  override fun getInclusion(): Set<Any> = inclusionState

  override fun getInclusionState(item: Any): ThreeStateCheckBox.State {
    if (item is ChangeListChange) {
      val ranges = PartialChangesHolder.getInstance(project).getRanges(ChangesUtil.getFilePath(item.change))
      if (ranges != null) {
        return getRangesBasedInclusionState(ranges, item)
      }
    }

    return if (item in inclusionState) ThreeStateCheckBox.State.SELECTED else ThreeStateCheckBox.State.NOT_SELECTED
  }

  private fun getRangesBasedInclusionState(ranges: List<LocalRange>, item: ChangeListChange): ThreeStateCheckBox.State {
    val changesInChangeList = ranges.changesInChangeList(item.changeListId)
    val included = changesInChangeList.countIncludedChanges()
    val total = changesInChangeList.countAllChanges()
    return when (included) {
      0 -> ThreeStateCheckBox.State.NOT_SELECTED
      total -> ThreeStateCheckBox.State.SELECTED
      else -> ThreeStateCheckBox.State.DONT_CARE
    }
  }

  override fun isInclusionEmpty(): Boolean = inclusionState.isEmpty()

  override fun addInclusion(items: Collection<Any>) {
    LOG.trace { "Adding ${items.size} items to inclusion" }
    inclusionState.addAll(items)
    fireInclusionChanged()
    updateRequests.tryEmit {
      ChangesViewInclusionModelApi.getInstance().add(project.projectId(), items.toDto())
    }
  }

  override fun removeInclusion(items: Collection<Any>) {
    LOG.trace { "Removing ${items.size} items from inclusion" }
    inclusionState.removeAll(items.toSet())
    fireInclusionChanged()
    updateRequests.tryEmit {
      ChangesViewInclusionModelApi.getInstance().remove(project.projectId(), items.toDto())
    }
  }

  override fun setInclusion(items: Collection<Any>) {
    LOG.trace { "Setting inclusion to ${items.size} items" }
    inclusionState = mutableSetOfChangeListChanges().also { it.addAll(items) }
    fireInclusionChanged()
    updateRequests.tryEmit {
      ChangesViewInclusionModelApi.getInstance().set(project.projectId(), items.toDto())
    }
  }

  override fun retainInclusion(items: Collection<Any>) {
    LOG.trace { "Retaining ${items.size} items in inclusion" }
    inclusionState.retainAll(items.toSet())
    fireInclusionChanged()
    updateRequests.tryEmit {
      ChangesViewInclusionModelApi.getInstance().retain(project.projectId(), items.toDto())
    }
  }

  override fun clearInclusion() {
    LOG.trace { "Clearing inclusion" }
    if (inclusionState.isNotEmpty()) {
      inclusionState.clear()
      fireInclusionChanged()
      updateRequests.tryEmit {
        ChangesViewInclusionModelApi.getInstance().clear(project.projectId())
      }
    }
  }

  suspend fun applyBackendState(items: Collection<InclusionDto>) {
    LOG.trace { "Applying backend inclusion state: received ${items.size} items, previous size ${inclusionState.size}" }
    inclusionState = items.fromDto()
    fireInclusionChanged()
    ChangesViewInclusionModelApi.getInstance().notifyInclusionUpdateApplied(project.projectId())
  }

  private fun Collection<Any>.toDto(): List<InclusionDto> = map(InclusionDto::toDto)

  private fun Collection<InclusionDto>.fromDto(): MutableSet<Any> =
    mapNotNullTo(mutableSetOfChangeListChanges()) { dto ->
      when (dto) {
        is InclusionDto.Change -> changeListsViewModel.resolveChange(dto.changeId).also { change ->
          if (change == null) {
            LOG.warn("Change ${dto.changeId} not found")
          }
        }
        is InclusionDto.File -> dto.path.filePath
      }
    }

  private fun mutableSetOfChangeListChanges(): MutableSet<Any> =
    CollectionFactory.createCustomHashingStrategySet(ChangeListChange.HASHING_STRATEGY)

  companion object {
    private val LOG = logger<ChangesViewDelegatingInclusionModel>()
  }
}
