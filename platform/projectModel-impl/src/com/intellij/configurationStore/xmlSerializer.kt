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
package com.intellij.configurationStore

import com.intellij.openapi.util.JDOMUtil
import com.intellij.reference.SoftReference
import com.intellij.util.xmlb.*
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

fun <T : Any> T.serialize(filter: SerializationFilter? = SkipDefaultValuesSerializationFilters()): Element = XmlSerializer.serialize(this, filter)

inline fun <reified T: Any> Element.deserialize(): T = deserialize(this, T::class.java)

fun <T> deserialize(element: Element, aClass: Class<T>): T {
  @Suppress("UNCHECKED_CAST")
  try {
    return (getBinding(aClass) as NotNullDeserializeBinding).deserialize(null, element) as T
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
    return deserialize(document.rootElement, aClass)
  }
  catch (e: IOException) {
    throw XmlSerializationException(e)
  }
  catch (e: JDOMException) {
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
    deserializeInto(instance, element, null)
    return instance
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
      // if cannot create data class
      val kClass = clazz.kotlin
      (kClass.primaryConstructor ?: kClass.constructors.firstOrNull())?.let {
        return it.callBy(emptyMap())
      }

      throw e
    }
  }
}