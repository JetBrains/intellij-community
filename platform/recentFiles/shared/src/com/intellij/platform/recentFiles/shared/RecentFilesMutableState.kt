// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.diagnostic.fileLogger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
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

  abstract fun convertDtoToModel(rpcDto: SwitcherRpcDto): T?
  abstract suspend fun convertVirtualFileIdToModel(virtualFileId: VirtualFileId): T?
  abstract fun convertModelToVirtualFile(model: T): VirtualFile?
  abstract fun checkValidity(model: T): Boolean

  fun chooseStateToWriteTo(filesKind: RecentFileKind): MutableStateFlow<RecentFilesState<T>> {
    return when (filesKind) {
      RecentFileKind.RECENTLY_EDITED -> recentlyEditedFilesState
      RecentFileKind.RECENTLY_OPENED -> recentlyOpenedFilesState
      RecentFileKind.RECENTLY_OPENED_UNPINNED -> recentlyOpenedPinnedFilesState
    }
  }

  suspend fun applyChangesToModel(change: RecentFilesEvent, targetFilesKind: RecentFileKind) {
    val targetModel = chooseStateToWriteTo(targetFilesKind)
    when (change) {
      is RecentFilesEvent.ItemsAdded -> {
        val toAdd = change.batch.mapNotNull(::convertDtoToModel)
        LOG.debug("Adding ${change.batch.size} items to $targetFilesKind frontend model")
        targetModel.update { oldList ->
          val updatedState = toAdd + (oldList.entries - toAdd.toSet())
          RecentFilesState(updatedState.filter(::checkValidity))
        }
      }
      is RecentFilesEvent.ItemsUpdated -> {
        val itemsToMergeWithExisting = change.batch.map(::convertDtoToModel).associateBy { it }
        LOG.debug("Updating ${change.batch.size} items in $targetFilesKind frontend model")
        targetModel.update { oldList ->
          if (change.putOnTop) {
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
      is RecentFilesEvent.ItemsRemoved -> {
        LOG.debug("Removing ${change.batch.size} items from $targetFilesKind frontend model")
        val toRemove = change.batch.mapNotNull { virtualFileId -> convertVirtualFileIdToModel(virtualFileId) }
        targetModel.update { oldList ->
          val updatedState = oldList.entries - toRemove.toSet()
          RecentFilesState(updatedState.filter(::checkValidity))
        }
      }
      is RecentFilesEvent.AllItemsRemoved -> {
        LOG.debug("Removing all items from $targetFilesKind frontend model")
        targetModel.update { RecentFilesState(listOf()) }
      }
    }
  }
}