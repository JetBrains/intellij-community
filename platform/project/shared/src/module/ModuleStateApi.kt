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

  suspend fun getCurrentModuleNames(projectId: ProjectId): List<String>

  companion object {
    @JvmStatic
    suspend fun getInstance(): ModuleStateApi {
      return RemoteApiProviderService.resolve(remoteApiDescriptor<ModuleStateApi>())
    }
  }
}

@Internal
enum class ModuleUpdateType {
  ADD, REMOVE, RENAME
}

@Internal
@Serializable
class ModuleUpdatedEvent(val moduleUpdateType: ModuleUpdateType, val newToOldModuleNameMap: Map<String, String>){// val moduleName: String, val newModuleName: String = moduleName) {
  constructor(moduleUpdateType: ModuleUpdateType, moduleNames: List<String>) : this(moduleUpdateType, moduleNames.associateWith { it })
  constructor(moduleUpdateType: ModuleUpdateType, moduleName: String) : this(moduleUpdateType, mapOf(moduleName to moduleName))

  val moduleNames: Set<String> = newToOldModuleNameMap.keys
}
