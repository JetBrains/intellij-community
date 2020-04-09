// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

open class AtomicLazyProperty<T>(private val initial: () -> T) : AtomicProperty<T> {

  private val value = AtomicReference<Any?>(UNINITIALIZED_VALUE)

  private val changeListeners = CopyOnWriteArrayList<(T) -> Unit>()
  private val resetListeners = CopyOnWriteArrayList<() -> Unit>()

  override fun get(): T {
    return update { it }
  }

  override fun set(value: T) {
    this.value.set(value)
    changeListeners.forEach { it(value) }
  }

  override fun updateAndGet(update: (T) -> T): T {
    val newValue = update(update)
    changeListeners.forEach { it(newValue) }
    return newValue
  }

  @Suppress("UNCHECKED_CAST")
  private fun update(update: (T) -> T): T {
    val resolve = { it: Any? -> if (it === UNINITIALIZED_VALUE) initial() else it as T }
    return value.updateAndGet { update(resolve(it)) } as T
  }

  override fun reset() {
    value.set(UNINITIALIZED_VALUE)
    resetListeners.forEach { it() }
  }

  override fun afterChange(listener: (T) -> Unit) {
    changeListeners.add(listener)
  }

  override fun afterReset(listener: () -> Unit) {
    resetListeners.add(listener)
  }

  override fun afterChange(listener: (T) -> Unit, parentDisposable: Disposable) {
    changeListeners.add(listener)
    Disposer.register(parentDisposable, Disposable { changeListeners.remove(listener) })
  }

  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) {
    resetListeners.add(listener)
    Disposer.register(parentDisposable, Disposable { resetListeners.remove(listener) })
  }

  companion object {
    private val UNINITIALIZED_VALUE = Any()
  }
}