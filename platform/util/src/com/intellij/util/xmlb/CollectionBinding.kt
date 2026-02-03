// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.xmlb

import com.intellij.openapi.util.JDOMUtil
import com.intellij.serialization.ClassUtil
import com.intellij.serialization.MutableAccessor
import com.intellij.util.ArrayUtilRt
import com.intellij.util.SmartList
import com.intellij.util.xmlb.annotations.Transient
import com.intellij.util.xmlb.annotations.XCollection
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Text
import java.lang.reflect.Type
import java.util.Collections
import java.util.SortedSet

internal fun createCollectionBinding(serializer: Serializer, itemType: Class<*>, accessor: MutableAccessor?, isArray: Boolean): RootBinding {
  var isSurroundWithTag = true
  var isSortOrderedSet = true
  var elementTypes = ArrayUtilRt.EMPTY_CLASS_ARRAY
  var elementName = Constants.OPTION
  var valueAttributeName = Constants.VALUE
  if (accessor != null) {
    val xCollection = accessor.getAnnotation(XCollection::class.java)
    if (xCollection != null) {
      isSurroundWithTag = false
      elementTypes = XmlSerializerUtil.getElementTypes(xCollection)
      elementName = xCollection.elementName
      valueAttributeName = xCollection.valueAttributeName
    }
    else {
      @Suppress("DEPRECATION")
      val oldAnnotation = accessor.getAnnotation(com.intellij.util.xmlb.annotations.AbstractCollection::class.java)
      if (oldAnnotation != null) {
        isSurroundWithTag = oldAnnotation.surroundWithTag
        isSortOrderedSet = oldAnnotation.sortOrderedSet
        elementTypes = XmlSerializerUtil.getElementTypes(oldAnnotation)
        elementName = oldAnnotation.elementTag
        valueAttributeName = oldAnnotation.elementValueAttribute
      }
    }
  }
  return CollectionBinding(
    elementName = elementName,
    valueAttributeName = valueAttributeName,
    isSurroundWithTag = isSurroundWithTag,
    isSortOrderedSet = isSortOrderedSet,
    itemType = itemType,
    elementTypes = elementTypes,
    serializer = serializer,
    strategy = if (isArray) ArrayStrategy else CollectionStrategyImpl,
  )
}

