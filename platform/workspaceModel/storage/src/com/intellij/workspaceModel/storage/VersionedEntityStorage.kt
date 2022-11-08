// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.workspaceModel.storage

import java.util.*

/**
 * Provides access to instance of [EntityStorage] which can be replaced by newer versions.
 */
interface VersionedEntityStorage {
  val version: Long
  val current: EntityStorage

  // Return builder or storage that is base for this entity storage
  val base: EntityStorage

  /**
   * Return cached result or evaluate it by calling [value::source] if it isn't evaluated for the current version of storage yet.
   */
  fun <R> cachedValue(value: CachedValue<R>): R
  fun <R> clearCachedValue(value: CachedValue<R>)

  /**
   * Return cached result or evaluate it by calling [value::source] if it isn't evaluated for the current version of storage and the
   * given parameter yet.
   */
  fun <P, R> cachedValue(value: CachedValueWithParameter<P, R>, parameter: P): R
  fun <P, R> clearCachedValue(value: CachedValueWithParameter<P, R>, parameter: P)
}

/**
 * Represents a key for a cached value and provide [source] function to evaluate it. [source] must be thread safe.
 */
// TODO: future: optimized computations if `source` is pure
// TODO debug by print .ctor call site
class CachedValue<R>(val source: (EntityStorage) -> R)

/**
 * Represents a key for a parametrized cached value and provide [source] function to evaluate it. [source] must be thread safe.
 */
class CachedValueWithParameter<P, R>(val source: (EntityStorage, P) -> R)

/**
 * Change containing set of changes.
 *
 * As this is not a list of change operations, but a list of changes, order of events is not defined.
 */
abstract class VersionedStorageChange(versionedStorage: VersionedEntityStorage) : EventObject(versionedStorage) {
  abstract val storageBefore: EntityStorageSnapshot
  abstract val storageAfter: EntityStorageSnapshot

  abstract fun <T : WorkspaceEntity> getChanges(entityClass: Class<T>): List<EntityChange<T>>

  abstract fun getAllChanges(): Sequence<EntityChange<*>>
}