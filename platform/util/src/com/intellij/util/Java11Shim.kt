// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.containers.ConcurrentLongObjectMap
import com.intellij.util.containers.DefaultJava11Shim
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
abstract class Java11Shim {
  companion object {
    @JvmField
    var INSTANCE: Java11Shim = DefaultJava11Shim()
  }

  /**
   * The implementation of `copyOf` is allowed to not do copy - it can return the same map, read `copyOf` as `immutable`.
   */
  abstract fun <K : Any, V> copyOf(map: Map<K, V>): Map<K, V>

  abstract fun <K : Any, V> mapOf(k: K, v: V): Map<K, V>

  abstract fun <K : Any, V> mapOf(k: K, v: V, k2: K, v2: V): Map<K, V>

  abstract fun <K : Any, V> mapOf(): Map<K, V>

  abstract fun <E> copyOf(collection: Collection<E>): Set<E>

  abstract fun <E> copyOfList(collection: Collection<E>): List<E>

  abstract fun <E> listOf(): List<E>

  abstract fun <E> listOf(element: E): List<E>

  abstract fun <E> listOf(e1: E, e2: E): List<E>

  abstract fun <E> listOf(array: Array<E>, size: Int): List<E>

  abstract fun <V : Any> createConcurrentLongObjectMap(): ConcurrentLongObjectMap<V>

  abstract fun getCallerClass(stackFrameIndex: Int): Class<*>?
}
