// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.util.RecursionManager
import com.intellij.util.ObjectUtils
import java.util.concurrent.atomic.AtomicReference

/**
 * Same as [SafePublicationLazyImpl], but doesn't cache value in case of computation recursion occurred.
 */
class RecursionAwareSafePublicationLazy<T>(initializer: () -> T) : Lazy<T> {

  @Volatile
  private var initializer: (() -> T)? = initializer
  @Suppress("UNCHECKED_CAST")
  private val valueRef: AtomicReference<T> = AtomicReference(UNINITIALIZED_VALUE as T)

  override val value: T
    get() {
      val computedValue = valueRef.get()
      if (computedValue !== UNINITIALIZED_VALUE) {
        return computedValue
      }

      val initializerValue = initializer
      if (initializerValue === null) {
        // Some thread managed to clear the initializer => it managed to set the value.
        return valueRef.get()
      }

      val stamp = ourRecursionGuard.markStack()
      val newValue = initializerValue()
      if (!stamp.mayCacheNow()) {
        // In case of recursion don't update [valueRef] and don't clear [initializer].
        return newValue
      }

      if (!valueRef.compareAndSet(UNINITIALIZED_VALUE, newValue)) {
        // Some thread managed to set the value.
        return valueRef.get()
      }

      initializer = null
      return newValue
    }

  override fun isInitialized(): Boolean = valueRef.get() !== UNINITIALIZED_VALUE

  override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."

  companion object {
    private val ourRecursionGuard = RecursionManager.createGuard("RecursionAwareSafePublicationLazy")
    private val UNINITIALIZED_VALUE: Any = ObjectUtils.sentinel("RecursionAwareSafePublicationLazy initial value")
  }
}
