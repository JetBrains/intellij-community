// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.impl

import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.impl.SeRemoteApi
import fleet.kernel.DurableRef
import fleet.util.openmap.SerializedValue
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeRemoteApiImpl: SeRemoteApi {
  override suspend fun itemSelected(projectId: ProjectId,
                                    sessionRef: DurableRef<SeSessionEntity>,
                                    itemData: SeItemData,
                                    modifiers: Int,
                                    searchText: String): Boolean {
    return SeBackendService.getInstance(projectId.findProject()).itemSelected(sessionRef, itemData, modifiers, searchText)
  }

  override suspend fun getItems(projectId: ProjectId,
                                sessionRef: DurableRef<SeSessionEntity>,
                                providerId: SeProviderId,
                                params: SeParams,
                                serializedDataContext: SerializedValue?): Flow<SeItemData> {
    return SeBackendService.getInstance(projectId.findProject()).getItems(sessionRef, providerId, params, serializedDataContext)
  }
}
