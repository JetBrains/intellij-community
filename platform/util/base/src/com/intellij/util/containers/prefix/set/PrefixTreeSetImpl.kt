// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefix.set

import com.intellij.util.containers.prefix.factory.PrefixTreeFactory
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal class PrefixTreeSetImpl<Key, KeyElement>(
  convertor: PrefixTreeFactory<Key, KeyElement>
) : MutablePrefixTreeSet<Key> {

  private val map = convertor.createMap<Nothing?>()

  override val size: Int
    get() = map.size

  override fun isEmpty(): Boolean {
    return map.isEmpty()
  }

  override fun contains(element: Key): Boolean {
    return map.contains(element)
  }

  override fun containsAll(elements: Collection<Key>): Boolean {
    return map.keys.containsAll(elements)
  }

  override fun getDescendants(element: Key): Set<Key> {
    return map.getDescendantKeys(element)
  }

  override fun getAncestors(element: Key): Set<Key> {
    return map.getAncestorKeys(element)
  }

  override fun getRoots(): Set<Key> {
    return map.getRootKeys()
  }

  override fun add(element: Key) {
    map[element] = null
  }

  override fun remove(element: Key) {
    map.remove(element)
  }

  override fun iterator(): Iterator<Key> {
    return map.keys.iterator()
  }
}