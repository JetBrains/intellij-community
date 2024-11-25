// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rpc

import com.intellij.platform.project.ProjectEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import fleet.kernel.DurableRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import kotlinx.coroutines.Deferred
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus

@Rpc
@ApiStatus.Internal
interface RemoteShelfApi : RemoteApi<Unit> {
  suspend fun loadChangesAsync(projectRef: DurableRef<ProjectEntity>)
  suspend fun showDiffForChanges(projectRef: DurableRef<ProjectEntity>, changeListDto: ChangeListDto)
  suspend fun notifyNodeSelected(projectRef: DurableRef<ProjectEntity>, changeListDto: ChangeListDto, fromModelChange: Boolean)
  suspend fun applyTreeGrouping(projectRef: DurableRef<ProjectEntity>, groupingKeys: Set<String>): Deferred<UpdateStatus>
  suspend fun renameShelvedChangeList(projectRef: DurableRef<ProjectEntity>, changeList: DurableRef<ShelvedChangeListEntity>, newName: String)
}

@ApiStatus.Internal
@Serializable
class ChangeListDto(val changeList: DurableRef<ShelvedChangeListEntity>, val changes: List<DurableRef<ShelvedChangeEntity>>)

@ApiStatus.Internal
@Serializable
enum class UpdateStatus {
  OK,
  FAILED
}