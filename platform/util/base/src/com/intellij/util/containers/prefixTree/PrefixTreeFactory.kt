// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers.prefixTree

import com.intellij.util.containers.prefixTree.map.MutablePrefixTreeMap
import com.intellij.util.containers.prefixTree.map.PrefixTreeMapImpl
import com.intellij.util.containers.prefixTree.set.MutablePrefixTreeSet
import com.intellij.util.containers.prefixTree.set.PrefixTreeSetImpl
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
fun interface PrefixTreeFactory<Key, KeyElement> {

  fun convertToList(element: Key): List<KeyElement>

  fun createSet(): MutablePrefixTreeSet<Key> =
    PrefixTreeSetImpl(this)

  fun <Value> createMap(): MutablePrefixTreeMap<Key, Value> =
    PrefixTreeMapImpl(this)

  companion object {

    fun <K, E> create(convert: (K) -> List<E>): PrefixTreeFactory<K, E> {
      return object : PrefixTreeFactory<K, E> {
        override fun convertToList(element: K): List<E> {
          return convert(element)
        }
      }
    }
  }
}