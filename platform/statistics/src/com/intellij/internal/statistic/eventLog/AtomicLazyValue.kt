// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic.eventLog

import java.util.concurrent.atomic.AtomicReference

/**
 * Atomic reference that will be initialized on first usage.
 *
 * @param init A callback used to get initial value.
 */
class AtomicLazyValue<T>(private val init: () -> T) {
  private var value: AtomicReference<T?> = AtomicReference(null)

  fun getValue(): T {
    return updateAndGet { prevValue -> prevValue }
  }

  /**
   * @see java.util.concurrent.atomic.AtomicReference.updateAndGet
   */
  @Suppress("UNCHECKED_CAST")
  fun updateAndGet(update: (prevValue: T) -> T): T {
    return value.updateAndGet { refPrevValue -> update(refPrevValue ?: init()) } as T
  }
}