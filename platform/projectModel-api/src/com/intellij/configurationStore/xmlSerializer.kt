// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("XmlSerializer")
package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.util.JDOMUtil
import com.intellij.reference.SoftReference
import com.intellij.util.io.URLUtil
import com.intellij.util.xmlb.*
import gnu.trove.THashMap
import org.jdom.Element
import org.jdom.JDOMException
import org.jetbrains.annotations.ApiStatus
import java.io.IOException
import java.lang.reflect.Type
import java.net.URL
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

private val skipDefaultsSerializationFilter = ThreadLocal<SoftReference<SkipDefaultsSerializationFilter>>()

@ApiStatus.Internal
fun getDefaultSerializationFilter(): SkipDefaultsSerializationFilter {
  var result = SoftReference.dereference(skipDefaultsSerializationFilter.get())
  if (result == null) {
    result = object : SkipDefaultsSerializationFilter() {
      override fun accepts(accessor: Accessor, bean: Any): Boolean {
        return if (bean is BaseState) {
          bean.accepts(accessor, bean)
        }
        else {
          super.accepts(accessor, bean)
        }
      }
    }
    skipDefaultsSerializationFilter.set(SoftReference(result))
  }
  return result
}

@JvmOverloads
fun <T : Any> T.serialize(filter: SerializationFilter? = getDefaultSerializationFilter(), createElementIfEmpty: Boolean = false): Element? {
  try {
    val clazz = javaClass
    val binding = serializer.getClassBinding(clazz)
    return if (binding is BeanBinding) {
      // top level expects not null (null indicates error, empty element will be omitted)
      binding.serialize(this, createElementIfEmpty, filter)
    }
    else {
      binding.serialize(this, null, filter) as Element
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

fun <T> Element.deserialize(clazz: Class<T>): T {
  if (clazz == Element::class.java) {
    @Suppress("UNCHECKED_CAST")
    return this as T
  }

  @Suppress("UNCHECKED_CAST")
  try {
    return (serializer.getClassBinding(clazz) as NotNullDeserializeBinding).deserialize(null, this) as T
  }
  catch (e: XmlSerializationException) {
    throw e
  }
  catch (e: Exception) {
    throw XmlSerializationException("Cannot deserialize class ${clazz.name}", e)
  }
}

fun <T> deserialize(url: URL, aClass: Class<T>): T {
  try {
    @Suppress("DEPRECATION")
    var document = JDOMUtil.loadDocument(URLUtil.openStream(url))
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
    (serializer.getClassBinding(bean.javaClass) as BeanBinding).deserializeInto(bean, this)
  }
  catch (e: XmlSerializationException) {
    throw e
  }
  catch (e: Exception) {
    throw XmlSerializationException(e)
  }
}

@JvmOverloads
fun <T> deserializeAndLoadState(component: PersistentStateComponent<T>, element: Element, clazz: Class<T> = ComponentSerializationUtil.getStateClass<T>(component::class.java)) {
  val state = element.deserialize(clazz)
  (state as? BaseState)?.resetModificationCount()
  component.loadState(state)
}

fun serializeStateInto(component: PersistentStateComponent<*>, element: Element) {
  component.state?.let {
    serializeObjectInto(it, element)
  }
}

@JvmOverloads
fun serializeObjectInto(o: Any, target: Element, filter: SerializationFilter? = null) {
  if (o is Element) {
    val iterator = o.children.iterator()
    for (child in iterator) {
      iterator.remove()
      target.addContent(child)
    }

    val attributeIterator = o.attributes.iterator()
    for (attribute in attributeIterator) {
      attributeIterator.remove()
      target.setAttribute(attribute)
    }
    return
  }

  val beanBinding = serializer.getClassBinding(o.javaClass) as KotlinAwareBeanBinding
  beanBinding.serializeInto(o, target, filter ?: getDefaultSerializationFilter())
}

private val serializer = object : XmlSerializerImpl.XmlSerializerBase() {
  private var bindingCache: SoftReference<MutableMap<BindingCacheKey, Binding>>? = null

  private fun getOrCreateBindingCache(): MutableMap<BindingCacheKey, Binding> {
    var map = bindingCache?.get()
    if (map == null) {
      map = THashMap()
      bindingCache = SoftReference(map)
    }
    return map
  }

  private val cacheLock = ReentrantReadWriteLock()

  override fun getClassBinding(aClass: Class<*>, originalType: Type, accessor: MutableAccessor?): Binding {
    val key = BindingCacheKey(originalType, accessor)
    return cacheLock.read {
      // create _bindingCache only under write lock
      bindingCache?.get()?.get(key)
    } ?: cacheLock.write {
      val map = getOrCreateBindingCache()
      map.get(key)?.let {
        return it
      }

      val binding = createClassBinding(aClass, accessor, originalType) ?: KotlinAwareBeanBinding(aClass, accessor)
      map.put(key, binding)
      try {
        binding.init(originalType, this)
      }
      catch (e: RuntimeException) {
        map.remove(key)
        throw e
      }
      catch (e: Error) {
        map.remove(key)
        throw e
      }
      binding
    }
  }

  @Suppress("unused")
  fun clearBindingCache() {
    cacheLock.write {
      bindingCache?.clear()
    }
  }
}

/**
 * used by MPS. Do not use if not approved.
 */
fun clearBindingCache() {
  serializer.clearBindingCache()
}

private data class BindingCacheKey(val type: Type, val accessor: MutableAccessor?)