package com.intellij.cce.workspace.storages

interface KeyValueStorage<T, V> {
  fun get(key: T): V
  fun getKeys(): List<T>
  fun save(baseKey: T, value: V): T
}