// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vfs.newvfs.persistent.log.util

import java.util.*
import java.util.concurrent.atomic.AtomicReference

/**
 * A map-like data structure with thread-safe [get], [getOrCreate] ([get] time complexity is `O(index / chunkSize)`).
 * Optimized for working with small indices. Implementation is similar to https://en.wikipedia.org/wiki/Unrolled_linked_list.
 * @param create guaranteed to be called at most once for each index
 */
@Suppress("UNCHECKED_CAST")
class SmallIndexMap<T: Any>(private val create: (Int) -> T, val chunkSize: Int = 64) {
  private var head: Node<Array<Any?>> = NodeImpl(arrayOfNulls(chunkSize)) // only modified on close

  private fun chunkId(index: Int) = index / chunkSize
  private fun elemId(index: Int) = index % chunkSize

  fun get(index: Int): T? {
    val chunk = getChunk(chunkId(index))
    val elemId = elemId(index)
    return chunk[elemId] as T?
  }

  fun getOrCreate(index: Int): T {
    val chunk = getChunk(chunkId(index))
    val elemId = elemId(index)
    chunk[elemId]?.let { return it as T }
    synchronized(this) {
      chunk[elemId]?.let { return it as T }
      val v = create(index)
      chunk[elemId] = v
      return v
    }
  }

  fun forEachExisting(body: (T) -> Unit) {
    synchronized(this) {
      var ptr: Node<Array<Any?>>? = head
      while (ptr != null) {
        val arr = ptr.value
        for (i in 0 until chunkSize) {
          arr[i]?.let { body(it as T) }
        }
        ptr = ptr.nextOrNull()
      }
    }
  }

  private fun getChunk(chunkId: Int): Array<Any?> {
    var ptr = head
    repeat(chunkId) {
      ptr = ptr.next { arrayOfNulls(chunkSize) }
    }
    return ptr.value
  }

  /**
   * Any access after [close] will throw [IllegalStateException]
   */
  fun close() {
    synchronized(this) {
      head = ClosedMapNode()
    }
  }

  private interface Node<C> {
    val value: C

    fun next(init: () -> C): Node<C>
    fun nextOrNull(): Node<C>?
  }

  private class NodeImpl<C>(override val value: C): Node<C> {
    private var next = AtomicReference<NodeImpl<C>?>(null)

    override fun next(init: () -> C): NodeImpl<C> {
      next.get()?.let { return it }
      val v = NodeImpl(init())
      if (next.compareAndSet(null, v)) {
        return v
      }
      return next.get()!!
    }

    override fun nextOrNull(): NodeImpl<C>? = next.get()
  }

  private class ClosedMapNode : Node<Array<Any?>> {
    override val value: Nothing
      get() = throw IllegalStateException("access to closed SmallIndexMap")
    override fun nextOrNull(): Nothing = throw IllegalStateException("access to closed SmallIndexMap")
    override fun next(init: () -> Array<Any?>): Nothing = throw IllegalStateException("access to closed SmallIndexMap")
  }
}