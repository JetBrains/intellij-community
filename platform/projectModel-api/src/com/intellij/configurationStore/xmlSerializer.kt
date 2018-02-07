/*
 * Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */
@file:JvmName("XmlSerializer")
package com.intellij.configurationStore

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.PersistentStateComponent
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
import kotlin.reflect.full.primaryConstructor
import kotlin.reflect.jvm.isAccessible

private val skipDefaultsSerializationFilter = ThreadLocal<SoftReference<SerializationFilter>>()

private fun getDefaultSerializationFilter(): SerializationFilter {
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
    (serializer.getClassBinding(bean.javaClass) as BeanBinding).deserializeInto(bean, this)
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

fun PersistentStateComponent<*>.serializeStateInto(element: Element) {
  state?.let { serializeObjectInto(it, element) }
}

fun serializeObjectInto(o: Any, target: Element) {
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

  val binding = serializer.getClassBinding(o.javaClass)
  (binding as BeanBinding).serializeInto(o, target, if (o is BaseState) null else getDefaultSerializationFilter())
}

private val serializer = object : XmlSerializerImpl.XmlSerializerBase() {
  @Suppress("ObjectPropertyName")
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

  override fun getClassBinding(aClass: Class<*>, originalType: Type, accessor: MutableAccessor?): Binding {
    val key = BindingCacheKey(originalType, accessor)
    val map = bindingCache
    return cacheLock.read { map.get(key) } ?: cacheLock.write {
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
}

private data class BindingCacheKey(val type: Type, val accessor: MutableAccessor?)

private class KotlinAwareBeanBinding(beanClass: Class<*>, accessor: MutableAccessor? = null) : BeanBinding(beanClass, accessor) {
  override fun deserialize(context: Any?, element: Element): Any {
    val instance = newInstance()
    deserializeInto(instance, element)
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
      return createUsingKotlin(clazz) ?: throw e
    }
    catch (e: NoSuchMethodException) {
      return createUsingKotlin(clazz) ?: throw e
    }
  }

  private fun createUsingKotlin(clazz: Class<*>): Any? {
    // if cannot create data class
    val kClass = clazz.kotlin
    val kFunction = kClass.primaryConstructor ?: kClass.constructors.first()
    try {
      kFunction.isAccessible = true
    }
    catch (e: SecurityException) {
    }
    return kFunction.callBy(emptyMap())
  }
}