// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.project.module

import com.intellij.ide.rpc.RpcCallUtil
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import com.intellij.platform.project.projectId
import com.intellij.platform.util.coroutines.childScope
import fleet.util.logging.logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

private val LOG = logger<ModulesStateService>()

@Service(Service.Level.PROJECT)
class ModulesStateService private constructor(private val project: Project, val coroutineScope: CoroutineScope) {
  private val state: ModulesState = ModulesState()
  private var initializationCompleted: Boolean = false

  init {
    loadModuleNamesAndSubscribe()
  }

  fun getModuleNames(): Set<String> {
    if (!initializationCompleted) {
      LOG.warn("Access to module names before initialization. Trying to get module names from ModuleManager")
      return ModuleManager.getInstance(project).modules.map { it.name }.toSet()
    }
    return state.moduleNames
  }

  private fun loadModuleNamesAndSubscribe(): Job {
    return coroutineScope.childScope("ModulesStateService.loadModuleNamesAndSubscribe").launch {
      LOG.debug("Starting initial module names loading for project: ${project.name}")
      val moduleNames = RpcCallUtil.invokeSafely { ModuleStateApi.getInstance().getCurrentModuleNames(project.projectId()) }
      state.moduleNames.addAll(moduleNames.toMutableSet())
      LOG.debug("Completed loading initial module names. Found ${moduleNames.size} modules")
      initializationCompleted = true
      subscribeToModuleChanges()
    }
  }

  private suspend fun subscribeToModuleChanges() {
    LOG.debug("Starting subscription for module updates in project: ${project.name}")
    RpcCallUtil.invokeSafely { ModuleStateApi.getInstance().getModulesUpdateEvents(project.projectId()) }.collect { update ->
      LOG.debug("Received module update: $update")
      state.applyModuleChange(update)
    }
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): ModulesStateService {
      return project.service<ModulesStateService>()
    }
  }
}

private class ModulesState() {
  val moduleNames: MutableSet<String> = mutableSetOf()

  fun applyModuleChange(moduleUpdatedEvent: ModuleUpdatedEvent) {
    when (moduleUpdatedEvent.moduleUpdateType) {
      ModuleUpdateType.RENAME -> {
        moduleUpdatedEvent.newToOldModuleNameMap.forEach { (newName, oldName) ->
          moduleNames.remove(oldName)
          moduleNames.add(newName)
        }
      }
      ModuleUpdateType.ADD -> moduleNames.addAll(moduleUpdatedEvent.moduleNames)
      ModuleUpdateType.REMOVE -> moduleNames.removeAll(moduleUpdatedEvent.moduleNames)
    }
  }
}

private class ModuleStateInitializer : ProjectActivity {
  override suspend fun execute(project: Project) {
    ModulesStateService.getInstance(project)
  }
}