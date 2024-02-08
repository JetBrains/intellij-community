// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.xmlb

import com.intellij.serialization.MutableAccessor
import com.intellij.serialization.PropertyCollector
import com.intellij.util.ThreeState
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xmlb.NotNullDeserializeBinding.LOG
import com.intellij.util.xmlb.XmlSerializerUtil.getAccessors
import com.intellij.util.xmlb.annotations.*
import it.unimi.dsi.fastutil.objects.Object2FloatMap
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap
import org.jdom.Attribute
import org.jdom.Element
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus
import java.lang.reflect.AccessibleObject
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Type
import java.util.*

private val PROPERTY_COLLECTOR = XmlSerializerPropertyCollector(MyPropertyCollectorConfiguration())

fun getBeanAccessors(aClass: Class<*>): List<MutableAccessor> = PROPERTY_COLLECTOR.collect(aClass)

@ApiStatus.Internal
open class BeanBinding(@JvmField val beanClass: Class<*>) : NotNullDeserializeBinding() {
  private val tagName: String
  @JvmField
  var bindings: Array<NestedBinding>? = null

  @JvmField
  var compareByFields: ThreeState = ThreeState.UNSURE

  init {
    assert(!beanClass.isArray) { "Bean is an array: $beanClass" }
    assert(!beanClass.isPrimitive) { "Bean is primitive type: $beanClass" }
    tagName = getTagName(beanClass)
    assert(!tagName.isBlank()) { "Bean name is empty: $beanClass" }
  }

  @Synchronized
  override fun init(originalType: Type, serializer: Serializer) {
    assert(bindings == null)
    val classAnnotation = beanClass.getAnnotation(
      Property::class.java)

    val accessors = getAccessors(beanClass)
    val result = if (accessors.isEmpty()) NestedBinding.EMPTY_ARRAY else arrayOfNulls(accessors.size)
    for (i in result.indices) {
      val binding = createBinding(accessors[i],
                                  serializer,
                                  classAnnotation?.style ?: Property.Style.OPTION_TAG)
      binding.init(originalType, serializer)
      result[i] = binding
    }
    bindings = result
  }

  override fun serialize(o: Any, context: Any?, filter: SerializationFilter?): Any? {
    return serializeInto(o, if (context == null) null else Element(tagName), filter)
  }

  fun serialize(bean: Any, createElementIfEmpty: Boolean, filter: SerializationFilter?): Element? {
    return serializeInto(o = bean, preCreatedElement = if (createElementIfEmpty) Element(tagName) else null, filter = filter)
  }

  open fun serializeInto(o: Any, preCreatedElement: Element?, filter: SerializationFilter?): Element? {
    var element = preCreatedElement
    for (binding in bindings!!) {
      if (o is SerializationFilter && !o.accepts(binding.accessor, o)) {
        continue
      }

      element = serializePropertyInto(binding = binding, o = o, preCreatedElement = element, filter = filter, isFilterPropertyItself = true)
    }
    return element
  }

  fun serializePropertyInto(
    binding: NestedBinding,
    o: Any,
    preCreatedElement: Element?,
    filter: SerializationFilter?,
    isFilterPropertyItself: Boolean,
  ): Element? {
    var element = preCreatedElement
    val accessor = binding.accessor
    val property = accessor.getAnnotation(Property::class.java)
    if (property == null || !property.alwaysWrite) {
      if (filter != null && isFilterPropertyItself) {
        if (filter is SkipDefaultsSerializationFilter) {
          if (filter.equal(binding, o)) {
            return element
          }
        }
        else if (!filter.accepts(accessor, o)) {
          return element
        }
      }

      //todo: optimize. Cache it.
      if (property != null) {
        val propertyFilter = XmlSerializerUtil.getPropertyFilter(property)
        if (propertyFilter != null && !propertyFilter.accepts(accessor, o)) {
          return element
        }
      }
    }

    if (element == null) {
      element = Element(tagName)
    }

    val node = binding.serialize(o, element, filter)
    if (node != null) {
      if (node is Attribute) {
        element.setAttribute(node as Attribute?)
      }
      else {
        Binding.addContent(element, node)
      }
    }
    return element
  }

