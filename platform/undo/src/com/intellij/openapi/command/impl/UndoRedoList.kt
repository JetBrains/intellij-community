// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.persistentListOf

internal class UndoRedoList<T>(
  private var builder: PersistentList.Builder<T> = persistentListOf<T>().builder()
) : MutableList<T> {

  fun getLast(): T = last()
  fun removeLast(): T = removeAt(lastIndex)

  fun getFirst(): T = first()

  // this can be quite slow for huge lists, but in our case, the size is usually <= 200, so it's not a problem.
  // If this causes any problems, we can use a hashmap or another more efficient data structure
  fun removeFirstSlow(): T = removeFirst()

  fun peekLast(): T? = lastOrNull()

  fun descendingIterator(): Iterator<T> = object : MutableIterator<T> {
    val listIterator = listIterator(size)

    override fun hasNext(): Boolean = listIterator.hasPrevious()
    override fun next(): T = listIterator.previous()
    override fun remove() = listIterator.remove()
  }

  fun snapshot(): UndoRedoListSnapshot<T> = UndoRedoListSnapshot(builder.build())

  fun resetTo(snapshot: UndoRedoListSnapshot<T>) {
    builder = snapshot.snapshot.builder()
  }

  override val size: Int get() = builder.size
  override fun clear(): Unit = builder.clear()
  override fun get(index: Int): T = builder[index]
  override fun isEmpty(): Boolean = builder.isEmpty()
  override fun iterator(): MutableIterator<T> = builder.iterator()
  override fun listIterator(): MutableListIterator<T> = builder.listIterator()
  override fun listIterator(index: Int): MutableListIterator<T> = builder.listIterator(index)
  override fun removeAt(index: Int): T = builder.removeAt(index)
  override fun subList(fromIndex: Int, toIndex: Int): MutableList<T> = builder.subList(fromIndex, toIndex)
  override fun set(index: Int, element: T): T = builder.set(index, element)
  override fun retainAll(elements: Collection<T>): Boolean = builder.retainAll(elements)
  override fun removeAll(elements: Collection<T>): Boolean = builder.removeAll(elements)
  override fun remove(element: T): Boolean = builder.remove(element)
  override fun lastIndexOf(element: T): Int = builder.lastIndexOf(element)
  override fun indexOf(element: T): Int = builder.indexOf(element)
  override fun containsAll(elements: Collection<T>): Boolean = builder.containsAll(elements)
  override fun contains(element: T): Boolean = builder.contains(element)
  override fun addAll(elements: Collection<T>): Boolean = builder.addAll(elements)
  override fun addAll(index: Int, elements: Collection<T>): Boolean = builder.addAll(index, elements)
  override fun add(index: Int, element: T): Unit = builder.add(index, element)
  override fun add(element: T): Boolean = builder.add(element)
  override fun toString(): String = joinToString(", ", "[", "]") { it.toString() }
}
