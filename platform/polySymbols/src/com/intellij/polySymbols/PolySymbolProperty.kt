// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

import org.jetbrains.annotations.ApiStatus

@ApiStatus.NonExtendable
interface PolySymbolProperty<T : Any> {

  val name: String

  fun tryCast(value: Any?): T?

  companion object {
    @JvmStatic
    inline operator fun <reified T : Any> get(name: String): PolySymbolProperty<T> =
      get(name, T::class.java)

    @JvmStatic
    operator fun <T : Any> get(name: String, type: Class<T>): PolySymbolProperty<T> =
      PolySymbolPropertyData(name, type)
  }
}

private class PolySymbolPropertyData<T : Any>(override val name: String, private val type: Class<T>) : PolySymbolProperty<T> {

  @Suppress("UNCHECKED_CAST")
  override fun tryCast(value: Any?): T? =
    if (value != null && this.type.isInstance(value)) value as T? else null

  override fun equals(other: Any?): Boolean =
    other === this ||
    other is PolySymbolPropertyData<*>
    && other.name == name
    && other.type == type

  override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + type.hashCode()
    return result
  }

  override fun toString(): String = name

}