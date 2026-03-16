// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import fleet.util.multiplatform.Actual

@Actual
internal fun <T> threadLocalImplNative(supplier: () -> T): ThreadLocalKmp<T> = NativeThreadLocal(supplier)

@kotlin.native.concurrent.ThreadLocal
private val threadLocals = mutableMapOf<NativeThreadLocal<*>, Any?>()

private class NativeThreadLocal<T>(private val supplier: () -> T) : ThreadLocalKmp<T> {
  init {
    threadLocals[this] = supplier() as Any?
  }

  override fun get(): T {
    return threadLocals[this] as T
  }

  override fun remove() {
    threadLocals[this] = supplier() as Any?
  }

  override fun set(value: T) {
    threadLocals[this] = value
  }
}