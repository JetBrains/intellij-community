// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.polySymbols

abstract class PolySymbolProperty<T : Any>(
  val name: String,
  type: Class<T>,
) {

  val type: Class<T> = if (type.isPrimitive) type.kotlin.javaObjectType else type

  @Suppress("UNCHECKED_CAST")
  fun tryCast(value: Any?): T? =
    if (value != null && this.type.isInstance(value)) value as T? else null

  final override fun equals(other: Any?): Boolean =
    other === this ||
    other is PolySymbolPropertyData<*>
    && other.name == name
    && other.type == type

  final override fun hashCode(): Int {
    var result = name.hashCode()
    result = 31 * result + type.hashCode()
    return result
  }

  override fun toString(): String = name + ": " + type.simpleName + " (" + javaClass.simpleName + ")"

  companion object {
    @JvmStatic
    inline operator fun <reified T : Any> get(name: String): PolySymbolProperty<T> =
      get(name, T::class.java)

    @JvmStatic
    operator fun <T : Any> get(name: String, type: Class<T>): PolySymbolProperty<T> =
      PolySymbolPropertyData(name, type)
  }
}

private class PolySymbolPropertyData<T : Any>(name: String, type: Class<T>) : PolySymbolProperty<T>(name, type) {

  override fun toString(): String = name + ": " + type.simpleName

}