// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import java.util.concurrent.atomic.AtomicReference

class AtomicLazyValue<T>(private val init: () -> T) {
  private var value: AtomicReference<T?> = AtomicReference(null)

  fun getValue(): T {
    return updateAndGet { it }
  }

  @Suppress("UNCHECKED_CAST")
  fun updateAndGet(compute: (prevValue: T) -> T): T {
    return value.updateAndGet { compute(getPrevValue(it)) } as T
  }

  @Suppress("UNCHECKED_CAST")
  private fun getPrevValue(prevValue: Any?): T = if (prevValue == null) init() else prevValue as T
}