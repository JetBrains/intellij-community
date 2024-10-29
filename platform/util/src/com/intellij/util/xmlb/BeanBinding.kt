// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")
@file:Internal

package com.intellij.util.xmlb

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.serialization.ClassUtil
import com.intellij.serialization.MutableAccessor
import com.intellij.serialization.PropertyCollector
import com.intellij.util.ThreeState
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xmlb.XmlSerializerImpl.createClassBinding
import com.intellij.util.xmlb.annotations.*
import it.unimi.dsi.fastutil.objects.Object2FloatMap
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap
import it.unimi.dsi.fastutil.objects.Object2ObjectArrayMap
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.reflect.AccessibleObject
import java.lang.reflect.AnnotatedElement
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Type
import java.util.*

@JvmField
internal val LOG: Logger = logger<Binding>()

private val PROPERTY_COLLECTOR = XmlSerializerPropertyCollector(MyPropertyCollectorConfiguration())
private val EMPTY_BINDINGS = arrayOf<NestedBinding>()

fun getBeanAccessors(aClass: Class<*>): List<MutableAccessor> = PROPERTY_COLLECTOR.collect(aClass)

internal const val JSON_CLASS_DISCRIMINATOR_KEY: String = "_class"

open class BeanBinding(@JvmField val beanClass: Class<*>) : Binding, RootBinding {
  @JvmField
  val tagName: String

  @Internal
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
  final override fun init(originalType: Type, serializer: Serializer) {
    assert(bindings == null)
    val accessors = PROPERTY_COLLECTOR.collect(beanClass)
    if (accessors.isEmpty()) {
      bindings = EMPTY_BINDINGS
    }
    else {
      val propertyStyle = beanClass.getAnnotation(Property::class.java)?.style ?: Property.Style.OPTION_TAG
      val result = Array(accessors.size) { i ->
        val binding = createBinding(accessor = accessors[i], serializer = serializer, propertyStyle = propertyStyle)
        binding.init(originalType, serializer)
        binding
      }
      bindings = result
    }
  }

  override fun toJson(bean: Any, filter: SerializationFilter?): JsonElement? {
    val map = serializeToJsonImpl(bean, filter, includeClassDiscriminator = false)
    return if (map == null) JsonObject(Collections.emptyMap()) else JsonObject(map)
  }

  internal fun toJson(bean: Any, filter: SerializationFilter?, includeClassDiscriminator: Boolean): JsonElement {
    val map = serializeToJsonImpl(bean, filter, includeClassDiscriminator = includeClassDiscriminator)
    return if (map == null) {
      JsonObject(if (includeClassDiscriminator) Collections.singletonMap(JSON_CLASS_DISCRIMINATOR_KEY, JsonPrimitive(tagName)) else Collections.emptyMap())
    }
    else {
      JsonObject(map)
    }
  }

  override fun deserializeToJson(element: Element): JsonElement {
    return deserializeBeanXmlToJson(element = element, bindings = bindings!!, jsonDiscriminator = tagName, includeClassDiscriminator = false)
  }

  fun deserializeToJson(element: Element, includeClassDiscriminator: Boolean): JsonElement {
    return deserializeBeanXmlToJson(element = element, bindings = bindings!!, jsonDiscriminator = tagName, includeClassDiscriminator = includeClassDiscriminator)
  }

  private fun serializeToJsonImpl(bean: Any, filter: SerializationFilter?, includeClassDiscriminator: Boolean): Map<String, JsonElement>? {
    // do not waste time to compute hashCode and preserving uniqueness, use simple Object2ObjectArrayMap
    var keys: Array<String?>? = null
    var values: Array<JsonElement?>? = null
    val bindings = bindings!!
    var index = 0
    val extraSize = if (includeClassDiscriminator) 1 else 0
    for (binding in bindings) {
      if (isPropertySkipped(filter = filter, binding = binding, bean = bean, rootBinding = this, isFilterPropertyItself = true)) {
        continue
      }

      // yes, empty map to denote an empty object
      if (keys == null) {
        keys = arrayOfNulls(bindings.size + extraSize)
        values = arrayOfNulls(bindings.size + extraSize)

        if (includeClassDiscriminator) {
          keys[index] = JSON_CLASS_DISCRIMINATOR_KEY
          values[index] = JsonPrimitive(tagName)
          index++
        }
      }

      val jsonElement = binding.toJson(bean = bean, filter = filter)
      if (jsonElement != null) {
        keys[index] = normalizePropertyNameForKotlinx(binding)
        values!![index] = jsonElement
        index++
      }
    }
    return if (keys == null) null else Object2ObjectArrayMap(keys, values!!, index)
  }

