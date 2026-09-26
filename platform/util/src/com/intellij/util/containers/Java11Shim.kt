// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.containers

import org.jetbrains.annotations.ApiStatus

/**
 * A factory of various containers which implementations are different in pre/after jdk9.
 * Used to simplify porting jdk9+ collections to jdk8-modules
 */
@ApiStatus.Internal
abstract class Java11Shim {
  companion object {
    @JvmField
    var INSTANCE: Java11Shim = DefaultJava11Shim()
    fun <V> createConcurrentLongObjectMap(): ConcurrentLongObjectMap<V> = ConcurrentLongObjectHashMap()
    fun <V> createConcurrentIntObjectMap(): ConcurrentIntObjectMap<V> = ConcurrentIntObjectHashMap()
    fun <V> createConcurrentIntObjectMap(initialCapacity:Int, loadFactor:Float, concurrencyLevel:Int): ConcurrentIntObjectMap<V> = ConcurrentIntObjectHashMap(initialCapacity, loadFactor, concurrencyLevel)
    fun <V> createConcurrentIntObjectSoftValueMap(): ConcurrentIntObjectMap<V> = ConcurrentIntKeySoftValueHashMap()
    fun <V> createConcurrentIntObjectWeakValueMap(): ConcurrentIntObjectMap<V> = ConcurrentIntKeyWeakValueHashMap()
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

  abstract fun getCallerClass(stackFrameIndex: Int): Class<*>?
}