internal class CollectionBinding(
  private val elementName: String,
  private val valueAttributeName: String,
  override val isSurroundWithTag: Boolean,
  @JvmField internal val isSortOrderedSet: Boolean,
  @JvmField internal val itemType: Class<*>,
  @JvmField internal val elementTypes: Array<Class<*>>,
  private val serializer: Serializer,
  private val strategy: CollectionStrategy,
) : MultiNodeBinding, RootBinding {
  private var itemBindings: List<Binding>? = null

  override fun init(originalType: Type, serializer: Serializer) {
    assert(itemBindings == null)

    val binding = getItemBinding(itemType, serializer)
    val elementTypes = elementTypes
    if (elementTypes.isEmpty()) {
      itemBindings = if (binding == null) emptyList() else listOf(binding)
    }
    else {
      val itemBindings: MutableList<Binding>
      if (binding == null) {
        itemBindings = if (elementTypes.size == 1) SmartList() else ArrayList(elementTypes.size)
      }
      else {
        itemBindings = ArrayList(elementTypes.size + 1)
        itemBindings.add(binding)
      }

      for (aClass in elementTypes) {
        val b = getItemBinding(aClass, serializer)
        if (b != null && !itemBindings.contains(b)) {
          itemBindings.add(b)
        }
      }

      this.itemBindings = if (itemBindings.isEmpty()) emptyList() else itemBindings
    }
  }

  override val isMulti: Boolean
    get() = true

  private fun getItemBinding(aClass: Class<*>, serializer: Serializer): Binding? {
    // element types maybe specified explicitly, in this case do not try to resolve binding for a class that marked as transient (to avoid warning "no accessors")
    return if (ClassUtil.isPrimitive(aClass) || aClass.isAnnotationPresent(Transient::class.java)) null else serializer.getRootBinding(aClass, aClass)
  }

  override fun toJson(bean: Any, filter: SerializationFilter?): JsonArray {
    val collection = strategy.getCollection(bean, this)
    if (collection.isEmpty()) {
      return JsonArray(emptyList())
    }

    val content = ArrayList<JsonElement>(collection.size)
    val includeClassDiscriminator = itemBindings!!.size > 1
    for (value in collection) {
      if (value == null) {
        content.add(JsonNull)
        continue
      }

      when (val binding = getItemBinding(value.javaClass, serializer)) {
        null -> content.add(primitiveToJsonElement(value))
        is BeanBinding -> content.add(binding.toJson(bean = value, filter = filter, includeClassDiscriminator = includeClassDiscriminator))
        else -> content.add(binding.toJson(value, filter) ?: JsonNull)
      }
    }
    return JsonArray(content)
  }

  override fun fromJson(currentValue: Any?, element: JsonElement): Any? {
    var collection = currentValue?.let { strategy.getCollection(it, this) }
    if (element == JsonNull) {
      return null
    }

    if (element !is JsonArray) {
      LOG.warn("Expected JsonArray but got $element")
      return collection
    }

    val isMutable = collection != null && ClassUtil.isMutableCollection(collection)
    if (isMutable) {
      (collection as MutableCollection).clear()
    }
    else {
      collection = if (collection is Set<*>) HashSet() else ArrayList()
    }

    collection as MutableCollection

    val itemBindings = itemBindings!!
    val size = itemBindings.size
    when (size) {
      0 -> {
        for (itemElement in element) {
          collection.add(fromJsonPrimitive(data = itemElement, valueClass = itemType)?.takeIf { it != Unit })
        }
      }
      1 -> {
        val binding = itemBindings[0]
        for (itemElement in element) {
          val o = (binding as RootBinding).fromJson(currentValue = null, element = itemElement)
          assert(o !== Unit)
          collection.add(o)
        }
      }
      else -> {
        for (itemElement in element) {
          val b = itemBindings.firstOrNull { it is BeanBinding && it.tagName == (itemElement as? JsonObject)?.get(JSON_CLASS_DISCRIMINATOR_KEY)?.jsonPrimitive?.content }
          if (b == null) {
            LOG.warn("Cannot find binding for $itemElement")
          }
          else {
            val o = (b as BeanBinding).fromJson(currentValue = null, element = itemElement)
            assert(o !== Unit)
            collection.add(o)
          }
        }
      }
    }

    return strategy.transformJsonValue(collection, itemType)
  }

  override fun serialize(bean: Any, filter: SerializationFilter?): Element {
    val tagName = if (isSurroundWithTag) strategy.getCollectionTagName(bean) else null
    requireNotNull(tagName) { "Do not use CollectionBinding as a root bean" }
    val result = Element(true, tagName, Namespace.NO_NAMESPACE)
    val collection = strategy.getCollection(bean, this)
    if (!collection.isEmpty()) {
      for (item in collection) {
        serializeItem(item, result, filter)
      }
    }
    return result
  }

  override fun serialize(bean: Any, parent: Element, filter: SerializationFilter?) {
    val tagName = if (isSurroundWithTag) strategy.getCollectionTagName(bean) else null
    val listElement: Element
    if (tagName == null) {
      listElement = parent
    }
    else {
      listElement = Element(true, tagName, Namespace.NO_NAMESPACE)
      parent.addContent(listElement)
    }

    val collection = strategy.getCollection(bean, this)
    if (!collection.isEmpty()) {
      for (item in collection) {
        serializeItem(value = item, parent = listElement, filter = filter)
      }
    }
  }

  override fun deserializeToJson(element: Element): JsonElement {
    return doDeserializeListToJson(if (isSurroundWithTag) element.children.single().children else element.children)
  }

  override fun doDeserializeListToJson(elements: List<Element>): JsonElement {
    val jsonElements = ArrayList<JsonElement>(elements.size)
    for (child in elements) {
      val adapter = JdomAdapter
      val binding = itemBindings!!.firstOrNull { it.isBoundTo(child, adapter) }
      when (binding) {
        null -> {
          val attributeName = valueAttributeName
          val value = if (attributeName.isEmpty()) JdomAdapter.getTextValue(child, "") else JdomAdapter.getAttributeValue(child, attributeName)
          jsonElements.add(valueToJson(value, itemType) ?: JsonNull)
        }
        is BeanBinding -> {
          jsonElements.add(binding.deserializeToJson(element = child, includeClassDiscriminator = itemBindings!!.size > 1))
        }
        else -> {
          jsonElements.add((binding as RootBinding).deserializeToJson(child) ?: JsonNull)
        }
      }
    }
    return JsonArray(jsonElements)
  }

  override fun <T : Any> deserialize(context: Any?, element: T, adapter: DomAdapter<T>): Any {
    return strategy.deserializeList(
      currentValue = context,
      elements = if (isSurroundWithTag) adapter.getChildren(element) else listOf(element),
      adapter = adapter,
      binding = this,
    )
  }

  override fun <T : Any> deserializeList(currentValue: Any?, elements: List<T>, adapter: DomAdapter<T>): Any {
    if (!isSurroundWithTag) {
      return strategy.deserializeList(currentValue = currentValue, elements = elements, adapter = adapter, binding = this)
    }

    val element = elements.single()
    return strategy.deserializeList(
      currentValue = if (currentValue == null && adapter.getName(element) == Constants.SET) HashSet<Any>() else currentValue,
      elements = adapter.getChildren(element),
      adapter = adapter,
      binding = this,
    )
  }

  private fun serializeItem(value: Any?, parent: Element, filter: SerializationFilter?) {
    if (value == null) {
      LOG.warn("Collection contains 'null' object")
      return
    }

    val binding = getItemBinding(aClass = value.javaClass, serializer = serializer)
    if (binding == null) {
      val elementName = elementName
      if (elementName.isEmpty()) {
        throw Error("elementName must be not empty")
      }

      val serializedItem = Element(true, elementName, Namespace.NO_NAMESPACE)
      val attributeName = valueAttributeName
      val serialized = XmlSerializerImpl.convertToString(value)
      if (attributeName.isEmpty()) {
        if (!serialized.isEmpty()) {
          serializedItem.addContent(Text(true, serialized))
        }
      }
      else {
        serializedItem.setAttribute(Attribute(true, attributeName, JDOMUtil.removeControlChars(serialized), Namespace.NO_NAMESPACE))
      }
      parent.addContent(serializedItem)
    }
    else {
      binding.serialize(value, parent, filter)
    }
  }

  internal fun <T : Any> deserializeItem(node: T, adapter: DomAdapter<T>, bean: Any?): Any? {
    val binding = itemBindings!!.firstOrNull { it.isBoundTo(node, adapter) }
    if (binding == null) {
      val attributeName = valueAttributeName
      val value = if (attributeName.isEmpty()) adapter.getTextValue(node, "") else adapter.getAttributeValue(node, attributeName)
      return XmlSerializerImpl.convert(value, itemType)
    }
    else {
      return binding.deserialize(bean, node, adapter)
    }
  }

  override fun <T : Any> isBoundTo(element: T, adapter: DomAdapter<T>): Boolean {
    return when {
      isSurroundWithTag -> adapter.getName(element) == strategy.getCollectionTagName(null)
      itemBindings!!.isEmpty() -> adapter.getName(element) == elementName
      else -> itemBindings!!.any { it.isBoundTo(element, adapter) }
    }
  }
}