  override fun fromJson(currentValue: Any?, element: JsonElement): Any? {
    if (element === JsonNull) {
      return null
    }

    val result = newInstance()
    if (element !is JsonObject) {
      LOG.warn("JsonObject is expected but got $element")
      return result
    }

    for (binding in bindings!!) {
      val key = normalizePropertyNameForKotlinx(binding)
      val value = element.get(key) ?: continue
      binding.setFromJson(result, value)
    }
    return result
  }

  override fun serialize(bean: Any, filter: SerializationFilter?): Element? {
    return serializeProperties(bean = bean, preCreatedElement = null, filter = filter)
  }

  final override fun serialize(bean: Any, parent: Element, filter: SerializationFilter?) {
    val element = Element(true, tagName, Namespace.NO_NAMESPACE)
    serializeProperties(bean = bean, preCreatedElement = element, filter = filter)
    parent.addContent(element)
  }

  fun serialize(bean: Any, createElementIfEmpty: Boolean, filter: SerializationFilter?): Element? {
    return serializeProperties(bean = bean, preCreatedElement = if (createElementIfEmpty) Element(tagName) else null, filter = filter)
  }

  open fun serializeProperties(bean: Any, preCreatedElement: Element?, filter: SerializationFilter?): Element? {
    var element = preCreatedElement
    for (binding in bindings!!) {
      element = serializeProperty(binding = binding, bean = bean, parentElement = element, filter = filter, isFilterPropertyItself = true)
    }
    return element
  }

  @Internal
  fun serializeProperty(binding: NestedBinding, bean: Any, parentElement: Element?, filter: SerializationFilter?, isFilterPropertyItself: Boolean): Element? {
    if (isPropertySkipped(filter = filter, binding = binding, bean = bean, rootBinding = this, isFilterPropertyItself = isFilterPropertyItself)) {
      return parentElement
    }

    val element = parentElement ?: Element(tagName)
    binding.serialize(bean = bean, parent = element, filter = filter)
    return element
  }

  @Internal
  override fun <T : Any> deserialize(context: Any?, element: T, adapter: DomAdapter<T>): Any {
    val instance = newInstance()
    deserializeInto(bean = instance, element = element, adapter = adapter)
    return instance
  }

  fun deserializeInto(bean: Any, element: Element) {
    deserializeJdomIntoBean(result = bean, element = element, accessorNameTracker = null, bindings = bindings!!)
  }

  @Internal
  fun <T : Any> deserializeInto(bean: Any, element: T, adapter: DomAdapter<T>) {
    when (adapter) {
      JdomAdapter -> deserializeJdomIntoBean(result = bean, element = element as Element, accessorNameTracker = null, bindings = bindings!!)
      XmlDomAdapter -> deserializeBeanInto(result = bean, element = element as XmlElement, bindings = bindings!!)
    }
  }

