// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.references

import com.intellij.platform.workspace.storage.ConnectionId
import org.jetbrains.annotations.TestOnly
import java.util.concurrent.ConcurrentHashMap

internal abstract class ReferenceContainer<T>(protected open val collection: Map<ConnectionId, T>) {
  abstract operator fun get(connectionId: ConnectionId): T?

  operator fun contains(connectionId: ConnectionId): Boolean {
    return collection.contains(connectionId)
  }

  val keys: Set<ConnectionId>
    get() = collection.keys

  fun filterKeys(predicate: (ConnectionId) -> Boolean): Map<ConnectionId, T> {
    return collection.filterKeys(predicate)
  }

  fun forEach(action: (Map.Entry<ConnectionId, T>) -> Unit) {
    collection.forEach(action)
  }

  @TestOnly
  internal fun getInternalStructure(): Map<ConnectionId, T> {
    return collection
  }
}


internal abstract class ImmutableReferenceContainer<T>(collection: Map<ConnectionId, T>)
  : ReferenceContainer<T>(collection) {

  override operator fun get(connectionId: ConnectionId): T? {
    return collection[connectionId]
  }

  abstract fun toMutableContainer(): MutableReferenceContainer<*>
}

internal abstract class MutableReferenceContainer<T>(override var collection: MutableMap<ConnectionId, T>) : ReferenceContainer<T>(collection) {
  private var freezed = true

  internal fun isFreezed(): Boolean {

    return freezed
  }

  protected fun freeze() {
    freezed = true
  }

  override operator fun get(connectionId: ConnectionId): T? {
    if (!collection.contains(connectionId)) return null
    startWrite()
    return collection[connectionId]
  }

  operator fun set(connectionId: ConnectionId, value: T) {
    startWrite()
    collection[connectionId] = value
  }

  private fun startWrite() {
    if (!freezed) return
    // IJPL-148735: yes, we indeed use ConcurrentHashMap in thread-unsafe object.
    // For example, invoking toImmutable makes the object thread-safe (with the same reference to thread-unsafe collection inside)
    // and then invoking toMutableContainer leaks this collection (not a copy) to the outer world,
    // and anyone can mutate immutable object now. So this collection is indeed used in multithreaded environment
    // with at most one writer (if clients are implemented correctly) and many readers.
    // We should make sure that code that attempts to mutate immutable objects does not compile in the first place.
    collection = collection.mapValuesTo(ConcurrentHashMap(collection.size)) { copyValue(it.value) }
    freezed = false
  }

  internal abstract fun copyValue(value: T): T

  internal abstract fun toImmutable(): ImmutableReferenceContainer<*>

  @TestOnly
  internal fun clear() {
    collection.clear()
  }

  @TestOnly
  internal fun putAll(from: MutableReferenceContainer<T>) {
    collection.putAll(from.collection)
  }
}