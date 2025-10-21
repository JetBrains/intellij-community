// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import com.intellij.util.ReflectionUtil
import java.util.*

internal class DefaultJava11Shim : Java11Shim() {
  override fun <K : Any, V> copyOf(map: Map<K, V>): Map<K, V> {
    return Collections.unmodifiableMap(map)
  }

  override fun <K : Any, V> mapOf(k: K, v: V): Map<K, V> {
    return Collections.singletonMap(k, v)
  }

  @Suppress("ReplacePutWithAssignment")
  override fun <K : Any, V> mapOf(k: K, v: V, k2: K, v2: V): Map<K, V> {
    val map = HashMap<K, V>(2)
    map.put(k, v)
    map.put(k2, v2)
    return map
  }

  override fun <E> copyOf(collection: Collection<E>): Set<E> {
    return Collections.unmodifiableSet(HashSet(collection))
  }

  override fun <K : Any, V> mapOf(): Map<K, V> {
    return Collections.emptyMap()
  }

  override fun <E> listOf(): List<E> {
    return Collections.emptyList()
  }

  override fun <E> listOf(element: E): List<E> {
    return Collections.singletonList(element)
  }

  override fun <E> copyOfList(collection: Collection<E>): List<E> {
    return Collections.unmodifiableList(collection.toList())
  }

  @Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")
  override fun <E> listOf(e1: E, e2: E): List<E> {
    return Arrays.asList(e1, e2)
  }

  override fun <E> listOf(array: Array<E>, size: Int): List<E> {
    return if (array.size == size) {
      array.asList()
    }
    else {
      array.asList().subList(0, size)
    }
  }

  override fun getCallerClass(stackFrameIndex: Int): Class<*>? {
    return ReflectionUtil.getCallerClass(stackFrameIndex + 1) // +1 to accommodate the current frame
  }
}