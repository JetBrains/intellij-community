// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.github.util

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer

class DisposalCountingHolder<T : Any>(private val valueFactory: (Disposable) -> T) : Disposable {

  private var valueAndDisposable: Pair<T, Disposable>? = null
  private var disposalCounter = 0

  val value: T?
    get() = valueAndDisposable?.first

  fun acquireValue(disposable: Disposable): T {
    if (Disposer.isDisposed(this)) error("Already disposed")

    if (valueAndDisposable == null) {
      valueAndDisposable = Disposer.newDisposable().let {
        valueFactory(it) to it
      }
    }

    disposalCounter++
    Disposer.register(disposable, Disposable {
      disposalCounter--
      if (disposalCounter <= 0) {
        disposeValue()
      }
    })
    return value!!
  }

  private fun disposeValue() {
    valueAndDisposable?.let { Disposer.dispose(it.second) }
    valueAndDisposable = null
  }

  override fun dispose() {
    disposeValue()
  }
}