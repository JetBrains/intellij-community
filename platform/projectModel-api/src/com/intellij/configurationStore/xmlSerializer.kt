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

@ApiStatus.Internal
val jdomSerializer: JdomSerializer = ServiceLoader.load(JdomSerializer::class.java, JdomSerializer::class.java.classLoader).first()

@JvmOverloads
fun <T : Any> serialize(obj: T, filter: SerializationFilter? = jdomSerializer.getDefaultSerializationFilter(), createElementIfEmpty: Boolean = false): Element? {
  return jdomSerializer.serialize(obj, filter, createElementIfEmpty)
}

inline fun <reified T: Any> deserialize(element: Element): T = jdomSerializer.deserialize(element, T::class.java)

fun <T> Element.deserialize(clazz: Class<T>): T = jdomSerializer.deserialize(this, clazz)

fun Element.deserializeInto(bean: Any) = jdomSerializer.deserializeInto(bean, this)

@JvmOverloads
fun <T> deserializeAndLoadState(component: PersistentStateComponent<T>, element: Element, clazz: Class<T> = ComponentSerializationUtil.getStateClass<T>(component::class.java)) {
  val state = jdomSerializer.deserialize(element, clazz)
  (state as? BaseState)?.resetModificationCount()
  component.loadState(state)
}

@JvmOverloads
fun serializeObjectInto(o: Any, target: Element, filter: SerializationFilter? = null) {
  jdomSerializer.serializeObjectInto(o, target, filter)
}

fun serializeStateInto(component: PersistentStateComponent<*>, element: Element) {
  component.state?.let {
    jdomSerializer.serializeObjectInto(it, element)
  }
}

interface JdomSerializer {
  fun <T : Any> serialize(obj: T, filter: SerializationFilter?, createElementIfEmpty: Boolean = false): Element?

  fun serializeObjectInto(obj: Any, target: Element, filter: SerializationFilter? = null)

  fun <T> deserialize(element: Element, clazz: Class<T>): T

  fun deserializeInto(obj: Any, element: Element)

  fun <T> deserialize(url: URL, aClass: Class<T>): T

  @ApiStatus.Internal
  fun getDefaultSerializationFilter(): SkipDefaultsSerializationFilter

  fun clearSerializationCaches()
}