// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.util

import com.intellij.util.containers.ConcurrentLongObjectMap
import org.jetbrains.annotations.ApiStatus
import java.util.*

@ApiStatus.Internal
// implementation of `copyOf` is allowed to not do copy - it can return the same map, read `copyOf` as `immutable`
abstract class Java11Shim {
  companion object {
    @JvmField
    var INSTANCE: Java11Shim = object : Java11Shim() {
      override fun <K : Any, V> copyOf(map: Map<K, V>) = Collections.unmodifiableMap(map)

      override fun <K : Any, V> mapOf(k: K, v: V): Map<K, V> = Collections.singletonMap(k, v)

      override fun <K : Any, V> mapOf(k: K, v: V, k2: K, v2: V): Map<K, V> {
        val map = HashMap<K, V>(2)
        map.put(k, v)
        map.put(k2, v2)
        return map
      }

      override fun <E> copyOf(collection: Collection<E>): Set<E> = Collections.unmodifiableSet(HashSet(collection))

      override fun <V : Any> createConcurrentLongObjectMap(): ConcurrentLongObjectMap<V> {
        return ConcurrentLongObjectHashMap()
      }

      override fun <K : Any, V> mapOf(): Map<K, V> = Collections.emptyMap()

      override fun <E> listOf(): List<E> = Collections.emptyList()

      override fun <E> listOf(element: E): List<E> = Collections.singletonList(element)

      override fun <E> listOf(e1: E, e2: E): List<E> = Arrays.asList(e1, e2)

      override fun <E> listOf(array: Array<E>, size: Int) = if (array.size == size) array.asList() else array.asList().subList(0, size)
    }
  }

  abstract fun <K : Any, V> copyOf(map: Map<K, V>): Map<K, V>

  abstract fun <K : Any, V> mapOf(k: K, v: V): Map<K, V>

  abstract fun <K : Any, V> mapOf(k: K, v: V, k2: K, v2: V): Map<K, V>

  abstract fun <K : Any, V> mapOf(): Map<K, V>

  abstract fun <E> copyOf(collection: Collection<E>): Set<E>

  abstract fun <E> listOf(): List<E>

  abstract fun <E> listOf(element: E): List<E>

  abstract fun <E> listOf(e1: E, e2: E): List<E>

  abstract fun <E> listOf(array: Array<E>, size: Int): List<E>

  abstract fun <V : Any> createConcurrentLongObjectMap(): ConcurrentLongObjectMap<V>
}