  override fun deserialize(context: Any?, element: Element): Any {
    val instance = newInstance()
    deserializeInto(result = instance, element = element)
    return instance
  }

  fun deserializeInto(result: Any, element: Element) {
    deserializeBeanInto(
      result = result,
      element = element,
      accessorNameTracker = null,
      bindings = bindings!!,
      start = 0,
      end = bindings!!.size,
    )
  }

  fun deserializeInto(result: Any, element: XmlElement) {
    deserializeBeanInto(
      result = result,
      element = element,
      bindings = bindings!!,
      start = 0,
      end = bindings!!.size,
    )
  }

  override fun deserialize(context: Any?, element: XmlElement): Any {
    val instance = newInstance()
    deserializeBeanInto(result = instance, element = element, bindings = bindings!!)
    return instance
  }

  open fun newInstance(): Any {
    try {
      val constructor = beanClass.getDeclaredConstructor()
      try {
        constructor.setAccessible(true)
      }
      catch (ignored: SecurityException) {
      }
      return constructor.newInstance()
    }
    catch (e: InvocationTargetException) {
      throw e.targetException ?: e
    }
  }

  fun equalByFields(currentValue: Any, defaultValue: Any, filter: SkipDefaultsSerializationFilter): Boolean {
    for (binding in bindings!!) {
      val accessor = binding.accessor
      if (!filter.equal(binding, accessor.read(currentValue), accessor.read(defaultValue))) {
        return false
      }
    }
    return true
  }

  fun computeBindingWeights(accessorNameTracker: Set<String>): Object2FloatMap<String> {
    val weights = Object2FloatOpenHashMap<String>(accessorNameTracker.size)
    var weight = 0f
    val step = bindings!!.size.toFloat() / accessorNameTracker.size.toFloat()
    for (name in accessorNameTracker) {
      weights.put(name, weight)
      weight += step
    }

    weight = 0f
    for (binding in bindings!!) {
      val name = binding.accessor.name
      if (!weights.containsKey(name)) {
        weights.put(name, weight)
      }

      weight++
    }
    return weights
  }

  fun sortBindings(weights: Object2FloatMap<String>) {
    Arrays.sort(bindings!!) { o1, o2 ->
      val n1 = o1.accessor.name
      val n2 = o2.accessor.name
      val w1 = weights.getFloat(n1)
      val w2 = weights.getFloat(n2)
      (w1 - w2).toInt()
    }
  }

  override fun isBoundTo(element: Element): Boolean = element.name == tagName

  override fun isBoundTo(element: XmlElement): Boolean = element.name == tagName

  override fun toString(): String = "BeanBinding[${beanClass.name}, tagName=$tagName]"
}

// binding value will be not set if no data
fun deserializeBeanInto(
  result: Any,
  element: XmlElement,
  bindings: Array<NestedBinding>,
  start: Int = 0,
  end: Int = bindings.size,
) {
  var attributeBindingCount = 0
  for (i in start until end) {
    val binding = bindings[i]
    if (binding is AttributeBinding) {
      attributeBindingCount++
      element.attributes.get(binding.name)?.let {
        binding.setValue(result, it)
      }
    }
    else if (element.content != null && binding is TextBinding) {
      binding.setValue(result, element.content!!)
    }
  }

  if (attributeBindingCount == bindings.size) {
    return
  }

  var data: LinkedHashMap<MultiNodeBinding, MutableList<XmlElement?>>? = null
  nextNode@ for (child in element.children) {
    for (i in start until end) {
      val binding = bindings[i]
      if (binding is AttributeBinding || binding is TextBinding || !binding.isBoundTo(child)) {
        continue
      }

      if (binding is MultiNodeBinding && binding.isMulti) {
        if (data == null) {
          data = LinkedHashMap()
        }
        data.computeIfAbsent(binding) { ArrayList() }.add(child)
      }
      else {
        binding.deserializeUnsafe(result, child)
      }
      continue@nextNode
    }
  }

  for (i in start until end) {
    val binding = bindings[i]
    if (binding is AccessorBindingWrapper && binding.isFlat) {
      binding.deserializeUnsafe(result, element)
    }
  }

  if (data != null) {
    for (binding in data.keys) {
      binding.deserializeList2(result, data.get(binding)!!)
    }
  }
}

