// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf


internal class UndoRedoSet<T>() : MutableSet<T> {
  private var builder: PersistentSet.Builder<T> = persistentHashSetOf<T>().builder()

  fun snapshot(): UndoRedoSetSnapshot<T> = UndoRedoSetSnapshot(builder.build())

  fun resetTo(snapshot: UndoRedoSetSnapshot<T>) {
    builder = snapshot.snapshot.builder()
  }

  override val size: Int get() = builder.size
  override fun clear(): Unit = builder.clear()
  override fun isEmpty(): Boolean = builder.isEmpty()
  override fun iterator(): MutableIterator<T> = builder.iterator()
  override fun retainAll(elements: Collection<T>): Boolean = builder.retainAll(elements.toSet())
  override fun removeAll(elements: Collection<T>): Boolean = builder.removeAll(elements.toSet())
  override fun remove(element: T): Boolean = builder.remove(element)
  override fun containsAll(elements: Collection<T>): Boolean = builder.containsAll(elements)
  override fun contains(element: T): Boolean = builder.contains(element)
  override fun addAll(elements: Collection<T>): Boolean = builder.addAll(elements)
  override fun add(element: T): Boolean = builder.add(element)
}
