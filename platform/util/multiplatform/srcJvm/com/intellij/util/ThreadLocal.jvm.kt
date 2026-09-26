// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import fleet.util.multiplatform.Actual

@Suppress("unused")
@Actual
internal fun <T> threadLocalImplJvm(supplier: () -> T): ThreadLocalKmp<T> = threadLocal(supplier)

private fun <T> threadLocal(supplier: () -> T) = object : ThreadLocalKmp<T> {
  val threadLocal = ThreadLocal.withInitial(supplier)

  override fun get(): T = threadLocal.get()

  override fun remove() = threadLocal.remove()

  override fun set(value: T) = threadLocal.set(value)
}