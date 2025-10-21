// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.junit.jupiter.api.extension.ExtensionContext

@Suppress("unused")
@Deprecated("Use ExtensionContext#get with TypedStoreKey instead")
internal inline fun <reified T> ExtensionContext.Store.typedGet(key: String): T {
  return get(key, T::class.java)
}

@Suppress("unused", "UNCHECKED_CAST")
@Deprecated("Use ExtensionContext#computeIfAbsent with TypedStoreKey instead")
internal fun <T> ExtensionContext.Store.computeIfAbsent(key: String, computable: () -> T): T {
  return getOrComputeIfAbsent(key) {
    computable()
  } as T
}
