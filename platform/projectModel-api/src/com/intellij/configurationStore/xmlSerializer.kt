// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("XmlSerializer")
package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.util.xmlb.BeanBinding
import com.intellij.util.xmlb.DomAdapter
import com.intellij.util.xmlb.JdomAdapter
import com.intellij.util.xmlb.SerializationFilter
import com.intellij.util.xmlb.SkipDefaultsSerializationFilter
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType
import java.net.URL

@ApiStatus.Internal
val jdomSerializer: JdomSerializer = run {
  val implClass = JdomSerializer::class.java.classLoader.loadClass("com.intellij.configurationStore.JdomSerializerImpl")
  MethodHandles.lookup().findConstructor(implClass, MethodType.methodType(Void.TYPE)).invoke() as JdomSerializer
}

@JvmOverloads
fun <T : Any> serialize(
  bean: T,
  filter: SerializationFilter? = jdomSerializer.getDefaultSerializationFilter(),
  createElementIfEmpty: Boolean = false,
): Element? {
  return jdomSerializer.serialize(bean = bean, filter = filter, createElementIfEmpty = createElementIfEmpty)
}

inline fun <reified T: Any> deserialize(element: Element): T = jdomSerializer.deserialize(element, T::class.java, JdomAdapter)

fun <T> Element.deserialize(clazz: Class<T>): T = jdomSerializer.deserialize(this, clazz, JdomAdapter)

fun Element.deserializeInto(bean: Any) {
  jdomSerializer.deserializeInto(obj = bean, element = this)
}

@ApiStatus.Internal
@JvmOverloads
fun <T : Any> deserializeAndLoadState(
  component: PersistentStateComponent<T>,
  element: Element,
  clazz: Class<T> = ComponentSerializationUtil.getStateClass(component::class.java),
) {
  val state = jdomSerializer.deserialize(element, clazz, JdomAdapter)
  (state as? BaseState)?.resetModificationCount()
  component.loadState(state)
}

@JvmOverloads
fun serializeObjectInto(o: Any, target: Element, filter: SerializationFilter? = null) {
  jdomSerializer.serializeObjectInto(o, target, filter)
}

@ApiStatus.Internal
fun serializeStateInto(component: PersistentStateComponent<*>, element: Element) {
  component.state?.let {
    jdomSerializer.serializeObjectInto(it, element)
  }
}

@ApiStatus.Internal
interface JdomSerializer {
  fun <T : Any> serialize(bean: T, filter: SerializationFilter?, createElementIfEmpty: Boolean = false): Element?

  fun serializeObjectInto(obj: Any, target: Element, filter: SerializationFilter? = null)

  fun <T, E : Any> deserialize(element: E, clazz: Class<T>, adapter: DomAdapter<E>): T

  fun deserializeInto(obj: Any, element: Element)

  fun <T> deserialize(url: URL, aClass: Class<T>): T

  @ApiStatus.Internal
  fun getDefaultSerializationFilter(): SkipDefaultsSerializationFilter

  fun clearSerializationCaches()

  fun <T> getBeanBinding(aClass: Class<T>): BeanBinding
}