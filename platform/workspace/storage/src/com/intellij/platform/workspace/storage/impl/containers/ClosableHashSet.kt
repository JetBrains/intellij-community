// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.workspace.storage.impl.containers

internal class ClosableHashSet<E> : HashSet<E>(), AutoCloseable {

  private var closed = false

  override fun add(element: E): Boolean {
    check()
    return super.add(element)
  }

  override fun addAll(elements: Collection<E>): Boolean {
    check()
    return super.addAll(elements)
  }

  override fun remove(element: E): Boolean {
    check()
    return super.remove(element)
  }

  override fun removeAll(elements: Collection<E>): Boolean {
    check()
    return super.removeAll(elements)
  }

  override fun retainAll(elements: Collection<E>): Boolean {
    check()
    return super.retainAll(elements)
  }

  override fun close() {
    closed = true
  }

  fun check() {
    if (closed) {
      throw CollectionClosedException()
    }
  }
}

internal class CollectionClosedException : RuntimeException("Collection is closed and cannot be modified")
