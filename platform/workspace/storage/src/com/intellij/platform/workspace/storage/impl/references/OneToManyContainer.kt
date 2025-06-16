// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.references

import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.impl.containers.NonNegativeIntIntBiMap
import java.util.concurrent.ConcurrentHashMap

internal class ImmutableOneToManyContainer(collection: Map<ConnectionId, NonNegativeIntIntBiMap>)
  : ImmutableReferenceContainer<NonNegativeIntIntBiMap>(collection) {

  // IJPL-148735: yes, we indeed use ConcurrentHashMap in immutable object.
  // For example, toMutableContainer leaks this collection (not a copy) to the
  // outer world, and anyone can mutate immutable object now.
  // Even if we have at most one writer, readers may get ConcurrentModificationException.
  // We should make sure that code that attempts to mutate immutable objects does not compile in the first place.
  constructor() : this(ConcurrentHashMap())

  override fun toMutableContainer(): MutableOneToManyContainer {
    return MutableOneToManyContainer(collection as MutableMap)
  }
}

internal class MutableOneToManyContainer(collection: MutableMap<ConnectionId, NonNegativeIntIntBiMap>) :
  MutableReferenceContainer<NonNegativeIntIntBiMap>(collection) {
  override fun copyValue(value: NonNegativeIntIntBiMap): NonNegativeIntIntBiMap {
    return value.toImmutable().toMutable()
  }

  override fun toImmutable(): ImmutableOneToManyContainer {
    freeze()
    // We need to use mutable object inside the `ImmutableOneToManyContainer` otherwise we need to copy
    // object ot get Immutable version of values. So by calling `toImmutable` 5 times we will make 5 copies
    // Mutable version of `NonNegativeIntIntBiMap` consumes more memory, so we need to call `toImmutable` to `flash`
    // data before passing to Immutable collection
    collection.values.forEach { it.toImmutable() }
    return ImmutableOneToManyContainer(collection)
  }
}