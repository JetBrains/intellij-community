// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.cce.workspace.storages

interface KeyValueStorage<T, V> {
  fun get(key: T): V
  fun getKeys(): List<T>
  fun save(baseKey: T, value: V): T
}