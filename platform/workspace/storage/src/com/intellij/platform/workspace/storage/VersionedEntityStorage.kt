// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage

import org.jetbrains.annotations.ApiStatus

/**
 * Provides access to instance of [EntityStorage] which can be replaced by newer versions. 
 * Code in plugins usually shouldn't access this interface directly, 
 * [WorkspaceModel][com.intellij.platform.backend.workspace.WorkspaceModel] should be used instead.
 */
@ApiStatus.Internal
public interface VersionedEntityStorage {
  public val version: Long
  public val current: EntityStorage

  /**
   * Returns a builder or storage that is the base for this entity storage.
   */
  public val base: EntityStorage

  /**
   * Return cached result or evaluate it by calling [value::source] if it isn't evaluated for the current version of storage yet.
   */
  public fun <R> cachedValue(value: CachedValue<R>): R
  public fun <R> clearCachedValue(value: CachedValue<R>)

  /**
   * Return cached result or evaluate it by calling [value::source] if it isn't evaluated for the current version of storage and the
   * given parameter yet.
   */
  public fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R
  public fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P)
}

/**
 * Represents a key for a cached value and provide [source] function to evaluate it. [source] must be thread safe.
 */
// TODO: future: optimized computations if `source` is pure
// TODO debug by print .ctor call site
@ApiStatus.Internal
public class CachedValue<R>(public val source: (EntityStorage) -> R)

/**
 * Represents a key for a parametrized cached value and provide [source] function to evaluate it. [source] must be thread safe.
 */
@ApiStatus.Internal
public class CachedValueWithParameter<P, R>(public val source: (EntityStorage, P) -> R)

/**
 * Change containing a set of changes. Instances of this class are passed to
 * [com.intellij.platform.backend.workspace.WorkspaceModel.eventLog] when you subscribe to changes in the IDE process.
 */
public interface VersionedStorageChange {
  public val storageBefore: ImmutableEntityStorage
  public val storageAfter: ImmutableEntityStorage

  /**
   * Get changes for some type of entity.
   *
   * The events are ordered: Removed -> Replaced -> Added
   */
  public fun <T : WorkspaceEntity> getChanges(entityClass: Class<T>): List<EntityChange<T>>
}
