// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "LoggingSimilarMessage")

package com.intellij.util.xmlb

import com.intellij.openapi.util.JDOMUtil
import com.intellij.serialization.ClassUtil
import com.intellij.util.ArrayUtil
import com.intellij.util.ReflectionUtil
import com.intellij.util.xmlb.annotations.MapAnnotation
import com.intellij.util.xmlb.annotations.XMap
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jdom.Element
import java.lang.reflect.ParameterizedType
import java.lang.reflect.Type
import java.util.Arrays
import java.util.Collections
import java.util.TreeMap

internal class MapBinding(
  private val oldAnnotation: MapAnnotation?,
  private val annotation: XMap?,
  private val mapClass: Class<out Map<*, *>>,
  override val isSurroundWithTag: Boolean,
) : MultiNodeBinding, RootBinding {
  private var keyClass: Class<*>? = null
  private var valueClass: Class<*>? = null

  private var keyBinding: Binding? = null
  private var valueBinding: Binding? = null

  private val isSurroundKey = annotation == null && (oldAnnotation == null || oldAnnotation.surroundKeyWithTag)

  override fun init(originalType: Type, serializer: Serializer) {
    val type = originalType as ParameterizedType
    val typeArguments = type.actualTypeArguments

    keyClass = ClassUtil.typeToClass(typeArguments[0])
    val valueType: Type
    if (typeArguments.size == 1) {
      val typeName = type.rawType.typeName
      if (typeName == "it.unimi.dsi.fastutil.objects.Object2IntMap" || typeName == "it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap") {
        valueType = Int::class.java
      }
      else {
        throw UnsupportedOperationException("Value class is unknown for ${type.typeName}")
      }
    }
    else {
      valueType = typeArguments[1]
    }
    valueClass = ClassUtil.typeToClass(valueType)
    keyBinding = serializer.getBinding(aClass = keyClass!!, type = typeArguments[0])
    valueBinding = serializer.getBinding(aClass = valueClass!!, type = valueType)
  }

  override val isMulti: Boolean
    get() = true

  private fun isSortMap(map: Map<*, *>): Boolean {
    // for new XMap we do not sort LinkedHashMap
    if (map is TreeMap<*, *> || (annotation != null && map is LinkedHashMap<*, *>)) {
      return false
    }
    return oldAnnotation == null || oldAnnotation.sortBeforeSave
  }

  override fun toJson(bean: Any, filter: SerializationFilter?): JsonElement {
    val map = bean as Map<*, *>

    if (map.isEmpty()) {
      return JsonObject(emptyMap())
    }

    val dataKeys = ArrayUtil.toObjectArray(map.keys)
    if (isSortMap(map)) {
      Arrays.sort(dataKeys, KEY_COMPARATOR)
    }

    val keys = arrayOfNulls<Any>(dataKeys.size)
    val values = arrayOfNulls<JsonElement>(keys.size)
    var size = 0
    var hasComplexKey = false
    for (dataKey in dataKeys) {
      val serializedKey = keyOrValueToJson(value = dataKey, binding = keyBinding, filter = filter)
      val serializedValue = keyOrValueToJson(value = map.get(dataKey), binding = valueBinding, filter = filter)
      if (!hasComplexKey) {
        hasComplexKey = serializedKey !is JsonPrimitive
      }

      keys[size] = serializedKey
      values[size] = serializedValue ?: JsonNull
      size++
    }

    return createMapElement(hasComplexKey = hasComplexKey, keys = keys, values = values, size = size)
  }

  override fun fromJson(currentValue: Any?, element: JsonElement): Any? {
    if (element === JsonNull) {
      return null
    }

    if (element !is JsonObject && element !is JsonArray) {
      LOG.warn("Expected JsonObject or JsonArray but got $element")
      return currentValue
    }

    // if accessor is null, it is a sub-map, and we must not use context
    var map: MutableMap<Any?, Any?>? = null
    if (currentValue != null) {
      if ((element is JsonObject && element.isEmpty()) || (element is JsonArray && element.isEmpty())) {
        return currentValue
      }
      else if (ClassUtil.isMutableMap(currentValue as Map<*, *>)) {
        @Suppress("UNCHECKED_CAST")
        map = currentValue as MutableMap<Any?, Any?>
        map.clear()
      }
    }

    if (map == null) {
      if (mapClass === java.util.Map::class.java) {
        map = HashMap<Any?, Any?>()
      }
      else {
        try {
          @Suppress("UNCHECKED_CAST")
          map = ReflectionUtil.newInstance(mapClass) as MutableMap<Any?, Any?>?
        }
        catch (e: Exception) {
          LOG.warn(e)
          map = HashMap<Any?, Any?>()
        }
      }
    }

    if (element is JsonObject) {
      for ((key, value) in element) {
        val deserializedKey = XmlSerializerImpl.convert(key, keyClass!!)
        val deserializedValue = keyOrValueFromJson(element = value, binding = valueBinding, valueClass = valueClass!!)
        map!!.put(deserializedKey, deserializedValue)
      }
    }
    else {
      for (entry in (element as JsonArray)) {
        entry as JsonObject
        map!!.put(keyOrValueFromJson(element = entry.get("key")!!, binding = keyBinding, valueClass = keyClass!!),
                  keyOrValueFromJson(element = entry.get("value")!!, binding = valueBinding, valueClass = valueClass!!))
      }
    }
    return map
  }

  override fun serialize(bean: Any, filter: SerializationFilter?): Element? {
    throw IllegalStateException("Do not use MapBinding as a root bean")
  }

  override fun serialize(bean: Any, parent: Element, filter: SerializationFilter?) {
    val serialized: Element
    if (isSurroundWithTag) {
      serialized = Element(Constants.MAP)
      parent.addContent(serialized)
    }
    else {
      serialized = parent
    }

    val map = bean as Map<*, *>
    val keys = ArrayUtil.toObjectArray(map.keys)
    if (isSortMap(map)) {
      Arrays.sort(keys, KEY_COMPARATOR)
    }

    for (k in keys) {
      val entry = Element(entryElementName)
      serialized.addContent(entry)

      serializeKeyOrValue(entry, keyAttributeName, k, keyBinding, filter)
      serializeKeyOrValue(entry, valueAttributeName, map.get(k), valueBinding, filter)
    }
  }

  private val entryElementName: String
    get() {
      if (annotation != null) {
        return annotation.entryTagName
      }
      return oldAnnotation?.entryTagName ?: Constants.ENTRY
    }

  private val keyAttributeName: String
    get() = annotation?.keyAttributeName ?: oldAnnotation?.keyAttributeName ?: Constants.KEY

  private val valueAttributeName: String
    get() = annotation?.valueAttributeName ?: oldAnnotation?.valueAttributeName ?: Constants.VALUE

  override fun <T : Any> deserializeList(currentValue: Any?, elements: List<T>, adapter: DomAdapter<T>): Any? {
    return deserializeMap(currentValue = currentValue, childNodes = if (isSurroundWithTag) adapter.getChildren(elements.single()) else elements, adapter = adapter)
  }

  override fun <T : Any> deserialize(context: Any?, element: T, adapter: DomAdapter<T>): Any? = null

  fun deserialize(context: Any?, element: Element): Any? {
    if (isSurroundWithTag) {
      return deserializeMap(currentValue = context, childNodes = element.children, adapter = JdomAdapter)
    }
    else {
      return deserializeMap(currentValue = context, childNodes = listOf(element), adapter = JdomAdapter)
    }
  }

  private fun <T : Any> deserializeMap(currentValue: Any?, childNodes: List<T>, adapter: DomAdapter<T>): Any? {
    var mutableMap: MutableMap<Any?, Any?>? = null
    if (currentValue != null) {
      @Suppress("UNCHECKED_CAST")
      if (childNodes.isEmpty()) {
        return currentValue
      }
      else if (ClassUtil.isMutableMap(currentValue as Map<Any?, Any?>)) {
        mutableMap = currentValue as MutableMap<Any?, Any?>
        mutableMap.clear()
      }
    }

    for (childNode in childNodes) {
      if (adapter.getName(childNode) != entryElementName) {
        LOG.warn("unexpected entry for serialized Map will be skipped: $childNode")
        continue
      }

      if (mutableMap == null) {
        if (mapClass == MutableMap::class.java) {
          mutableMap = HashMap<Any?, Any?>()
        }
        else {
          try {
            @Suppress("UNCHECKED_CAST")
            mutableMap = ReflectionUtil.newInstance(mapClass) as MutableMap<Any?, Any?>
          }
          catch (e: Exception) {
            LOG.warn(e)
            mutableMap = HashMap<Any?, Any?>()
          }
        }
      }

      mutableMap!!.put(
        deserializeKeyOrValue(entry = childNode, attributeName = keyAttributeName, context = currentValue, binding = keyBinding, valueClass = keyClass!!, adapter = adapter),
        deserializeKeyOrValue(entry = childNode, attributeName = valueAttributeName, context = currentValue, binding = valueBinding, valueClass = valueClass!!, adapter = adapter),
      )
    }
    return mutableMap
  }

  override fun deserializeToJson(element: Element): JsonElement {
    return doDeserializeListToJson(elements = if (isSurroundWithTag) element.children else listOf(element))
  }

  override fun doDeserializeListToJson(elements: List<Element>): JsonElement {
    val keys = arrayOfNulls<Any>(elements.size)
    val values = arrayOfNulls<JsonElement>(keys.size)
    var i = 0
    var hasComplexKey = false
    for (childNode in elements) {
      if (childNode.name != entryElementName) {
        LOG.warn("unexpected entry for serialized Map will be skipped: $childNode")
        continue
      }

      val serializedKey = deserializeKeyOrValueToJson(entry = childNode, attributeName = keyAttributeName, binding = keyBinding, valueClass = keyClass!!)
      keys[i] = serializedKey
      values[i] = deserializeKeyOrValueToJson(entry = childNode, attributeName = valueAttributeName, binding = valueBinding, valueClass = valueClass!!)
      i++

      if (!hasComplexKey) {
        hasComplexKey = serializedKey !is JsonPrimitive
      }
    }
    return createMapElement(hasComplexKey = hasComplexKey, keys = keys, values = values, size = i)
  }

  private fun deserializeKeyOrValueToJson(entry: Element, attributeName: String, binding: Binding?, valueClass: Class<*>): JsonElement? {
    val attribute = entry.getAttributeValue(attributeName)
    if (attribute != null) {
      return valueToJson(attribute, valueClass)
    }
    else if (!isSurroundKey) {
      checkNotNull(binding)
      for (element in entry.children) {
        if (binding.isBoundTo(element, JdomAdapter)) {
          return (binding as RootBinding).deserializeToJson(element)
        }
      }
      LOG.warn("Cannot find binding for ${JDOMUtil.write(entry)}")
      return null
    }
    else {
      val entryChild = entry.getChild(attributeName)
      val elements = entryChild?.children ?: Collections.emptyList()
      return when {
        elements.isEmpty() -> JsonNull
        binding is MultiNodeBinding -> binding.deserializeListToJson(elements)
        elements.size == 1 -> (binding as RootBinding).deserializeToJson(elements.get(0))
        else -> throw UnsupportedOperationException("Unsupported binding: $binding")
      }
    }
  }

  private fun serializeKeyOrValue(entry: Element, attributeName: String, value: Any?, binding: Binding?, filter: SerializationFilter?) {
    if (value == null) {
      return
    }

    if (binding == null) {
      entry.setAttribute(attributeName, JDOMUtil.removeControlChars(XmlSerializerImpl.convertToString(value)))
    }
    else {
      val container: Element
      if (isSurroundKey) {
        container = Element(attributeName)
        entry.addContent(container)
      }
      else {
        container = entry
      }
      binding.serialize(bean = value, parent = container, filter = filter)
    }
  }

  private fun <T : Any> deserializeKeyOrValue(entry: T, attributeName: String, context: Any?, binding: Binding?, valueClass: Class<*>, adapter: DomAdapter<T>): Any? {
    val attribute = adapter.getAttributeValue(entry, attributeName)
    if (attribute != null) {
      return XmlSerializerImpl.convert(attribute, valueClass)
    }
    else if (!isSurroundKey) {
      checkNotNull(binding)
      for (element in adapter.getChildren(entry)) {
        if (binding.isBoundTo(element, adapter)) {
          return binding.deserialize(context, element, adapter)
        }
      }
    }
    else {
      val entryChild = adapter.getChild(entry, attributeName)
      val children = if (entryChild == null) Collections.emptyList() else adapter.getChildren(entryChild)
      if (children.isEmpty()) {
        return null
      }
      else {
        return deserializeList(binding = binding!!, currentValue = null, elements = children, adapter = adapter)
      }
    }
    return null
  }

  fun isBoundToWithoutProperty(elementName: String): Boolean {
    return when {
      annotation != null -> elementName == annotation.entryTagName
      oldAnnotation != null && !oldAnnotation.surroundWithTag -> elementName == oldAnnotation.entryTagName
      else -> elementName == Constants.MAP
    }
  }

  override fun <T : Any> isBoundTo(element: T, adapter: DomAdapter<T>): Boolean {
    return when {
      oldAnnotation != null && !oldAnnotation.surroundWithTag -> oldAnnotation.entryTagName == adapter.getName(element)
      annotation != null -> annotation.propertyElementName == adapter.getName(element)
      else -> adapter.getName(element) == Constants.MAP
    }
  }
}

