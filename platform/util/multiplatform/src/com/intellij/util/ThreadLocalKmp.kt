// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util

import fleet.util.multiplatform.linkToActual

interface ThreadLocalKmp<T> {
  fun get(): T
  fun remove()
  fun set(value: T)
}

fun <T> ThreadLocalKmp(): ThreadLocalKmp<T?> = ThreadLocalKmp { null }

fun <T> ThreadLocalKmp(supplier: () -> T): ThreadLocalKmp<T> = threadLocalImpl(supplier)

@Suppress("unused")
internal fun <T> threadLocalImpl(supplier: () -> T): ThreadLocalKmp<T> = linkToActual()