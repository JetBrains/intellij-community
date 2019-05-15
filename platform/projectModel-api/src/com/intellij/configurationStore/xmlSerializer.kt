// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("XmlSerializer")
package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.net.URL
import java.util.*

private val serializer: JdomSerializer = ServiceLoader.load<JdomSerializer>(JdomSerializer::class.java).first()

@ApiStatus.Internal
fun getDefaultSerializationFilter() = serializer.getDefaultSerializationFilter()

@JvmOverloads
fun <T : Any> T.serialize(filter: SerializationFilter? = serializer.getDefaultSerializationFilter(), createElementIfEmpty: Boolean = false): Element? {
  return serializer.serialize(this, filter, createElementIfEmpty)
}

inline fun <reified T: Any> Element.deserialize(): T = deserialize(T::class.java)

fun <T> Element.deserialize(clazz: Class<T>): T = serializer.deserialize(this, clazz)

fun Element.deserializeInto(bean: Any) = serializer.deserializeInto(this, bean)

fun <T> deserialize(url: URL, aClass: Class<T>): T = serializer.deserialize(url, aClass)

@JvmOverloads
fun <T> deserializeAndLoadState(component: PersistentStateComponent<T>, element: Element, clazz: Class<T> = ComponentSerializationUtil.getStateClass<T>(component::class.java)) {
  val state = serializer.deserialize(element, clazz)
  (state as? BaseState)?.resetModificationCount()
  component.loadState(state)
}

@JvmOverloads
fun serializeObjectInto(o: Any, target: Element, filter: SerializationFilter? = null) {
  serializer.serializeObjectInto(o, target, filter)
}

fun serializeStateInto(component: PersistentStateComponent<*>, element: Element) {
  component.state?.let {
    serializer.serializeObjectInto(it, element)
  }
}

interface JdomSerializer {
  fun getDefaultSerializationFilter(): SkipDefaultsSerializationFilter

  fun <T : Any> serialize(obj: T, filter: SerializationFilter?, createElementIfEmpty: Boolean = false): Element?

  fun serializeObjectInto(o: Any, target: Element, filter: SerializationFilter? = null)

  fun <T> deserialize(element: Element, clazz: Class<T>): T

  fun deserializeInto(element: Element, bean: Any)

  fun <T> deserialize(url: URL, aClass: Class<T>): T
}