  @Internal
  fun deserializeInto(bean: Any, element: XmlElement) {
    deserializeBeanInto(result = bean, element = element, bindings = bindings!!)
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

  @Suppress("unused")
  internal fun computeBindingWeights(accessorNameTracker: Set<String>): Object2FloatMap<String> {
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

  @Internal
  override fun <T : Any> isBoundTo(element: T, adapter: DomAdapter<T>): Boolean = adapter.getName(element) == tagName

  override fun toString(): String = "BeanBinding(${beanClass.name}, tagName=$tagName)"

  @Deprecated("Don't use internal API", ReplaceWith(""))
  fun serializeInto(bean: Any, preCreatedElement: Element?, filter: SerializationFilter?): Element? {
    return serializeProperties(bean, preCreatedElement, filter)
  }
}

// binding value will be not set if no data
private fun deserializeBeanInto(result: Any, element: XmlElement, bindings: Array<NestedBinding>) {
  var attributeBindingCount = 0
  for (binding in bindings) {
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

  var data: LinkedHashMap<MultiNodeBinding, MutableList<XmlElement>>? = null
  nextNode@ for (child in element.children) {
    for (binding in bindings) {
      if (binding is AttributeBinding || binding is TextBinding || !binding.isBoundTo(child, XmlDomAdapter)) {
        continue
      }

      if (binding is MultiNodeBinding && binding.isMulti) {
        if (data == null) {
          data = LinkedHashMap()
        }
        data.computeIfAbsent(binding) { ArrayList() }.add(child)
      }
      else {
        binding.deserialize(context = result, element = child, adapter = XmlDomAdapter)
      }
      continue@nextNode
    }
  }

  for (binding in bindings) {
    if (binding is AccessorBindingWrapper && binding.isFlat) {
      binding.deserialize(context = result, element = element, adapter = XmlDomAdapter)
    }
  }

  if (data != null) {
    for (binding in data.keys) {
      binding.deserializeList(currentValue = result, elements = data.get(binding)!!, adapter = XmlDomAdapter)
    }
  }
}

private fun deserializeBeanXmlToJson(element: Element, bindings: Array<NestedBinding>, jsonDiscriminator: String, includeClassDiscriminator: Boolean): JsonElement {
  val extraSize = if (includeClassDiscriminator) 1 else 0
  val keys = arrayOfNulls<String>(bindings.size + extraSize)
  val values = arrayOfNulls<JsonElement>(bindings.size + extraSize)
  var i = 0
  fun put(key: String, value: JsonElement) {
    keys[i] = key
    values[i] = value
    i++
  }

  if (includeClassDiscriminator) {
    put(JSON_CLASS_DISCRIMINATOR_KEY, JsonPrimitive(jsonDiscriminator))
  }

  if (element.hasAttributes()) {
    nextAttribute@ for (attribute in element.attributes) {
      for (binding in bindings) {
        if (binding is AttributeBinding && binding.name == attribute.name) {
          put(normalizePropertyNameForKotlinx(binding), valueToJson(attribute.value, binding.valueClass) ?: JsonNull)
          continue@nextAttribute
        }
      }
    }
  }

  var data: LinkedHashMap<NestedBinding, MutableList<Element>>? = null
  nextNode@ for (content in element.content) {
    for (binding in bindings) {
      if (content is Text) {
        if (binding is TextBinding) {
          put(normalizePropertyNameForKotlinx(binding), JsonPrimitive(content.getValue()))
        }
        continue
      }

      val child = content as Element
      if (binding.isBoundTo(child, JdomAdapter)) {
        if (binding is MultiNodeBinding && binding.isMulti) {
          if (data == null) {
            data = LinkedHashMap()
          }
          data.computeIfAbsent(binding) { ArrayList() }.add(child)
        }
        else {
          put(normalizePropertyNameForKotlinx(binding), binding.deserializeToJson(child) ?: JsonNull)
        }
        continue@nextNode
      }
    }
  }

  for (binding in bindings) {
    if (binding is AccessorBindingWrapper && binding.isFlat) {
      put(normalizePropertyNameForKotlinx(binding), binding.deserializeToJson(element) ?: JsonNull)
    }
  }

  if (data != null) {
    for ((binding, list) in data) {
      put(normalizePropertyNameForKotlinx(binding), (binding as MultiNodeBinding).deserializeListToJson(list))
    }
  }
  return JsonObject(Object2ObjectArrayMap(keys, values, i))
}

internal fun deserializeJdomIntoBean(result: Any, element: Element, bindings: Array<NestedBinding>, accessorNameTracker: MutableSet<String>? = null) {
  if (element.hasAttributes()) {
    nextAttribute@ for (attribute in element.attributes) {
      for (binding in bindings) {
        if (binding is AttributeBinding && binding.name == attribute.name) {
          accessorNameTracker?.add(binding.accessor.name)
          binding.setValue(result, attribute.value)
          continue@nextAttribute
        }
      }
    }
  }

  var data: LinkedHashMap<NestedBinding, MutableList<Element>>? = null
  nextNode@ for (content in element.content) {
    for (binding in bindings) {
      if (content is Text) {
        if (binding is TextBinding) {
          binding.setValue(result, content.value)
        }
        continue
      }

      val child = content as Element
      if (binding.isBoundTo(child, JdomAdapter)) {
        if (binding is MultiNodeBinding && binding.isMulti) {
          if (data == null) {
            data = LinkedHashMap()
          }
          data.computeIfAbsent(binding) { ArrayList() }.add(child)
        }
        else {
          accessorNameTracker?.add(binding.accessor.name)
          binding.deserialize(result, child, JdomAdapter)
        }
        continue@nextNode
      }
    }
  }

  for (binding in bindings) {
    if (binding is AccessorBindingWrapper && binding.isFlat) {
      binding.deserialize(result, element, JdomAdapter)
    }
  }

  if (data != null) {
    for (binding in data.keys) {
      accessorNameTracker?.add(binding.accessor.name)
      (binding as MultiNodeBinding).deserializeList(result, data.get(binding)!!, JdomAdapter)
    }
  }
}

fun deserializeNestedBindingInto(result: Any, element: Element, binding: NestedBinding) {
  for (attribute in element.attributes) {
    if (binding is AttributeBinding && binding.name == attribute.name) {
      binding.setValue(result, attribute.value)
      return
    }
  }

  var data: MutableList<Element>? = null
  for (content in element.content) {
    if (content is Text) {
      if (binding is TextBinding) {
        binding.setValue(result, content.getValue())
      }
      return
    }

    val child = content as Element
    if (binding.isBoundTo(child, JdomAdapter)) {
      if (binding is MultiNodeBinding && binding.isMulti) {
        if (data == null) {
          data = ArrayList()
        }
        data.add(child)
      }
      else {
        binding.deserialize(result, child, JdomAdapter)
        break
      }
    }
  }

  if (binding is AccessorBindingWrapper && binding.isFlat) {
    binding.deserialize(result, element, JdomAdapter)
  }

  if (data != null) {
    (binding as MultiNodeBinding).deserializeList(currentValue = result, elements = data, adapter = JdomAdapter)
  }
}

@Suppress("DuplicatedCode")
fun deserializeNestedBindingToJson(element: Element, binding: NestedBinding): JsonElement? {
  for (attribute in element.attributes) {
    if (binding is AttributeBinding && binding.name == attribute.name) {
      return JsonPrimitive(attribute.value)
    }
  }

  var data: MutableList<Element>? = null
  for (content in element.content) {
    if (content is Text) {
      return if (binding is TextBinding) JsonPrimitive(content.getValue()) else null
    }

    val child = content as Element
    if (binding.isBoundTo(child, JdomAdapter)) {
      if (binding is MultiNodeBinding && binding.isMulti) {
        if (data == null) {
          data = ArrayList()
        }
        data.add(child)
      }
      else {
        return binding.deserializeToJson(child)
      }
    }
  }

  if (binding is AccessorBindingWrapper && binding.isFlat) {
    return binding.deserializeToJson(element)
  }

  if (data != null) {
    (binding as MultiNodeBinding).deserializeListToJson(elements = data)
  }

  return null
}

// must be static class and not anonymous
private class MyPropertyCollectorConfiguration
  : PropertyCollector.Configuration(/* collectAccessors = */ true, /* collectPrivateFields = */ false, /* collectFinalFields = */ false) {
  override fun isAnnotatedAsTransient(element: AnnotatedElement): Boolean = element.isAnnotationPresent(Transient::class.java)

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
  accessor.getAnnotation(com.intellij.util.xmlb.annotations.Attribute::class.java)?.let {
    return AttributeBinding(accessor, it)
  }

  if (accessor.isAnnotationPresent(com.intellij.util.xmlb.annotations.Text::class.java)) {
    return TextBinding(accessor)
  }

  val type = accessor.genericType
  val aClass = ClassUtil.typeToClass(type)
  var binding: Binding?
  if (ClassUtil.isPrimitive(aClass)) {
    binding = null
  }
  else {
    // do not cache because it depends on accessor
    binding = createClassBinding(aClass, accessor, type, serializer)
    if (binding == null) {
      // BeanBinding doesn't depend on accessor, get from cache or compute
      binding = serializer.getRootBinding(aClass, type)
    }
    else {
      binding.init(type, serializer)
      if (binding is CompactCollectionBinding) {
        return binding
      }
    }
  }

  val optionTag: OptionTag? = accessor.getAnnotation(OptionTag::class.java)
  if (optionTag != null && XmlSerializerUtil.getConverter(optionTag) != null) {
    return createOptionTagBindingByAnnotation(optionTag = optionTag, accessor = accessor, binding = binding)
  }

  if (binding is JDOMElementBinding) {
    return binding
  }

  accessor.getAnnotation(Tag::class.java)?.let { tagAnnotation ->
    return TagBinding(
      binding = binding,
      accessor = accessor,
      nameAttributeValue = null,
      converterClass = null,
      tag = tagAnnotation.value.ifEmpty { accessor.getName() },
      nameAttribute = null,
      valueAttribute = null,
      textIfTagValueEmpty = tagAnnotation.textIfEmpty,
    )
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
    return TagBinding(
      binding = binding,
      accessor = accessor,
      nameAttributeValue = null,
      converterClass = null,
      tag = xCollection.propertyElementName.ifEmpty { accessor.name },
      nameAttribute = null,
      valueAttribute = null,
      textIfTagValueEmpty = "",
    )
  }

  if (optionTag == null) {
    accessor.getAnnotation(XMap::class.java)?.let { xMapAnnotation ->
      return TagBinding(
        binding = binding,
        accessor = accessor,
        nameAttributeValue = null,
        converterClass = null,
        tag = xMapAnnotation.propertyElementName.ifEmpty { accessor.name },
        nameAttribute = null,
        valueAttribute = null,
      )
    }
  }

  if (propertyStyle == Property.Style.ATTRIBUTE) {
    return AttributeBinding(accessor, null)
  }

  if (optionTag == null) {
    return TagBinding(
      binding = binding,
      accessor = accessor,
      nameAttributeValue = accessor.name,
      converterClass = null,
      tag = Constants.OPTION,
      nameAttribute = Constants.NAME,
      valueAttribute = if (binding == null) Constants.VALUE else null,
    )
  }
  else {
    return createOptionTagBindingByAnnotation(optionTag = optionTag, accessor = accessor, binding = binding)
  }
}

private fun createOptionTagBindingByAnnotation(optionTag: OptionTag, accessor: MutableAccessor, binding: Binding?): TagBinding {
  val customTag = optionTag.tag
  val nameAttribute = optionTag.nameAttribute.takeIf { it.isNotEmpty() }
  val converter = XmlSerializerUtil.getConverter(optionTag)
  val isSerializedAsPrimitive = binding == null || converter != null
  return TagBinding(
    binding = binding,
    accessor = accessor,
    nameAttributeValue = if (nameAttribute == null) null else optionTag.value.ifEmpty { accessor.name },
    converterClass = converter,
    tag = if (nameAttribute == null && customTag == Constants.OPTION) accessor.name else customTag,
    nameAttribute = nameAttribute,
    valueAttribute = if (isSerializedAsPrimitive) optionTag.valueAttribute.takeIf { it.isNotEmpty() } else null,
    serializeBeanBindingWithoutWrapperTag = binding is BeanBinding && optionTag.valueAttribute.isEmpty()
  )
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

    LOG.debug("No accessors for ${aClass.name}. " +
              "This means that state class cannot be serialized properly. Please see https://jb.gg/ij-psoc")
    return Collections.emptyList()
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

@Internal
fun isPropertySkipped(filter: SerializationFilter?, binding: NestedBinding, rootBinding: BeanBinding, bean: Any, isFilterPropertyItself: Boolean): Boolean {
  val accessor = binding.accessor

  if (bean is SerializationFilter && !bean.accepts(accessor, bean)) {
    return true
  }

  val property = accessor.getAnnotation(Property::class.java)
  if (property == null || !property.alwaysWrite) {
    if (filter != null && isFilterPropertyItself) {
      if (filter is SkipDefaultsSerializationFilter) {
        val c = bean.javaClass
        val defaultBean = filter.getDefaultValue(c) { rootBinding.newInstance() }
        if (filter.equal(binding, accessor.read(bean), accessor.read(defaultBean))) {
          return true
        }
      }
      else if (!filter.accepts(accessor, bean)) {
        return true
      }
    }

    if (property != null) {
      val propertyFilter = XmlSerializerUtil.getPropertyFilter(property)
      if (propertyFilter != null && !propertyFilter.accepts(accessor, bean)) {
        return true
      }
    }
  }
  return false
}

fun normalizePropertyNameForKotlinx(binding: NestedBinding): String {
  val s = binding.propertyName
  if (s.all { it.isUpperCase() || it == '_' || it.isDigit() }) {
    // lower-case it
    return s.lowercase()
  }
  return s
}