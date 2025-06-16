// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.references

import com.google.common.collect.BiMap
import com.google.common.collect.HashBiMap
import com.intellij.platform.workspace.storage.ConnectionId
import com.intellij.platform.workspace.storage.impl.ChildEntityId
import com.intellij.platform.workspace.storage.impl.ParentEntityId
import java.util.concurrent.ConcurrentHashMap

internal class ImmutableAbstractOneToOneContainer(collection: Map<ConnectionId, BiMap<ChildEntityId, ParentEntityId>>)
  : ImmutableReferenceContainer<BiMap<ChildEntityId, ParentEntityId>>(collection) {

  // IJPL-148735: yes, we indeed use ConcurrentHashMap in immutable object.
  // For example, toMutableContainer leaks this collection (not a copy) to the
  // outer world, and anyone can mutate immutable object now.
  // Even if we have at most one writer, readers may get ConcurrentModificationException.
  // We should make sure that code that attempts to mutate immutable objects does not compile in the first place.
  constructor() : this(ConcurrentHashMap())

  override fun toMutableContainer(): MutableAbstractOneToOneContainer {
    return MutableAbstractOneToOneContainer(collection as MutableMap)
  }
}

internal class MutableAbstractOneToOneContainer(collection: MutableMap<ConnectionId, BiMap<ChildEntityId, ParentEntityId>>) :
  MutableReferenceContainer<BiMap<ChildEntityId, ParentEntityId>>(collection) {

  override fun copyValue(value: BiMap<ChildEntityId, ParentEntityId>): BiMap<ChildEntityId, ParentEntityId> {
    return value.toMap(HashBiMap.create<ChildEntityId, ParentEntityId>())
  }

  override fun toImmutable(): ImmutableAbstractOneToOneContainer {
    freeze()
    return ImmutableAbstractOneToOneContainer(collection)
  }
}