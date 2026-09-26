// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import fleet.util.multiplatform.Actual

@Actual
internal fun <T> threadLocalImplJs(supplier: () -> T): ThreadLocalKmp<T> = threadLocal(supplier)

private fun <T> threadLocal(supplier: () -> T) = object : ThreadLocalKmp<T> {
  var value: T? = null

  override fun get(): T = value ?: supplier().also { value = it }

  override fun remove() {
    value = null
  }

  override fun set(value: T) {
    this.value = value
  }
}
