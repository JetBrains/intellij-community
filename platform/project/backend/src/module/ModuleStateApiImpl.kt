// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project.backend.module

import com.intellij.openapi.module.Module
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.ModuleListener
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProjectOrNull
import com.intellij.platform.project.module.ModuleStateApi
import com.intellij.platform.project.module.ModuleUpdateType
import com.intellij.platform.project.module.ModuleUpdatedEvent
import com.intellij.platform.project.module.ModulesStateService
import com.intellij.platform.rpc.backend.RemoteApiProvider
import com.intellij.util.Function
import fleet.rpc.remoteApiDescriptor
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.launch

internal class ModuleStateApiImpl : ModuleStateApi {
  @OptIn(ExperimentalCoroutinesApi::class)
  override suspend fun getModulesUpdateEvents(projectId: ProjectId): Flow<ModuleUpdatedEvent> {
    val project = projectId.findProjectOrNull() ?: return emptyFlow()
    val connection = project.messageBus.simpleConnect()
    val flow = channelFlow {
      val coroutineScope = ModulesStateService.getInstance(project).coroutineScope
      connection.subscribe(ModuleListener.TOPIC, object : ModuleListener {
        override fun modulesAdded(project: Project, modules: List<Module>) {
          coroutineScope.launch {
            send(ModuleUpdatedEvent(ModuleUpdateType.ADD, modules.map { it.name}))
          }
        }

        override fun moduleRemoved(project: Project, module: Module) {
          coroutineScope.launch {
            send(ModuleUpdatedEvent(ModuleUpdateType.REMOVE, module.name))
          }
        }

        override fun modulesRenamed(project: Project, modules: List<Module>, oldNameProvider: Function<in Module, String>) {
          coroutineScope.launch {
            send(ModuleUpdatedEvent(ModuleUpdateType.RENAME, modules.associate { module ->
             module.name to oldNameProvider.`fun`(module)
            }))
          }
        }
      })

      awaitClose { connection.disconnect() }
    }
    return flow
  }

  override suspend fun getCurrentModuleNames(projectId: ProjectId): List<String> {
    val project = projectId.findProjectOrNull() ?: return emptyList()
    return ModuleManager.getInstance(project).modules.map { it.name }
  }
}


private class ModuleStateApiProvider : RemoteApiProvider {
  override fun RemoteApiProvider.Sink.remoteApis() {
    remoteApi(remoteApiDescriptor<ModuleStateApi>()) {
      ModuleStateApiImpl()
    }
  }
}