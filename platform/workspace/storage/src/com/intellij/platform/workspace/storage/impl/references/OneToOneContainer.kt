// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.references

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.impl.containers.IntIntUniqueBiMap
import java.util.concurrent.ConcurrentHashMap

internal class ImmutableOneToOneContainer(collection: Map<ConnectionId, IntIntUniqueBiMap>)
  : ImmutableReferenceContainer<IntIntUniqueBiMap>(collection) {

  // IJPL-148735: yes, we indeed use ConcurrentHashMap in immutable object.
  // For example, toMutableContainer leaks this collection (not a copy) to the
  // outer world, and anyone can mutate immutable object now.
  // Even if we have at most one writer, readers may get ConcurrentModificationException.
  // We should make sure that code that attempts to mutate immutable objects does not compile in the first place.
  constructor() : this(ConcurrentHashMap())

  override fun toMutableContainer(): MutableOneToOneContainer {
    return MutableOneToOneContainer(collection as MutableMap)
  }
}

internal class MutableOneToOneContainer(collection: MutableMap<ConnectionId, IntIntUniqueBiMap>):
  MutableReferenceContainer<IntIntUniqueBiMap>(collection) {

  override fun copyValue(value: IntIntUniqueBiMap): IntIntUniqueBiMap {
    return value.toImmutable().toMutable()
  }

  override fun toImmutable(): ImmutableOneToOneContainer {
    freeze()
    // We need to use mutable object inside the `ImmutableOneToOneContainer` otherwise we need to copy
    // object ot get Immutable version of values. So by calling `toImmutable` 5 times we will make 5 copies
    // For `IntIntUniqueBiMap` it's safe because it consumes same amount of memory
    return ImmutableOneToOneContainer(collection)
  }
}