private val KEY_COMPARATOR = Comparator { o1: Any?, o2: Any? ->
  if (o1 is Comparable<*> && o2 is Comparable<*>) {
    @Suppress("UNCHECKED_CAST")
    (o1 as Comparable<Any?>).compareTo(o2 as Comparable<Any?>)
  }
  else {
    0
  }
}

private fun keyOrValueToJson(value: Any?, binding: Binding?, filter: SerializationFilter?): JsonElement? {
  if (value == null) {
    return null
  }

  return if (binding == null) {
    primitiveToJsonElement(value)
  }
  else {
    binding.toJson(value, filter)
  }
}

private fun keyOrValueFromJson(element: JsonElement, binding: Binding?, valueClass: Class<*>): Any? {
  if (binding == null) {
    return fromJsonPrimitive(data = element, valueClass = valueClass)
  }
  else {
    return (binding as RootBinding).fromJson(currentValue = null, element = element)
  }
}

private fun createMapElement(hasComplexKey: Boolean, keys: Array<Any?>, values: Array<JsonElement?>, size: Int): JsonElement {
  if (hasComplexKey) {
    return JsonArray(Array(keys.size) { index ->
      // don't use here Map.of - map must be ordered
      JsonObject(Object2ObjectArrayMap(arrayOf("key", "value"), arrayOf(keys[index] as JsonElement, values[index]!!)))
    }.asList())
  }
  else {
    for (i in 0 until size) {
      keys[i] = (keys[i] as JsonPrimitive).content
    }
    return JsonObject(Object2ObjectArrayMap(keys, values, size))
  }
}

internal fun <T : Any> deserializeList(binding: Binding, currentValue: Any?, elements: List<T>, adapter: DomAdapter<T>): Any? {
  return when {
    binding is MultiNodeBinding -> binding.deserializeList(currentValue = currentValue, elements = elements, adapter = adapter)
    elements.size == 1 -> binding.deserialize(context = currentValue, element = elements.get(0), adapter = adapter)
    elements.isEmpty() -> null
    else -> throw AssertionError("Duplicate data for $binding will be ignored")
  }
}