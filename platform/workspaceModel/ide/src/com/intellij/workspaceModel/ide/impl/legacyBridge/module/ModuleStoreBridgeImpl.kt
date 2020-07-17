package com.intellij.workspaceModel.ide.impl.legacyBridge.module

import com.intellij.configurationStore.*
import com.intellij.openapi.components.*
import com.intellij.openapi.module.Module
import com.intellij.util.io.systemIndependentPath
import com.intellij.workspaceModel.ide.impl.jps.serialization.JpsProjectModelSynchronizer

internal class ModuleStoreBridgeImpl(module: Module) : ModuleStoreBase() {
  private val enabled: Boolean

  init {
    val moduleManager = ModuleManagerComponentBridge.getInstance(module.project)
    enabled = JpsProjectModelSynchronizer.enabled && module is ModuleBridgeImpl
              && module.originalDirectoryPath?.systemIndependentPath != moduleManager.outOfTreeModulesPath
  }

  private val pathMacroManager = PathMacroManager.getInstance(module)

  override val storageManager: StateStorageManagerImpl =
    if (enabled)
      ModuleStateStorageManagerBridge(pathMacroManager, module)
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

  private class ModuleStateStorageManagerBridge(pathMacroManager: PathMacroManager, module: Module) : ModuleStateStorageManager(
    TrackingPathMacroSubstitutorImpl(pathMacroManager), module)
}