internal sealed interface CollectionStrategy {
  fun getCollectionTagName(target: Any?): String

  fun <T : Any> deserializeList(currentValue: Any?, elements: List<T>, adapter: DomAdapter<T>, binding: CollectionBinding): Any

  fun getCollection(bean: Any, binding: CollectionBinding): Collection<Any?>

  fun transformJsonValue(value: Collection<Any?>, itemType: Class<*>): Any = value
}

private data object CollectionStrategyImpl : CollectionStrategy {
  override fun <T : Any> deserializeList(currentValue: Any?, elements: List<T>, adapter: DomAdapter<T>, binding: CollectionBinding): Any {
    val result: MutableCollection<Any?>
    val isContextMutable = currentValue != null && ClassUtil.isMutableCollection(currentValue)
    if (isContextMutable) {
      @Suppress("UNCHECKED_CAST")
      result = currentValue as MutableCollection<Any?>
      result.clear()
    }
    else {
      result = if (currentValue is Set<*>) HashSet() else ArrayList()
    }

    for (node in elements) {
      result.add(binding.deserializeItem(node = node, adapter = adapter, bean = currentValue))
    }

    return result
  }

  override fun getCollection(bean: Any, binding: CollectionBinding): Collection<*> {
    val collection = bean as Collection<*>
    if (collection.size < 2 || ((binding.isSortOrderedSet && bean is LinkedHashSet<*>)) || bean is SortedSet<*>) {
      // no need to sort
      return collection
    }
    else if (bean is Set<*>) {
      val result = bean.toTypedArray()
      result.sort()
      return result.asList()
    }
    else {
      return collection
    }
  }

  override fun getCollectionTagName(target: Any?): String {
    return when (target) {
      is Set<*> -> Constants.SET
      is List<*> -> Constants.LIST
      else -> "collection"
    }
  }
}

private data object ArrayStrategy : CollectionStrategy {
  override fun getCollectionTagName(target: Any?) = "array"

  override fun <T : Any> deserializeList(currentValue: Any?, elements: List<T>, adapter: DomAdapter<T>, binding: CollectionBinding): Any {
    val size = elements.size

    @Suppress("UNCHECKED_CAST")
    val result = java.lang.reflect.Array.newInstance(binding.itemType, size) as Array<Any>
    for (i in 0 until size) {
      result[i] = binding.deserializeItem(elements[i], adapter, currentValue)!!
    }
    return result
  }

  override fun getCollection(bean: Any, binding: CollectionBinding): Collection<Any?> {
    @Suppress("UNCHECKED_CAST")
    val list = bean as Array<Any>
    return if (list.isEmpty()) Collections.emptyList<Any>() else list.asList()
  }

  override fun transformJsonValue(value: Collection<Any?>, itemType: Class<*>): Any {
    @Suppress("PLATFORM_CLASS_MAPPED_TO_KOTLIN", "UNCHECKED_CAST")
    val list = value as java.util.Collection<Any?>

    @Suppress("UNCHECKED_CAST")
    val result = java.lang.reflect.Array.newInstance(itemType, list.size()) as Array<Any>
    return list.toArray(result)
  }
}
