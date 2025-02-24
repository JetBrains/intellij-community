// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.recentFiles.shared

import com.intellij.ide.ui.colors.ColorId
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.vfs.VirtualFileId
import com.intellij.openapi.util.NlsSafe
import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Rpc
interface FileSwitcherApi : RemoteApi<Unit> {
  suspend fun getRecentFileEvents(projectId: ProjectId): Flow<RecentFilesEvent>
  suspend fun updateRecentFilesBackendState(request: RecentFilesBackendRequest): Boolean

  companion object {
    @JvmStatic
    suspend fun getInstance(): FileSwitcherApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<FileSwitcherApi>())
    }
  }
}

@ApiStatus.Internal
@Serializable
sealed interface RecentFilesBackendRequest {
  val projectId: ProjectId

  @Serializable
  data class NewSearchWithParameters(val onlyEdited: Boolean, val pinned: Boolean, val frontendEditorSelectionHistory: List<VirtualFileId>, override val projectId: ProjectId) : RecentFilesBackendRequest

  @Serializable
  data class HideFiles(val filesToHide: List<SwitcherRpcDto.File>, override val projectId: ProjectId) : RecentFilesBackendRequest

  @Serializable
  data class ScheduleRehighlighting(override val projectId: ProjectId) : RecentFilesBackendRequest
}


@ApiStatus.Internal
@Serializable
sealed interface SwitcherRpcDto {

  @Serializable
  data class File(
    val mainText: @NlsSafe String,
    val statusText: @NlsSafe String,
    val pathText: @NlsSafe String,
    val hasProblems: Boolean,
    val iconId: IconId,
    val foregroundTextColorId: ColorId?,
    val backgroundColorId: ColorId?,
    val virtualFileId: VirtualFileId,
  ) : SwitcherRpcDto
}

@ApiStatus.Internal
@Serializable
sealed interface RecentFilesEvent {
  @Serializable
  class ItemsAdded(val batch: List<SwitcherRpcDto>) : RecentFilesEvent

  @Serializable
  class ItemsRemoved(val batch: List<SwitcherRpcDto>) : RecentFilesEvent

  @Serializable
  class AllItemsRemoved : RecentFilesEvent

  @Serializable
  class EndOfUpdates : RecentFilesEvent
}