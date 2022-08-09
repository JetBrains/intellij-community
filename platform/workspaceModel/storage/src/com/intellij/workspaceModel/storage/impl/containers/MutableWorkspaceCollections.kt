// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.storage.impl.containers

import java.util.*
import java.util.function.Predicate
import java.util.function.UnaryOperator
import kotlin.collections.List

/**
 * Sublist isn't update indexes for now
 */
class MutableWorkspaceList<E>(collection: Collection<E>) : ArrayList<E>(collection) {
  private lateinit var updateAction: (value: List<E>) -> Unit

  override fun add(element: E): Boolean {
    val result = super.add(element)
    callForOutsideUpdate()
    return result
  }

  override fun add(index: Int, element: E) {
    super.add(index, element)
    callForOutsideUpdate()
  }

  override fun addAll(elements: Collection<E>): Boolean {
    val result = super.addAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun addAll(index: Int, elements: Collection<E>): Boolean {
    val result = super.addAll(index, elements)
    callForOutsideUpdate()
    return result
  }

  override fun clear() {
    super.clear()
    callForOutsideUpdate()
  }

  override fun remove(element: E): Boolean {
    val result = super.remove(element)
    callForOutsideUpdate()
    return result
  }

  override fun removeAll(elements: Collection<E>): Boolean {
    val result = super.removeAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun retainAll(elements: Collection<E>): Boolean {
    val result = super.retainAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun removeIf(filter: Predicate<in E>): Boolean {
    val result = super.removeIf(filter)
    callForOutsideUpdate()
    return result
  }

  override fun removeAt(index: Int): E {
    val result = super.removeAt(index)
    callForOutsideUpdate()
    return result
  }

  override fun set(index: Int, element: E): E {
    val result = super.set(index, element)
    callForOutsideUpdate()
    return result
  }

  override fun replaceAll(operator: UnaryOperator<E>) {
    super.replaceAll(operator)
    callForOutsideUpdate()
  }

  fun setModificationUpdateAction(updater: (value: List<E>) -> Unit) {
    updateAction = updater
  }

  private fun callForOutsideUpdate() {
    updateAction.invoke(this)
  }
}

fun <T> Collection<T>.toMutableWorkspaceList(): MutableWorkspaceList<T> {
  return MutableWorkspaceList(this)
}

/**
 * [MutableIterable.removeAll] and [MutableIterator.remove]  aren't update indexes for now
 */
class MutableWorkspaceSet<E>(collection: Collection<E>) : LinkedHashSet<E>(collection) {
  private var updateAction: ((Set<E>) -> Unit)? = null

  override fun add(element: E): Boolean {
    val result = super.add(element)
    callForOutsideUpdate()
    return result
  }

  override fun addAll(elements: Collection<E>): Boolean {
    val result = super.addAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun clear() {
    super.clear()
    callForOutsideUpdate()
  }

  override fun remove(element: E): Boolean {
    val result = super.remove(element)
    callForOutsideUpdate()
    return result
  }

  override fun removeAll(elements: Collection<E>): Boolean {
    val result = super.removeAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun retainAll(elements: Collection<E>): Boolean {
    val result = super.retainAll(elements)
    callForOutsideUpdate()
    return result
  }

  override fun removeIf(filter: Predicate<in E>): Boolean {
    val result = super.removeIf(filter)
    callForOutsideUpdate()
    return result
  }

  fun setModificationUpdateAction(updater: (value: Set<E>) -> Unit) {
    updateAction = updater
  }

  private fun callForOutsideUpdate() {
    updateAction?.invoke(this)
  }
}

fun <T> Collection<T>.toMutableWorkspaceSet(): MutableWorkspaceSet<T> {
  return MutableWorkspaceSet(this)
}