package com.intellij.workspaceModel.ide.legacyBridge

import com.intellij.workspaceModel.storage.EntitySource
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.api.ModuleEntity

/**
 * Bridge interface for facet which uses custom module settings entity under the hood
 */
interface FacetBridge<T: WorkspaceEntity> {
  /**
   * Check if it's a newly created facet bridge, and it's root entity doesn't exist in main storage
   */
  fun isNew(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity): Boolean

  /**
   * Returns the new name associated with given facet bridge
   */
  fun getNewName(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity): String

  /**
   * Add root entity which [FacetBridge] uses under the hood, into the storage
   * @param mutableStorage for saving root entity and it's children in it
   * @param moduleEntity corresponds to this [FacetBridge]
   * @param entitySource which should be used for such entities
   */
  fun addToStorage(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity, entitySource: EntitySource)

  /**
   * Removes all associated with this bridge entities from the storage
   */
  fun removeFromStorage(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity)

  /**
   * Rename entity associated with this bridge
   */
  fun rename(mutableStorage: MutableEntityStorage, moduleEntity: ModuleEntity, newName: String)

  /**
   * Apply changes from the entity which used under the hood into the builder passed as a parameter
   */
  fun applyChangesToStorage(mutableStorage: MutableEntityStorage, module: ModuleBridge)

  /**
   * Update facet configuration base on the data from the related entity
   */
  fun updateFacetConfiguration(rootEntity: T)

  /**
   * Method returns the entity which is used under the hood of this [FacetBridge]
   */
  fun getRootEntity(): T
}