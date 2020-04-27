package com.intellij.workspace.legacyBridge.intellij

import com.intellij.configurationStore.*
import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.workspace.jps.JpsProjectModelSynchronizer

internal class LegacyBridgeModuleStoreImpl(module: Module) : ModuleStoreBase() {
  private val enabled: Boolean

  init {
    val moduleManager = LegacyBridgeModuleManagerComponent.getInstance(module.project)
    enabled = JpsProjectModelSynchronizer.enabled && !module.moduleFilePath.startsWith(moduleManager.outOfTreeModulesPath)
  }

  private val pathMacroManager = PathMacroManager.getInstance(module)

  override val storageManager: StateStorageManagerImpl =
    if (enabled)
      LegacyBridgeModuleStateStorageManager(pathMacroManager, module)
    else
      DummyModuleStateStorageManager()

  override fun getPathMacroManagerForDefaults() = pathMacroManager

  override val loadPolicy: StateLoadPolicy
    get() = if (enabled) StateLoadPolicy.LOAD else StateLoadPolicy.NOT_LOAD

  override fun <T> getStorageSpecs(component: PersistentStateComponent<T>,
                                   stateSpec: State,
                                   operation: StateStorageOperation): List<Storage> {
    if (!enabled) return emptyList()
    return super.getStorageSpecs(component, stateSpec, operation)
  }

  private class DummyModuleStateStorageManager: StateStorageManagerImpl(
    rootTagName = "module",
    componentManager = null,
    macroSubstitutor = null,
    virtualFileTracker = null
  )

  private class LegacyBridgeModuleStateStorageManager(pathMacroManager: PathMacroManager, module: Module) : ModuleStateStorageManager(
    TrackingPathMacroSubstitutorImpl(pathMacroManager), module)
}