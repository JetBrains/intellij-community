// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.editor.state

/**
 * Computes the default of a [StateProperty]. It runs on the calling thread, so it must not take the RW lock.
 */
fun interface SyncDefaultValueCalculator<T> {
  fun calculate(): T
}

/**
 * A default that never changes.
 */
class FixedDefaultValue<T>(private val value: T) : SyncDefaultValueCalculator<T> {
  override fun calculate(): T {
    return value
  }
}

/**
 * Changes the value of a [StateProperty] before the property stores it. It applies to the override and to the default.
 */
fun interface CustomOutValueModifier<T> {
  fun modifyOutValue(calculated: T): T
}
