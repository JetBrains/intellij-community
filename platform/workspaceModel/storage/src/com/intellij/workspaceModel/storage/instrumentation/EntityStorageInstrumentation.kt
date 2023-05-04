// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.instrumentation

import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.EntityStorageSnapshot
import com.intellij.workspaceModel.storage.MutableEntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.impl.EntityId

interface EntityStorageInstrumentation : EntityStorage {
  /**
   * Create entity using [newInstance] function.
   * In some implementations of the storage ([EntityStorageSnapshot]), the entity is cached and the new instance is created only once.
   */
  fun <T: WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: (() -> T)): T
}
interface EntityStorageSnapshotInstrumentation : EntityStorageSnapshot, EntityStorageInstrumentation
interface MutableEntityStorageInstrumentation : MutableEntityStorage, EntityStorageInstrumentation
