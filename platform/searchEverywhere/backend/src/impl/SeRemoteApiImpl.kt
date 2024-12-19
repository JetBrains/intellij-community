// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import com.jetbrains.rhizomedb.EID
import fleet.kernel.DurableRef
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeRemoteApiImpl: SeRemoteApi {
  override suspend fun itemSelected(projectId: ProjectId, itemId: EID) {
    SeBackendService.getInstance(projectId.findProject()).itemSelected(itemId)
  }

  override suspend fun getItems(projectId: ProjectId,
                                sessionRef: DurableRef<SeSessionEntity>,
                                providerId: SeProviderId,
                                params: SeParams): Flow<SeItemData> {
    return SeBackendService.getInstance(projectId.findProject()).getItems(sessionRef, providerId, params)
  }
}