fun deserializeBeanInto(
  result: Any,
  element: Element,
  bindings: Array<NestedBinding>,
  start: Int = 0,
  end: Int,
  accessorNameTracker: MutableSet<String>? = null,
) {
  nextAttribute@ for (attribute in element.attributes) {
    if (attribute.namespaceURI.isNullOrEmpty()) {
      for (i in start until end) {
        val binding = bindings[i]
        if (binding is AttributeBinding && binding.name == attribute.name) {
          accessorNameTracker?.add(binding.getAccessor().name)
          binding.setValue(result, attribute.value)
          continue@nextAttribute
        }
      }
    }
  }

  var data: LinkedHashMap<NestedBinding, MutableList<Element?>>? = null
  nextNode@ for (content in element.content) {
    for (i in start until end) {
      val binding = bindings[i]
      if (content is Text) {
        if (binding is TextBinding) {
          binding.setValue(result, content.getValue())
        }
        continue
      }

      val child = content as Element
      if (binding.isBoundTo(child)) {
        if (binding is MultiNodeBinding && binding.isMulti) {
          if (data == null) {
            data = LinkedHashMap()
          }
          data.computeIfAbsent(binding) { ArrayList() }.add(child)
        }
        else {
          accessorNameTracker?.add(binding.accessor.name)
          binding.deserializeUnsafe(result, child)
        }
        continue@nextNode
      }
    }
  }

  for (i in start until end) {
    val binding = bindings[i]
    if (binding is AccessorBindingWrapper && binding.isFlat) {
      binding.deserializeUnsafe(result, element)
    }
  }

  if (data != null) {
    for (binding in data.keys) {
      accessorNameTracker?.add(binding.accessor.name)
      (binding as MultiNodeBinding).deserializeList(result, data[binding]!!)
    }
  }
}

// must be static class and not anonymous
private class MyPropertyCollectorConfiguration : PropertyCollector.Configuration(true, false, false) {
  override fun isAnnotatedAsTransient(element: AnnotatedElement): Boolean {
    return element.isAnnotationPresent(Transient::class.java)
  }

  override fun hasStoreAnnotations(element: AccessibleObject): Boolean {
    @Suppress("DEPRECATION")
    return element.isAnnotationPresent(OptionTag::class.java) ||
           element.isAnnotationPresent(Tag::class.java) ||
           element.isAnnotationPresent(com.intellij.util.xmlb.annotations.Attribute::class.java) ||
           element.isAnnotationPresent(Property::class.java) ||
           element.isAnnotationPresent(com.intellij.util.xmlb.annotations.Text::class.java) ||
           element.isAnnotationPresent(CollectionBean::class.java) ||
           element.isAnnotationPresent(MapAnnotation::class.java) ||
           element.isAnnotationPresent(XMap::class.java) ||
           element.isAnnotationPresent(XCollection::class.java) ||
           element.isAnnotationPresent(com.intellij.util.xmlb.annotations.AbstractCollection::class.java)
  }
}

