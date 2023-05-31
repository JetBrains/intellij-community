// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.instrumentation

import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.impl.EntityId

/**
 * Instrumentation level of the storage.
 *
 * This level of function contains the functionality that should be publicly available, but not the part of the common storage API.
 *
 * For example, entity implementations may use some advanced functionality of the storage (e.g. get entities by reference).
 */
interface EntityStorageInstrumentation : EntityStorage {
  /**
   * Create entity using [newInstance] function.
   * In some implementations of the storage ([EntityStorageSnapshot]), the entity is cached and the new instance is created only once.
   */
  fun <T: WorkspaceEntity> initializeEntity(entityId: EntityId, newInstance: (() -> T)): T
  fun <T: WorkspaceEntity> resolveReference(reference: EntityReference<T>): T?
}
interface EntityStorageSnapshotInstrumentation : EntityStorageSnapshot, EntityStorageInstrumentation
interface MutableEntityStorageInstrumentation : MutableEntityStorage, EntityStorageInstrumentation
