// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

/**
 * Provides a way to associate [WorkspaceEntity] with external data. The association survives when a entity is modified. Use
 * [WorkspaceEntityStorageDiffBuilder.getMutableExternalIndex] to fill the index and [WorkspaceEntityStorage.getExternalIndex] to access it.
 */
interface ExternalEntityIndex<T> {
  fun getEntities(data: T): List<WorkspaceEntity>
  fun getDataByEntity(entity: WorkspaceEntity): T?
}

interface MutableExternalEntityIndex<T> : ExternalEntityIndex<T> {
  fun index(entity: WorkspaceEntity, data: T)
  fun remove(entity: WorkspaceEntity)
}