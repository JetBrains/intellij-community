// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.impl.shared.rpc

import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeEntity
import com.intellij.vcs.impl.shared.rhizome.ShelvedChangeListEntity
import com.jetbrains.rhizomedb.EID
import fleet.kernel.SharedRef
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import kotlinx.serialization.Serializable

@Rpc
interface RemoteShelfApi : RemoteApi<Unit> {
  suspend fun loadChangesAsync(projectId: EID)
  suspend fun showDiffForChanges(projectId: EID, changeListDto: ChangeListDto)
}

@Serializable
class ChangeListDto(val changeList: SharedRef<ShelvedChangeListEntity>, val changes: List<SharedRef<ShelvedChangeEntity>>)