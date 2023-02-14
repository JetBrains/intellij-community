// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.junit.jupiter.api.extension.ExtensionContext

internal inline fun <reified T> ExtensionContext.Store.typedGet(key: String): T {
  return get(key, T::class.java)
}

internal fun <T> ExtensionContext.Store.computeIfAbsent(key: String, computable: () -> T): T {
  @Suppress("UNCHECKED_CAST")
  return getOrComputeIfAbsent(key) {
    computable()
  } as T
}
