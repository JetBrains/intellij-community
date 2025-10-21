// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.jetbrains.annotations.NonNls
import org.junit.jupiter.api.extension.ExtensionContext
import org.junit.jupiter.api.extension.ExtensionContext.Namespace
import org.junit.jupiter.api.extension.ExtensionContext.Namespace.GLOBAL
import org.junit.jupiter.api.extension.ExtensionContext.Store

/**
 * [com.intellij.openapi.util.Key]-like tool but for [ExtensionContext]
 * Create with ctor and use [ExtensionContext] extension methods
 */
class TypedStoreKey<T : Any>(private val dataType: Class<T>) {

  override fun toString(): String = "TypedStoreKey(dataType=$dataType)"

  companion object {

    inline fun <reified T : Any> createKey(): TypedStoreKey<T> =
      TypedStoreKey(T::class.java)

    operator fun <T : Any> ExtensionContext.get(key: TypedStoreKey<T>): T? =
      get(GLOBAL, key)

    operator fun <T : Any> ExtensionContext.set(key: TypedStoreKey<T>, data: T): Unit =
      set(GLOBAL, key, data)

    fun <T : Any> ExtensionContext.computeIfAbsent(key: TypedStoreKey<T>, create: () -> T): T =
      computeIfAbsent(GLOBAL, key, create)

    fun <T : Any> ExtensionContext.remove(key: TypedStoreKey<T>): T? =
      remove(GLOBAL, key)

    operator fun <T : Any> ExtensionContext.get(namespace: Namespace, key: TypedStoreKey<T>): T? =
      getStore(namespace).get(key, key.dataType)

    operator fun <T : Any> ExtensionContext.set(namespace: Namespace, key: TypedStoreKey<T>, data: T): Unit =
      getStore(namespace).put(key, data)

    fun <T : Any> ExtensionContext.computeIfAbsent(namespace: Namespace, key: TypedStoreKey<T>, create: () -> T): T =
      getStore(namespace).getOrComputeIfAbsent(key, { create() }, key.dataType)

    fun <T : Any> ExtensionContext.remove(namespace: Namespace, key: TypedStoreKey<T>): T? =
      getStore(namespace).remove(key, key.dataType)

    @Suppress("unused", "UNCHECKED_CAST")
    @Deprecated("Use TypedStoreKey#createKey instead")
    fun <T : Any> createList(key: @NonNls String): TypedStoreKey<ArrayList<T>> =
      TypedStoreKey(ArrayList::class.java as Class<ArrayList<T>>)

    @Deprecated("Use ExtensionContext#get with TypedStoreKey instead")
    fun <T : Any> Store.getTyped(key: TypedStoreKey<T>): T? = get(key, key.dataType)

    @Deprecated("Use ExtensionContext#put with TypedStoreKey instead")
    fun <T : Any> Store.putTyped(key: TypedStoreKey<T>, data: T): Unit = put(key, data)

    @Deprecated("Use ExtensionContext#computeIfAbsent with TypedStoreKey instead")
    fun <T : Any> Store.computeIfAbsentTyped(key: TypedStoreKey<T>, create: () -> T): T =
      getOrComputeIfAbsent(key, { create() }, key.dataType)
  }
}

