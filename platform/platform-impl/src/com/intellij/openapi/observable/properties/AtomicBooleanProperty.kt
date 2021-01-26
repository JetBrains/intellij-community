// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicReference

class AtomicBooleanProperty(initial: Boolean) : BooleanProperty, AtomicProperty<Boolean> {

  private val value = AtomicReference(initial)

  private val changeListeners = CopyOnWriteArrayList<(Boolean) -> Unit>()
  private val setListeners = CopyOnWriteArrayList<() -> Unit>()
  private val resetListeners = CopyOnWriteArrayList<() -> Unit>()

  override fun set() = set(true)

  override fun reset() = set(false)

  override fun set(value: Boolean) {
    val oldValue = this.value.getAndSet(value)
    submitChangeEvents(oldValue, value)
  }

  override fun updateAndGet(update: (Boolean) -> Boolean): Boolean {
    var oldValue: Boolean? = null
    val newValue = value.updateAndGet {
      oldValue = it
      update(it)
    }
    submitChangeEvents(oldValue!!, newValue)
    return newValue
  }

  fun compareAndSet(expect: Boolean, update: Boolean): Boolean {
    val succeed = value.compareAndSet(expect, update)
    if (succeed) {
      submitChangeEvents(expect, update)
    }
    return succeed
  }

  private fun submitChangeEvents(oldValue: Boolean, newValue: Boolean) {
    when {
      !oldValue && newValue -> setListeners.forEach { it() }
      oldValue && !newValue -> resetListeners.forEach { it() }
    }
    changeListeners.forEach { it(newValue) }
  }

  override fun get(): Boolean = value.get()

  override fun afterChange(listener: (Boolean) -> Unit) {
    changeListeners.add(listener)
  }

  override fun afterSet(listener: () -> Unit) {
    setListeners.add(listener)
  }

  override fun afterReset(listener: () -> Unit) {
    resetListeners.add(listener)
  }

  fun afterSet(listener: () -> Unit, parentDisposable: Disposable) {
    setListeners.add(listener)
    Disposer.register(parentDisposable, Disposable { setListeners.remove(listener) })
  }

  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable) {
    resetListeners.add(listener)
    Disposer.register(parentDisposable, Disposable { resetListeners.remove(listener) })
  }

  override fun afterChange(listener: (Boolean) -> Unit, parentDisposable: Disposable) {
    changeListeners.add(listener)
    Disposer.register(parentDisposable, Disposable { changeListeners.remove(listener) })
  }
}