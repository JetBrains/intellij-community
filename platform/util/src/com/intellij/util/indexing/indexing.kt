// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing

import com.intellij.openapi.util.ThrowableNotNullFunction
import org.jetbrains.annotations.ApiStatus

/**
 * Helper functions for index access
 */
@ApiStatus.Internal
fun <K, V> InvertedIndex<K, V, *>.forEachValueOf(key: K & Any, action: ValueContainer.ContainerAction<V>) {
  withData(key, ThrowableNotNullFunction { container -> container.forEach(action) })
}
