// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util

import com.intellij.openapi.util.RecursionManager
import java.util.concurrent.atomic.AtomicReference

/**
 * Same as [SafePublicationLazyImpl], but returns `null` in case of computation recursion occurred.
 */
internal class RecursionPreventingSafePublicationLazy<T>(recursionKey: Any?, initializer: () -> T) : Lazy<T?> {

  @Volatile
  private var initializer: (() -> T)? = { ourNotNullizer.notNullize(initializer()) }
  private val valueRef: AtomicReference<T?> = AtomicReference()
  private val recursionKey: Any = recursionKey ?: this

  override val value: T?
    get() {
      val computedValue = valueRef.get()
      if (computedValue !== null) {
        return ourNotNullizer.nullize(computedValue)
      }

      val initializerValue = initializer
      if (initializerValue === null) {
        // Some thread managed to clear the initializer => it managed to set the value.
        return ourNotNullizer.nullize(requireNotNull(valueRef.get()))
      }

      val stamp = RecursionManager.markStack()
      val newValue = ourRecursionGuard.doPreventingRecursion(recursionKey, false, initializerValue)
      // In case of recursion don't update [valueRef] and don't clear [initializer].
      if (newValue === null) {
        // Recursion occurred for this lazy.
        return null
      }
      if (!stamp.mayCacheNow()) {
        // Recursion occurred somewhere deep.
        return ourNotNullizer.nullize(newValue)
      }

      if (!valueRef.compareAndSet(null, newValue)) {
        // Some thread managed to set the value.
        return ourNotNullizer.nullize(requireNotNull(valueRef.get()))
      }

      initializer = null
      return ourNotNullizer.nullize(newValue)
    }

  override fun isInitialized(): Boolean = valueRef.get() !== null

  override fun toString(): String = if (isInitialized()) value.toString() else "Lazy value not initialized yet."

  companion object {
    private val ourRecursionGuard = RecursionManager.createGuard<Any>("RecursionPreventingSafePublicationLazy")
    private val ourNotNullizer = NotNullizer("RecursionPreventingSafePublicationLazy")
  }
}
