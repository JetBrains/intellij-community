// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
@file:Internal

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

interface RootBinding : Binding {
  fun serialize(bean: Any, filter: SerializationFilter?): Element?

  // currentValue is used in collection binding and is modified in place if it's mutable
  fun fromJson(currentValue: Any?, element: JsonElement): Any?

  fun deserializeToJson(element: Element): JsonElement?
}

interface Binding {
  fun serialize(bean: Any, parent: Element, filter: SerializationFilter?)

  @Internal
  fun <T : Any> isBoundTo(element: T, adapter: DomAdapter<T>): Boolean

  fun init(originalType: Type, serializer: Serializer) {
  }

  @Internal
  fun <T : Any> deserialize(context: Any?, element: T, adapter: DomAdapter<T>): Any?

  fun toJson(bean: Any, filter: SerializationFilter?): JsonElement?
}

@Internal
interface NestedBinding : Binding {
  val accessor: MutableAccessor

  fun setFromJson(bean: Any, element: JsonElement)

  fun deserializeToJson(element: Element): JsonElement?

  // used only by kotlinx serialization
  val propertyName: String
    get() = accessor.name
}

internal interface MultiNodeBinding : Binding {
  val isMulti: Boolean
  val isSurroundWithTag: Boolean

  fun <T : Any> deserializeList(currentValue: Any?, elements: List<T>, adapter: DomAdapter<T>): Any?

  fun deserializeListToJson(elements: List<Element>): JsonElement {
    return doDeserializeListToJson(elements = if (isSurroundWithTag) elements.single().children else elements)
  }

  fun doDeserializeListToJson(elements: List<Element>): JsonElement
}

@RequiresOptIn(level = RequiresOptIn.Level.ERROR)
@Target(
  AnnotationTarget.CLASS,
  AnnotationTarget.TYPEALIAS,
  AnnotationTarget.FUNCTION,
  AnnotationTarget.PROPERTY,
  AnnotationTarget.FIELD,
  AnnotationTarget.CONSTRUCTOR
)
annotation class SettingsInternalApi