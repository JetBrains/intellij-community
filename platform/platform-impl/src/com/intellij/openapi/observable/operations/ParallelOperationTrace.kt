// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.operations

import com.intellij.openapi.Disposable
import java.util.*


interface ParallelOperationTrace {

  fun isOperationCompleted(): Boolean

  fun subscribe(listener: Listener)

  fun subscribe(listener: Listener, parentDisposable: Disposable)

  interface Listener : EventListener {
    @JvmDefault
    fun onOperationStart() {
    }

    @JvmDefault
    fun onOperationFinish() {
    }
  }
}