// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.command.impl

import com.intellij.codeWithMe.ClientId
import com.intellij.openapi.command.UndoConfirmationPolicy
import com.intellij.openapi.command.undo.AdjustableUndoableAction
import com.intellij.openapi.command.undo.DocumentReference
import com.intellij.openapi.command.undo.ImmutableActionChangeRange
import com.intellij.openapi.command.undo.UndoableAction
import kotlinx.collections.immutable.PersistentList
import kotlinx.collections.immutable.PersistentSet
import kotlinx.collections.immutable.persistentHashSetOf
import kotlinx.collections.immutable.persistentListOf
import org.jetbrains.annotations.ApiStatus
import java.lang.ref.Reference

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
  override fun get(index: Int): T = builder.get(index)
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

internal class UndoRedoListSnapshot<T>(val snapshot: PersistentList<T>) {
  fun toList(): UndoRedoList<T> = UndoRedoList(snapshot.builder())
}

internal class UndoRedoSet<T>(
  private var builder: PersistentSet.Builder<T> = persistentHashSetOf<T>().builder()
) : MutableSet<T> {
  fun snapshot(): UndoRedoSetSnapshot<T> = UndoRedoSetSnapshot(builder.build())

  fun resetTo(snapshot: UndoRedoSetSnapshot<T>) {
    builder = snapshot.snapshot.builder()
  }

  override val size: Int get() = builder.size
  override fun clear(): Unit = builder.clear()
  override fun isEmpty(): Boolean = builder.isEmpty()
  override fun iterator(): MutableIterator<T> = builder.iterator()
  override fun retainAll(elements: Collection<T>): Boolean = builder.retainAll(elements)
  override fun removeAll(elements: Collection<T>): Boolean = builder.removeAll(elements)
  override fun remove(element: T): Boolean = builder.remove(element)
  override fun containsAll(elements: Collection<T>): Boolean = builder.containsAll(elements)
  override fun contains(element: T): Boolean = builder.contains(element)
  override fun addAll(elements: Collection<T>): Boolean = builder.addAll(elements)
  override fun add(element: T): Boolean = builder.add(element)
}

internal class UndoRedoSetSnapshot<T>(val snapshot: PersistentSet<T>)

internal class LocalCommandMergerSnapshot(
  val documentReferences: DocumentReference?,
  val actions: UndoRedoListSnapshot<UndoableAction>,
  val lastGroupId: Reference<Any>?,
  val transparent: Boolean,
  val commandName: String?,
  val stateBefore: EditorAndState?,
  val stateAfter: EditorAndState?,
  val undoConfirmationPolicy: UndoConfirmationPolicy
) {
  companion object {
    fun empty() = LocalCommandMergerSnapshot(null, UndoRedoList<UndoableAction>().snapshot(), null, false, null, null, null, UndoConfirmationPolicy.DEFAULT)
  }
}

internal class LocalUndoRedoSnapshot(
  val clientSnapshots: Map<ClientId, PerClientLocalUndoRedoSnapshot>,

  val sharedUndoStack: UndoRedoListSnapshot<ImmutableActionChangeRange>,
  val sharedRedoStack: UndoRedoListSnapshot<ImmutableActionChangeRange>
)

internal class PerClientLocalUndoRedoSnapshot(
  val localCommandMergerSnapshot: LocalCommandMergerSnapshot,

  val undoStackSnapshot: UndoRedoListSnapshot<UndoableGroup>,
  val redoStackSnapshot: UndoRedoListSnapshot<UndoableGroup>,

  val actionsHolderSnapshot: UndoRedoSetSnapshot<AdjustableUndoableAction>
) {
  companion object {
    fun empty(): PerClientLocalUndoRedoSnapshot = PerClientLocalUndoRedoSnapshot(
      LocalCommandMergerSnapshot.empty(),

      UndoRedoList<UndoableGroup>().snapshot(),
      UndoRedoList<UndoableGroup>().snapshot(),

      UndoRedoSet<AdjustableUndoableAction>().snapshot()
    )
  }
}

@ApiStatus.Internal
@ApiStatus.Experimental
class ResetUndoHistoryToken internal constructor(
  private val undoManager: UndoManagerImpl,
  private var snapshot: LocalUndoRedoSnapshot?,
  private val reference: DocumentReference
) {
  fun resetHistory(): Boolean {
    val snapshot = snapshot ?: return false
    return undoManager.resetLocalHistory(reference, snapshot)
  }

  fun refresh() {
    snapshot = undoManager.getUndoRedoSnapshotForDocument(reference)
  }
}