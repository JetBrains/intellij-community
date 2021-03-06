// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.util.ObjectUtils
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicReference

class SafePublicationClearableLazyValue<T>(private val initializer: () -> T) {

  @Suppress("UNCHECKED_CAST")
  private val valueRef: AtomicReference<T> = AtomicReference(UNINITIALIZED_VALUE as T)

  val value: T
    get() {
      val computedValue = valueRef.get()
      if (computedValue !== UNINITIALIZED_VALUE) {
        return computedValue
      }
      val newValue = initializer()
      if (valueRef.compareAndSet(UNINITIALIZED_VALUE, newValue)) {
        return newValue
      }
      else {
        return valueRef.get()
      }
    }

  @Suppress("UNCHECKED_CAST")
  fun clear(): Unit = valueRef.set(UNINITIALIZED_VALUE as T)

  @NonNls
  override fun toString(): String = if (valueRef.get() !== UNINITIALIZED_VALUE) value.toString() else "Lazy value not initialized."

  companion object {
    private val UNINITIALIZED_VALUE: Any = ObjectUtils.sentinel("SafePublicationClearableLazyValue initial value")
  }
}
