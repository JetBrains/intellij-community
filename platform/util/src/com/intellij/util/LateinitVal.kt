// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/**
 * Provides `lateinit val` implementation (which is not allowed by Kotlin) as a property delegate.
 * Not thread-safe.
 *
 * Usage:
 * ```
 * var x: String by lateinitVal()
 * // or var x by lateinitVal<String>()
 * println(x) // throws because unassigned
 * x = "abc"
 * println(x)
 * x = "def"  // throws because already assigned
 * ```
 *
 * @return a delegate to emulate `lateinit val` property
 */
fun <T> lateinitVal(): ReadWriteProperty<Any?, T> = LateinitVal()

private val UNASSIGNED = Any()

private class LateinitVal<T> : ReadWriteProperty<Any?, T> {

  @Suppress("UNCHECKED_CAST")
  private var myValue: T = UNASSIGNED as T

  override fun getValue(thisRef: Any?, property: KProperty<*>): T {
    val v = myValue
    if (v === UNASSIGNED) {
      throw IllegalStateException("value must be assigned")
    }
    else {
      return v
    }
  }

  override fun setValue(thisRef: Any?, property: KProperty<*>, value: T) {
    if (myValue === UNASSIGNED) {
      myValue = value
    }
    else {
      throw IllegalStateException("value can be assigned only once")
    }
  }
}
