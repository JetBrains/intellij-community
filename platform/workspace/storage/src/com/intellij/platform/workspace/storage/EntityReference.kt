// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import org.jetbrains.annotations.ApiStatus

/**
 * Represents a reference to an entity which can be stored outside [EntityStorage].
 *
 * The reference can be obtained via [WorkspaceEntity.createReference]. An instance of this class stores an internal ID of the entity,
 * and doesn't contain reference to the original storage, so it's ok to store them in long-living data structures, this won't create a 
 * memory leak.
 *
 * The reference will resolve to the same entity for the same storage, and will survive [modifications][MutableEntityStorage.modifyEntity],
 * but if the entity is removed or replaced by a different one by [MutableEntityStorage.replaceBySource], the reference may either 
 * resolve to `null` or resolve to a completely different entity which reused the same internal ID. So if you need to be sure that the
 * reference resolves to the original entity, you need to also subscribe to changes in the storage.
 */
@ApiStatus.NonExtendable
public interface EntityReference<out E : WorkspaceEntity> {
  /**
   * Returns an entity corresponding to this reference in [storage] or `null` if there is no such entity.
   */
  public fun resolve(storage: EntityStorage): E?

  /**
   * Checks whether this reference points to the given [entity].
   * This function works faster than resolving the reference and comparing the result.
   */
  public fun isReferenceTo(entity: WorkspaceEntity): Boolean
}