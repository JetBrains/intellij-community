/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
@file:JvmName("XmlSerializer")
package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.ComponentSerializationUtil
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.util.JDOMUtil
import com.intellij.reference.SoftReference
import com.intellij.util.xmlb.*
import com.intellij.util.xmlb.XmlSerializerImpl.isPrimitive
import com.intellij.util.xmlb.XmlSerializerImpl.typeToClass
import gnu.trove.THashMap
import org.jdom.Element
import org.jdom.JDOMException
import java.io.IOException
import java.lang.reflect.Type
import java.net.URL
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write
import kotlin.reflect.primaryConstructor

@JvmOverloads
fun <T : Any> T.serialize(filter: SerializationFilter? = SkipDefaultsSerializationFilter()): Element {
  try {
    val clazz = javaClass
    val binding = getBinding(clazz)
    if (binding is BeanBinding) {
      // top level expects not null (null indicates error, empty element will be omitted)
      return binding.serialize(this, true, filter)
    }
    else {
      return binding.serialize(this, null, filter) as Element
    }
  }
  catch (e: XmlSerializationException) {
    throw e
  }
  catch (e: Exception) {
    throw XmlSerializationException("Can't serialize instance of ${this.javaClass}", e)
  }
}

inline fun <reified T: Any> Element.deserialize(): T = deserialize(T::class.java)

fun <T> Element.deserialize(aClass: Class<T>): T {
  @Suppress("UNCHECKED_CAST")
  try {
    return (getBinding(aClass) as NotNullDeserializeBinding).deserialize(null, this) as T
  }
  catch (e: XmlSerializationException) {
    throw e
  }
  catch (e: Exception) {
    throw XmlSerializationException("Cannot deserialize class ${aClass.name}", e)
  }
}

fun <T> deserialize(url: URL, aClass: Class<T>): T {
  try {
    var document = JDOMUtil.loadDocument(url)
    document = JDOMXIncluder.resolve(document, url.toExternalForm())
    return document.rootElement.deserialize(aClass)
  }
  catch (e: IOException) {
    throw XmlSerializationException(e)
  }
  catch (e: JDOMException) {
    throw XmlSerializationException(e)
  }
}

fun Element.deserializeInto(bean: Any) {
  try {
    (getBinding(bean.javaClass) as BeanBinding).deserializeInto(bean, this)
  }
  catch (e: XmlSerializationException) {
    throw e
  }
  catch (e: Exception) {
    throw XmlSerializationException(e)
  }
}

fun PersistentStateComponent<*>.deserializeAndLoadState(element: Element) {
  val state = element.deserialize(ComponentSerializationUtil.getStateClass<Any>(javaClass))
  (state as? BaseState)?.resetModificationCount()
  @Suppress("UNCHECKED_CAST")
  (this as PersistentStateComponent<Any>).loadState(state)
}

fun <T : Any> T.serializeInto(element: Element) {
  try {
    val binding = getBinding(javaClass)
    (binding as BeanBinding).serializeInto(this, element, null)
  }
  catch (e: XmlSerializationException) {
    throw e
  }
  catch (e: Exception) {
    throw XmlSerializationException(e)
  }
}

private var _bindingCache: SoftReference<MutableMap<BindingCacheKey, Binding>>? = null

private val bindingCache: MutableMap<BindingCacheKey, Binding>
  get() {
    var map = _bindingCache?.get()
    if (map == null) {
      map = THashMap()
      _bindingCache = SoftReference(map)
    }
    return map
  }

private val cacheLock = ReentrantReadWriteLock()

private fun <T> getBinding(aClass: Class<T>, originalType: Type = aClass, accessor: MutableAccessor? = null): Binding {
  val key = BindingCacheKey(originalType, accessor)
  val map = bindingCache
  return cacheLock.read { map.get(key) } ?: cacheLock.write {
    map.get(key)?.let {
      return it
    }

    val binding = XmlSerializerImpl.createClassBinding(aClass, accessor, originalType) ?: KotlinAwareBeanBinding(aClass, accessor)
    map.put(key, binding)
    try {
      binding.init(originalType)
    }
    catch (e: XmlSerializationException) {
      map.remove(key)
      throw e
    }
    binding
  }
}

private data class BindingCacheKey(val type: Type, val accessor: MutableAccessor?)

private class KotlinAwareBeanBinding(beanClass: Class<*>, accessor: MutableAccessor? = null) : BeanBinding(beanClass, accessor) {
  override fun deserialize(context: Any?, element: Element): Any {
    val instance = newInstance()
    deserializeInto(instance, element)
    return instance
  }

  override fun getBinding(accessor: MutableAccessor): Binding? {
    val type = accessor.genericType
    return getClassBinding(typeToClass(type), type, accessor)
  }

  private fun newInstance(): Any {
    val clazz = myBeanClass
    try {
      val constructor = clazz.getDeclaredConstructor()
      try {
        constructor.isAccessible = true
      }
      catch (e: SecurityException) {
        return clazz.newInstance()
      }
      return constructor.newInstance()
    }
    catch (e: RuntimeException) {
      return createUsingKotlin(clazz) ?: throw e
    }
    catch (e: NoSuchMethodException) {
      return createUsingKotlin(clazz) ?: throw e
    }
  }

  private fun createUsingKotlin(clazz: Class<*>): Any? {
    // if cannot create data class
    val kClass = clazz.kotlin
    return (kClass.primaryConstructor ?: kClass.constructors.firstOrNull())?.callBy(emptyMap())
  }
}

private fun getClassBinding(clazz: Class<*>, type: Type = clazz, accessor: MutableAccessor? = null) = if (isPrimitive(clazz)) null else getBinding(clazz, type, accessor)