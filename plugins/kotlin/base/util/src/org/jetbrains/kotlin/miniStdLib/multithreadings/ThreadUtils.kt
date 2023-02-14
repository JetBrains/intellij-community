// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package org.jetbrains.kotlin.miniStdLib.multithreadings

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@JvmInline
private value class ThreadLocalDelegate<V>(private val threadLocal: ThreadLocal<V>): ReadWriteProperty<Any?, V> {
    override fun getValue(thisRef: Any?, property: KProperty<*>): V = threadLocal.get()
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: V) = threadLocal.set(value)
}

fun <V> threadLocal(initialValue: () -> V): ReadWriteProperty<Any?, V> {
    return ThreadLocalDelegate(ThreadLocal.withInitial(initialValue))
}

fun <V> threadLocal(initialValue: V): ReadWriteProperty<Any?, V> {
    return ThreadLocalDelegate(ThreadLocal.withInitial { initialValue })
}