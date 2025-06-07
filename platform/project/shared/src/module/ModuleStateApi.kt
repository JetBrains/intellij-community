// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project.module

import com.intellij.platform.project.ProjectId
import com.intellij.platform.rpc.RemoteApiProviderService
import fleet.rpc.RemoteApi
import fleet.rpc.Rpc
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.flow.Flow
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
@Rpc
interface ModuleStateApi : RemoteApi<Unit> {

  suspend fun getModulesUpdateEvents(projectId: ProjectId): Flow<ModuleUpdatedEvent>

  companion object {
    @JvmStatic
    suspend fun getInstance(): ModuleStateApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<ModuleStateApi>())
    }
  }
}

@Internal
@Serializable
sealed interface ModuleUpdatedEvent {

  @Internal
  @Serializable
  class ModulesAddedEvent(val moduleNames: List<String>) : ModuleUpdatedEvent

  @Internal
  @Serializable
  class ModuleRemovedEvent(val moduleName: String) : ModuleUpdatedEvent

  @Internal
  @Serializable
  class ModulesRenamedEvent(val newToOldModuleNameMap: Map<String, String>) : ModuleUpdatedEvent

}