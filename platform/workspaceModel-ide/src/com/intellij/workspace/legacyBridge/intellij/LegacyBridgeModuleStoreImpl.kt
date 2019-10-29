package com.intellij.workspace.legacyBridge.intellij

import com.intellij.configurationStore.ModuleStateStorageManager
import com.intellij.configurationStore.ModuleStoreBase
import com.intellij.configurationStore.StateStorageManagerImpl
import com.intellij.configurationStore.TrackingPathMacroSubstitutorImpl
import com.intellij.openapi.components.PathMacroManager

class LegacyBridgeModuleStoreImpl(private val pathMacroManager: PathMacroManager) : ModuleStoreBase() {
  override val storageManager: StateStorageManagerImpl = LegacyBridgeModuleStateStorageManager(pathMacroManager)

  override fun getPathMacroManagerForDefaults() = pathMacroManager

  private class LegacyBridgeModuleStateStorageManager(pathMacroManager: PathMacroManager) : ModuleStateStorageManager(
    macroSubstitutor = TrackingPathMacroSubstitutorImpl(pathMacroManager),
    module = null
  )
}