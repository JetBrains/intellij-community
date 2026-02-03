// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.syntax.extensions.impl

import fleet.util.multiplatform.Actual
import kotlinx.atomicfu.locks.ReentrantLock
import kotlinx.atomicfu.locks.withLock

/** Native implementation of [newConcurrentMap] */
@Suppress("unused")
@Actual
internal fun <K : Any, V : Any> newConcurrentMapNative(): MultiplatformConcurrentMap<K, V> =
  SyntaxConcurrentMapNative()

@Actual
internal fun <K : Any> newConcurrentSetNative(): MutableSet<K> =
  SyntaxConcurrentSetNative()

private class SyntaxConcurrentMapNative<K : Any, V : Any>() : MultiplatformConcurrentMap<K, V> { // TODO: proper concurrent map
  private val map = HashMap<K, V>()
  private val lock = ReentrantLock()

  override val size: Int get() = lock.withLock { map.size }
  override val keys: MutableSet<K> get() = lock.withLock { map.keys.toMutableSet() }
  override fun computeIfAbsent(key: K, f: (K) -> V): V = lock.withLock { get(key) ?: f(key).also { put(key, it) } }
  override fun get(key: K): V? = lock.withLock { map[key] }
  override fun remove(key: K): V? = lock.withLock { map.remove(key) }
  override fun put(key: K, value: V): V? = lock.withLock { map.put(key, value) }
  override fun hashCode(): Int = map.hashCode()
  override fun toString(): String = map.toString()
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SyntaxConcurrentMapNative<*, *>) return false
    return map == other.map
  }
}

private class SyntaxConcurrentSetNative<T: Any> : MutableSet<T> {
  private val set = HashSet<T>()
  private val lock = ReentrantLock()
  override fun iterator(): MutableIterator<T> = object : MutableIterator<T> {
    private val iterator = set.iterator()
    override fun remove() = lock.withLock { iterator.remove() }
    override fun next(): T = lock.withLock { iterator.next() }
    override fun hasNext(): Boolean = lock.withLock { iterator.hasNext() }
  }
  override fun add(element: T): Boolean = lock.withLock { set.add(element) }
  override fun remove(element: T): Boolean = lock.withLock { set.remove(element) }
  override fun addAll(elements: Collection<T>): Boolean = lock.withLock { set.addAll(elements) }
  override fun removeAll(elements: Collection<T>): Boolean = lock.withLock { set.removeAll(elements) }
  override fun retainAll(elements: Collection<T>): Boolean = lock.withLock { set.retainAll(elements) }
  override fun clear(): Unit = lock.withLock { set.clear() }
  override val size: Int get() = lock.withLock { set.size }
  override fun isEmpty(): Boolean = lock.withLock { set.isEmpty() }
  override fun contains(element: T): Boolean = lock.withLock { set.contains(element) }
  override fun containsAll(elements: Collection<T>): Boolean = lock.withLock { set.containsAll(elements) }
  override fun hashCode(): Int = lock.withLock { set.hashCode() }
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is Set<*>) return false
    return set == other
  }
}
