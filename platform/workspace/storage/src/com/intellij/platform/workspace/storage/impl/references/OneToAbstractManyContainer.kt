// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.references

import com.intellij.platform.workspace.storage.impl.ChildEntityId
import com.intellij.platform.workspace.storage.impl.ConnectionId
import com.intellij.platform.workspace.storage.impl.ParentEntityId
import com.intellij.platform.workspace.storage.impl.containers.LinkedBidirectionalMap

private typealias ReferenceContainerType = LinkedBidirectionalMap<ChildEntityId, ParentEntityId>

internal class ImmutableOneToAbstractManyContainer(collection: Map<ConnectionId, ReferenceContainerType>)
  : ImmutableReferenceContainer<ReferenceContainerType>(collection) {
  constructor() : this(HashMap())

  override fun toMutableContainer(): MutableOneToAbstractManyContainer {
    return MutableOneToAbstractManyContainer(collection as MutableMap)
  }
}

internal class MutableOneToAbstractManyContainer(collection: MutableMap<ConnectionId, ReferenceContainerType>) :
  MutableReferenceContainer<ReferenceContainerType>(collection) {

  override fun copyValue(value: ReferenceContainerType): ReferenceContainerType {
    return LinkedBidirectionalMap<ChildEntityId, ParentEntityId>().also {
      value.forEach { (key, value) -> it.add(key, value) }
    }
  }

  override fun toImmutable(): ImmutableOneToAbstractManyContainer {
    freeze()
    return ImmutableOneToAbstractManyContainer(collection)
  }
}