// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.testFramework.junit5.impl

import org.jetbrains.annotations.NonNls
import org.junit.jupiter.api.extension.ExtensionContext.Store
import kotlin.reflect.KClass

/**
 * [com.intellij.openapi.util.Key]-like tool but for [org.junit.jupiter.api.extension.ExtensionContext]
 * Create with ctor or [createList] and use [org.junit.jupiter.api.extension.ExtensionContext] extension methods
 */
class TypedStoreKey<T : Any>(private val key: @NonNls String, private val dataType: KClass<T>) {
  companion object {

    fun <T : Any> createList(key: @NonNls String): TypedStoreKey<ArrayList<T>> {
      @Suppress("UNCHECKED_CAST")
      return TypedStoreKey(key, ArrayList::class as KClass<ArrayList<T>>)
    }

    fun <T : Any> Store.putTyped(key: TypedStoreKey<T>, data: T) {
      put(key.key, data)
    }

    fun <T : Any> Store.computeIfAbsentTyped(key: TypedStoreKey<T>, create: () -> T): T =
      computeIfAbsent(key.key, create)

    fun <T : Any> Store.getTyped(key: TypedStoreKey<T>): T? = get(key.key, key.dataType.java)
  }

  override fun toString(): String = "TypedStoreKey(key='$key', dataType=$dataType)"
}