private fun createBinding(accessor: MutableAccessor, serializer: Serializer, propertyStyle: Property.Style): NestedBinding {
  val attribute = accessor.getAnnotation(com.intellij.util.xmlb.annotations.Attribute::class.java)
  if (attribute != null) {
    return AttributeBinding(accessor, attribute)
  }

  val text = accessor.getAnnotation(com.intellij.util.xmlb.annotations.Text::class.java)
  if (text != null) {
    return TextBinding(accessor)
  }

  val optionTag = accessor.getAnnotation(OptionTag::class.java)
  if (optionTag != null && optionTag.converter != Converter::class.java) {
    return OptionTagBinding(accessor, optionTag)
  }

  val binding = serializer.getBinding(accessor)
  if (binding is JDOMElementBinding) {
    return binding
  }

  val tag = accessor.getAnnotation(Tag::class.java)
  if (tag != null) {
    return TagBinding(accessor, tag)
  }

  if (binding is CompactCollectionBinding) {
    return AccessorBindingWrapper(accessor, binding, false, Property.Style.OPTION_TAG)
  }

  var surroundWithTag = true
  var inline = false
  val property = accessor.getAnnotation(Property::class.java)
  if (property != null) {
    surroundWithTag = property.surroundWithTag
    inline = property.flat
  }

  if (!surroundWithTag || inline) {
    if (inline && binding !is BeanBinding) {
      throw XmlSerializationException("inline supported only for BeanBinding: $accessor")
    }
    if (binding == null || binding is TextBinding) {
      throw XmlSerializationException("Text-serializable properties can't be serialized without surrounding tags: $accessor")
    }
    return AccessorBindingWrapper(accessor, binding, inline, property!!.style)
  }

  val xCollection = accessor.getAnnotation(XCollection::class.java)
  if (xCollection != null && (!xCollection.propertyElementName.isEmpty() || xCollection.style == XCollection.Style.v2)) {
    return TagBinding(accessor, xCollection.propertyElementName)
  }

  if (optionTag == null) {
    accessor.getAnnotation(XMap::class.java)?.let {
      return TagBinding(accessor, it.propertyElementName)
    }
  }

  if (propertyStyle == Property.Style.ATTRIBUTE) {
    return AttributeBinding(accessor, null)
  }
  return OptionTagBinding(accessor, optionTag)
}

private class XmlSerializerPropertyCollector(configuration: Configuration) : PropertyCollector(configuration) {
  private val accessorCache = XmlSerializerPropertyCollectorListClassValue(configuration)

  override fun collect(aClass: Class<*>): List<MutableAccessor> = accessorCache.get(aClass)
}

private class XmlSerializerPropertyCollectorListClassValue(
  private val configuration: PropertyCollector.Configuration,
) : ClassValue<List<MutableAccessor>>() {
  override fun computeValue(aClass: Class<*>): List<MutableAccessor> {
    // do not pass classToOwnFields cache - no need because we collect the whole set of accessors
    val result = PropertyCollector.doCollect(aClass, configuration, null)
    if (result.isNotEmpty() || isAssertBindings(aClass)) {
      return result
    }

    @Suppress("DEPRECATION")
    when {
      com.intellij.openapi.util.JDOMExternalizable::class.java.isAssignableFrom(aClass) -> {
        LOG.error("Do not compute bindings for JDOMExternalizable: ${aClass.name}")
      }
      aClass.isEnum -> LOG.error("Do not compute bindings for enum: ${aClass.name}")
      aClass === String::class.java -> LOG.error("Do not compute bindings for String")
    }

    LOG.warn("No accessors for ${aClass.name}. " +
             "This means that state class cannot be serialized properly. Please see https://jb.gg/ij-psoc")
    return result
  }
}

private fun isAssertBindings(startClass: Class<*>): Boolean {
  var currentClass = startClass
  do {
    val property = currentClass.getAnnotation(Property::class.java)
    if (property != null && !property.assertIfNoBindings) {
      return true
    }

    currentClass = currentClass.superclass ?: return false
  }
  while (true)
}

private fun getTagName(aClass: Class<*>): String {
  var currentClass = aClass
  while (true) {
    getTagNameFromAnnotation(currentClass)?.let {
      return it
    }
    currentClass = currentClass.superclass ?: break
  }

  var name = aClass.simpleName
  if (name.isEmpty()) {
    name = aClass.superclass.simpleName
  }

  val lastIndexOf = name.lastIndexOf('$')
  if (lastIndexOf > 0 && name.length > (lastIndexOf + 1)) {
    return name.substring(lastIndexOf + 1)
  }
  return name
}

private fun getTagNameFromAnnotation(aClass: Class<*>): String? = aClass.getAnnotation(Tag::class.java)?.value?.takeIf { it.isNotEmpty() }