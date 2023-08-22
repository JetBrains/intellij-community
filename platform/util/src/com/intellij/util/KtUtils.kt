// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("KotlinUtils")

package com.intellij.util

import org.jetbrains.annotations.ApiStatus.Experimental
import java.util.concurrent.atomic.AtomicReference
import kotlin.reflect.KProperty

@Suppress("INVISIBLE_REFERENCE", "INVISIBLE_MEMBER")
inline fun <reified T : Any> Any?.asSafely(): @kotlin.internal.NoInfer T? = this as? T

inline fun <T> runIf(condition: Boolean, block: () -> T): T? = if (condition) block() else null

inline fun <T : Any> T?.alsoIfNull(block: () -> Unit): T? {
  if (this == null) {
    block()
  }
  return this
}

inline fun <T> T.applyIf(condition: Boolean, body: T.() -> T): T =
  if (condition) body() else this

typealias AsyncSupplier<T> = suspend () -> T

operator fun <V> AtomicReference<V>.getValue(thisRef: Any?, property: KProperty<*>): V = get()

operator fun <V> AtomicReference<V>.setValue(thisRef: Any?, property: KProperty<*>, value: V): Unit = set(value)

@Experimental
fun <T> Sequence<T>.multiple(): Boolean {
  with(iterator()) {
    if (!hasNext()) return false
    next()
    return hasNext()
  }
}
