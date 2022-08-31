package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity

interface FacetBridge<T: WorkspaceEntity> {
  fun isNewModuleSettings(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity): Boolean
  fun getNewModuleSettingsName(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity): String
  fun addNewModuleSettings(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity, entitySource: EntitySource)
  fun removeModuleSettings(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity)
  fun renameModuleSettings(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity, newName: String)
  fun applyChangesToStorage(mutableStorage: MutableEntityStorage, module: ModuleBridge)
  fun updateFacetConfiguration(rootEntity: T)
  fun getRootEntity(): T
}