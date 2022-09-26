// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl.containers

import org.jetbrains.annotations.TestOnly
import java.util.*
import java.util.function.Predicate
import java.util.function.UnaryOperator
import kotlin.math.max

/**
 * Sublist isn't update indexes for now
 */
class MutableWorkspaceList<E>(collection: Collection<E>) : ArrayList<E>(collection) {
  private var updateAction: ((value: List<E>) -> Unit)? = null

  override fun add(element: E): Boolean {
    checkModificationAllowed()
    val result = super.add(element)
    callForOutsideUpdate()
    return result
  }

  override fun add(index: Int, element: E) {
    checkModificationAllowed()
    super.add(index, element)
    callForOutsideUpdate()
  }

  override fun addAll(elements: Collection<E>): Boolean {
    checkModificationAllowed()
    val result = super.addAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun addAll(index: Int, elements: Collection<E>): Boolean {
    checkModificationAllowed()
    val result = super.addAll(index, elements)
    callForOutsideUpdate()
    return result
  }

  override fun clear() {
    checkModificationAllowed()
    super.clear()
    callForOutsideUpdate()
  }

  override fun remove(element: E): Boolean {
    checkModificationAllowed()
    val result = super.remove(element)
    callForOutsideUpdate()
    return result
  }

  override fun removeAll(elements: Collection<E>): Boolean {
    checkModificationAllowed()
    val result = super.removeAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun retainAll(elements: Collection<E>): Boolean {
    checkModificationAllowed()
    val result = super.retainAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun removeIf(filter: Predicate<in E>): Boolean {
    checkModificationAllowed()
    val result = super.removeIf(filter)
    callForOutsideUpdate()
    return result
  }

  override fun removeAt(index: Int): E {
    checkModificationAllowed()
    val result = super.removeAt(index)
    callForOutsideUpdate()
    return result
  }

  override fun set(index: Int, element: E): E {
    checkModificationAllowed()
    val result = super.set(index, element)
    callForOutsideUpdate()
    return result
  }

  override fun replaceAll(operator: UnaryOperator<E>) {
    checkModificationAllowed()
    super.replaceAll(operator)
    callForOutsideUpdate()
  }

  fun setModificationUpdateAction(updater: (value: List<E>) -> Unit) {
    updateAction = updater
  }

  fun cleanModificationUpdateAction() {
    updateAction = null
  }

  private fun callForOutsideUpdate() {
    updateAction?.invoke(this)
  }

  private fun checkModificationAllowed() {
    if (updateAction == null) {
      throw IllegalStateException("Modifications are allowed inside `modifyEntity` method only!")
    }
  }
}

fun <T> Collection<T>.toMutableWorkspaceList(): MutableWorkspaceList<T> {
  return MutableWorkspaceList(this)
}

/**
 * [MutableIterable.removeAll] and [MutableIterator.remove]  aren't update indexes for now
 */
class MutableWorkspaceSet<E>(collection: Collection<E>) : LinkedHashSet<E>(max(2 * collection.size, 11)) {
  private var updateAction: ((Set<E>) -> Unit)? = null

  init {
    collection.forEach { super.add(it) }
  }

  override fun add(element: E): Boolean {
    checkModificationAllowed()
    val result = super.add(element)
    callForOutsideUpdate()
    return result
  }

  override fun addAll(elements: Collection<E>): Boolean {
    checkModificationAllowed()
    val result = super.addAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun clear() {
    checkModificationAllowed()
    super.clear()
    callForOutsideUpdate()
  }

  override fun remove(element: E): Boolean {
    checkModificationAllowed()
    val result = super.remove(element)
    callForOutsideUpdate()
    return result
  }

  override fun removeAll(elements: Collection<E>): Boolean {
    checkModificationAllowed()
    val result = super.removeAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun retainAll(elements: Collection<E>): Boolean {
    checkModificationAllowed()
    val result = super.retainAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun removeIf(filter: Predicate<in E>): Boolean {
    checkModificationAllowed()
    val result = super.removeIf(filter)
    callForOutsideUpdate()
    return result
  }

  fun setModificationUpdateAction(updater: (value: Set<E>) -> Unit) {
    updateAction = updater
  }

  @TestOnly
  fun getModificationUpdateAction(): ((value: Set<E>) -> Unit)? {
    return updateAction
  }

  fun cleanModificationUpdateAction() {
    updateAction = null
  }

  private fun callForOutsideUpdate() {
    updateAction?.invoke(this)
  }

  private fun checkModificationAllowed() {
    if (updateAction == null) {
      throw IllegalStateException("Modifications are allowed inside `modifyEntity` method only!")
    }
  }
}

fun <T> Collection<T>.toMutableWorkspaceSet(): MutableWorkspaceSet<T> {
  return MutableWorkspaceSet(this)
}