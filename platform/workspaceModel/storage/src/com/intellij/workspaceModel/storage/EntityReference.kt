// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage

/**
 * Represents a reference to an entity inside of [WorkspaceEntity].
 *
 * The reference can be obtained via [EntityStorage.createReference].
 *
 * The reference will return the same entity for the same storage, but the changes in storages should be tracked if the client want to
 *   use this reference between different storages. For example, if the referred entity was removed from the storage, this reference may
 *   return null, but it can also return a different (newly added) entity.
 */
abstract class EntityReference<out E : WorkspaceEntity> {
  abstract fun resolve(storage: EntityStorage): E?
}