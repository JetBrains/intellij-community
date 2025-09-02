// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.xdebugger.impl.util

import fleet.multiplatform.shims.ConcurrentHashMap

internal class IdentityWrapper<T>(val obj: T) {
  override fun equals(other: Any?): Boolean {
    return other is IdentityWrapper<T> && obj === other.obj
  }

  override fun hashCode(): Int {
    return System.identityHashCode(obj)
  }
}

internal fun <K> K.identityWrapper() = IdentityWrapper(this)
internal fun <K, V> identityConcurrentHashMap() = ConcurrentHashMap<IdentityWrapper<K>, V>()
