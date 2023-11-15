// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.CheckedDisposable
import com.intellij.openapi.util.Disposer

class DisposalCountingHolder<T : Any>(private val valueFactory: (CheckedDisposable) -> T) : Disposable {

  private var valueAndDisposable: Pair<T, CheckedDisposable>? = null
  private var disposalCounter = 0

  @get:Synchronized
  val value: T? get() = valueAndDisposable?.first

  @Synchronized
  fun acquireValue(disposable: Disposable): T {
    if (Disposer.isDisposed(this)) error("Already disposed")

    val current = valueAndDisposable
    val value = if (current == null) {
      val newDisposable = Disposer.newCheckedDisposable()
      val newValue = valueFactory(newDisposable)
      valueAndDisposable = newValue to newDisposable
      newValue
    }
    else {
      current.first
    }

    disposalCounter++
    Disposer.register(disposable, Disposable {
      disposalCounter--
      if (disposalCounter <= 0) {
        disposeValue()
      }
    })
    return value
  }

  @Synchronized
  private fun disposeValue() {
    valueAndDisposable?.let { Disposer.dispose(it.second) }
    valueAndDisposable = null
  }

  override fun dispose() {
    disposeValue()
  }
}