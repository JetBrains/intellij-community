// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import com.intellij.util.containers.ConcurrentLongObjectMap
import org.jetbrains.annotations.ApiStatus

/**
 * The implementation of `copyOf` is allowed to not do copy - it can return the same map, read `copyOf` as `immutable`.
 */
@ApiStatus.Internal
interface Java11Shim {
  companion object {
    var INSTANCE: Java11Shim = DefaultJava11Shim()
    //
    //// wait for https://youtrack.jetbrains.com/issue/KTI-2139
    //@Deprecated("Use INSTANCE", ReplaceWith("INSTANCE"))
    //fun getINSTANCE(): Java11Shim = INSTANCE
  }

  fun <K : Any, V> copyOf(map: Map<K, V>): Map<K, V>

  fun <K : Any, V> mapOf(k: K, v: V): Map<K, V>

  fun <K : Any, V> mapOf(k: K, v: V, k2: K, v2: V): Map<K, V>

  fun <K : Any, V> mapOf(): Map<K, V>

  fun <E> copyOf(collection: Collection<E>): Set<E>

  fun <E> copyOfList(collection: Collection<E>): List<E>

  fun <E> listOf(): List<E>

  fun <E> listOf(element: E): List<E>

  fun <E> listOf(e1: E, e2: E): List<E>

  fun <E> listOf(array: Array<E>, size: Int): List<E>

  fun <V : Any> createConcurrentLongObjectMap(): ConcurrentLongObjectMap<V>

  fun getCallerClass(stackFrameIndex: Int): Class<*>?
}
