// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.xmlb

import com.intellij.serialization.MutableAccessor
import kotlinx.serialization.json.JsonElement
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.reflect.Type

interface Serializer {
  fun getRootBinding(aClass: Class<*>, originalType: Type): Binding

  fun getRootBinding(aClass: Class<*>): Binding = getRootBinding(aClass = aClass, originalType = aClass)

  fun getBinding(aClass: Class<*>, type: Type): Binding?
}

fun interface SerializationFilter {
  fun accepts(accessor: Accessor, bean: Any): Boolean
}

@Internal
interface RootBinding : Binding {
  fun serialize(bean: Any, filter: SerializationFilter?): Element?

  // currentValue is used in collection binding and is modified in place if it's mutable
  fun fromJson(currentValue: Any?, element: JsonElement): Any?
}

interface Binding {
  fun serialize(bean: Any, parent: Element, filter: SerializationFilter?)

  fun <T : Any> isBoundTo(element: T, adapter: DomAdapter<T>): Boolean

  fun init(originalType: Type, serializer: Serializer) {
  }

  fun <T : Any> deserialize(context: Any?, element: T, adapter: DomAdapter<T>): Any?

  fun toJson(bean: Any, filter: SerializationFilter?): JsonElement?
}

interface NestedBinding : Binding {
  val accessor: MutableAccessor

  fun setFromJson(bean: Any, element: JsonElement)

  // used only by kotlinx serialization
  val propertyName: String
    get() = accessor.name
}

@Internal
interface MultiNodeBinding : Binding {
  val isMulti: Boolean

  fun <T : Any> deserializeList(currentValue: Any?, elements: List<T>, adapter: DomAdapter<T>): Any?
}