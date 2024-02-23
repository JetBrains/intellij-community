// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.xmlb

import com.intellij.serialization.MutableAccessor
import com.intellij.util.xml.dom.XmlElement
import kotlinx.serialization.json.JsonElement
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.reflect.Type

interface Serializer {
  fun getRootBinding(aClass: Class<*>, originalType: Type): Binding

  fun getRootBinding(aClass: Class<*>): Binding = getRootBinding(aClass = aClass, originalType = aClass)

  fun getBinding(accessor: MutableAccessor): Binding?

  fun getBinding(aClass: Class<*>, type: Type): Binding?
}

fun interface SerializationFilter {
  fun accepts(accessor: Accessor, bean: Any): Boolean
}

@Internal
interface RootBinding : Binding {
  fun serialize(bean: Any, filter: SerializationFilter?): Element?
}

interface Binding {
  fun serialize(bean: Any, parent: Element, filter: SerializationFilter?)

  fun isBoundTo(element: Element): Boolean

  fun isBoundTo(element: XmlElement): Boolean

  fun init(originalType: Type, serializer: Serializer) {
  }

  fun deserializeUnsafe(context: Any?, element: Element): Any?

  fun deserializeUnsafe(context: Any?, element: XmlElement): Any?

  fun toJson(bean: Any, filter: SerializationFilter?): JsonElement?

  fun fromJson(bean: Any?, element: JsonElement): Any?
}

interface NestedBinding : Binding {
  val accessor: MutableAccessor

  // used only by kotlinx serialization
  val propertyName: String
    get() = accessor.name
}

@ApiStatus.Internal
interface MultiNodeBinding : Binding {
  val isMulti: Boolean

  fun deserializeJdomList(context: Any?, elements: List<Element>): Any?

  fun deserializeList(context: Any?, elements: List<XmlElement>): Any?
}

interface NotNullDeserializeBinding : Binding {
  fun deserialize(context: Any?, element: Element): Any

  fun deserialize(context: Any?, element: XmlElement): Any

  override fun deserializeUnsafe(context: Any?, element: Element): Any = deserialize(context = context, element = element)

  override fun deserializeUnsafe(context: Any?, element: XmlElement): Any = deserialize(context = context, element = element)
}