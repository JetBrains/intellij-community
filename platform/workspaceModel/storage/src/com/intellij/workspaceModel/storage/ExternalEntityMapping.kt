// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

/**
 * Provides a way to associate [WorkspaceEntity] with external data. The association survives when a entity is modified. Use
 * [MutableEntityStorage.getMutableExternalMapping] to fill the index and [EntityStorage.getExternalMapping] to access it.
 */
interface ExternalEntityMapping<T> {
  fun getEntities(data: T): List<WorkspaceEntity>
  fun getFirstEntity(data: T): WorkspaceEntity?
  fun getDataByEntity(entity: WorkspaceEntity): T?
  fun forEach(action: (key: WorkspaceEntity, value: T) -> Unit)
  fun size(): Int
}

interface MutableExternalEntityMapping<T> : ExternalEntityMapping<T> {
  fun addMapping(entity: WorkspaceEntity, data: T)
  fun addIfAbsent(entity: WorkspaceEntity, data: T): Boolean
  fun getOrPutDataByEntity(entity: WorkspaceEntity, defaultValue: () -> T): T
  fun removeMapping(entity: WorkspaceEntity): T?
}
