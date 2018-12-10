// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.plugins.groovy.util

import com.intellij.openapi.util.RecursionManager
import java.util.concurrent.atomic.AtomicReference

/**
 * Same as [SafePublicationLazyImpl], but returns `null` in case of computation recursion occurred.
 */
class RecursionPreventingSafePublicationLazy<T : Any>(initializer: () -> T) : Lazy<T?> {

  @Volatile
  private var initializer: (() -> T)? = initializer
  private val valueRef: AtomicReference<T> = AtomicReference()

  override val value: T?
    get() {
      val computedValue = valueRef.get()
      if (computedValue !== null) {
        return computedValue
      }

      val initializerValue = initializer
      if (initializerValue === null) {
        // Some thread managed to clear the initializer => it managed to set the value.
        return valueRef.get()
      }

      val stamp = ourRecursionGuard.markStack()
      val newValue = ourRecursionGuard.doPreventingRecursion(this, false, initializerValue)
      if (newValue === null || !stamp.mayCacheNow()) {
        // In case of recursion don't update [valueRef] and don't clear [initializer].
        return null
      }

      if (!valueRef.compareAndSet(null, newValue)) {
        // Some thread managed to set the value.
        return valueRef.get()
      }

      initializer = null
      return newValue
    }

  override fun isInitialized(): Boolean = valueRef.get() !== null

  override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."

  companion object {
    private val ourRecursionGuard = RecursionManager.createGuard("RecursionPreventingSafePublicationLazy")
  }
}
