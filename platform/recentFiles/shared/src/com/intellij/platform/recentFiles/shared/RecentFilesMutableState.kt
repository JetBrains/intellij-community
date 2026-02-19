// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import org.jetbrains.annotations.ApiStatus

private val LOG by lazy { fileLogger() }

@ApiStatus.Internal
class RecentFilesState<T>(val entries: List<T> = listOf())

@ApiStatus.Internal
abstract class RecentFilesMutableState<T>(protected val project: Project) {
  protected val recentlyOpenedFilesState: MutableStateFlow<RecentFilesState<T>> = MutableStateFlow(RecentFilesState())
  protected val recentlyEditedFilesState: MutableStateFlow<RecentFilesState<T>> = MutableStateFlow(RecentFilesState())
  protected val recentlyOpenedPinnedFilesState: MutableStateFlow<RecentFilesState<T>> = MutableStateFlow(RecentFilesState())

  abstract fun checkValidity(model: T): Boolean

  fun chooseStateToWriteTo(targetFilesKind: RecentFileKind): MutableStateFlow<RecentFilesState<T>> {
    return when (targetFilesKind) {
      RecentFileKind.RECENTLY_EDITED -> recentlyEditedFilesState
      RecentFileKind.RECENTLY_OPENED -> recentlyOpenedFilesState
      RecentFileKind.RECENTLY_OPENED_UNPINNED -> recentlyOpenedPinnedFilesState
    }
  }

  fun addEvent(targetFilesKind: RecentFileKind, batch: List<T>) {
    val targetModel = chooseStateToWriteTo(targetFilesKind)
    val toAdd = batch
    LOG.debug("Adding ${batch.size} items to $targetFilesKind frontend model")
    targetModel.update { oldList ->
      val updatedState = toAdd + (oldList.entries - toAdd.toSet())
      RecentFilesState(updatedState.filter(::checkValidity))
    }
  }

  fun updateEvent(
    targetFilesKind: RecentFileKind, batch: List<T>, putOnTop: Boolean,
  ) {
    val itemsToMergeWithExisting = batch.associateBy { it }
    val targetModel = chooseStateToWriteTo(targetFilesKind)
    LOG.debug("Updating ${batch.size} items in $targetFilesKind frontend model")
    targetModel.update { oldList ->
      if (putOnTop) {
        val newValuesToPutIntoFirstPosition = oldList.entries.mapNotNull { oldItem -> itemsToMergeWithExisting[oldItem] }
        val restOfExistingValues = oldList.entries - newValuesToPutIntoFirstPosition.toSet()
        val updatedState = newValuesToPutIntoFirstPosition + restOfExistingValues
        RecentFilesState(updatedState.filter(::checkValidity))
      }
      else {
        val effectiveModelsToInsert = oldList.entries.map { oldItem -> itemsToMergeWithExisting[oldItem] ?: oldItem }
        RecentFilesState(effectiveModelsToInsert.filter(::checkValidity))
      }
    }
  }

  fun removeEvent(targetFilesKind: RecentFileKind, batch: List<T>) {
    val targetModel = chooseStateToWriteTo(targetFilesKind)
    LOG.debug("Removing ${batch.size} items from $targetFilesKind frontend model")
    targetModel.update { oldList ->
      val updatedState = oldList.entries - batch.toSet()
      RecentFilesState(updatedState.filter(::checkValidity))
    }
  }

  fun removeAllEvent(
    targetFilesKind: RecentFileKind,
  ) {
    val targetModel = chooseStateToWriteTo(targetFilesKind)
    LOG.debug("Removing all items from $targetFilesKind frontend model")
    targetModel.update { RecentFilesState(listOf()) }
  }
}
