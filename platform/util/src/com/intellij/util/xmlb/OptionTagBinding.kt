// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.xmlb

import com.intellij.openapi.util.JDOMUtil
import com.intellij.serialization.ClassUtil
import com.intellij.serialization.MutableAccessor
import com.intellij.util.xml.dom.XmlElement
import kotlinx.serialization.json.JsonElement
import org.jdom.Content
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Text
import java.util.*

internal class OptionTagBinding(
  @JvmField internal val binding: Binding?,
  override val accessor: MutableAccessor,
  private val nameAttributeValue: String?,
  converterClass: Class<out Converter<*>>?,
  private val tag: String,
  private val nameAttribute: String?,
  private val valueAttribute: String?,
  private val serializeBeanBindingWithoutWrapperTag: Boolean = false,
  private val textIfTagValueEmpty: String = "",
) : PrimitiveValueBinding {
  private val converter: Converter<Any>? = converterClass?.let {
    val constructor = it.getDeclaredConstructor()
    try {
      constructor.setAccessible(true)
    }
    catch (ignored: SecurityException) {
    }
    @Suppress("UNCHECKED_CAST")
    constructor.newInstance() as Converter<Any>
  }

  override val isPrimitive: Boolean
    get() = binding == null || converter != null

  override fun deserializeUnsafe(context: Any?, element: Element): Any {
    return deserialize(host = context!!, element = element, domAdapter = JdomAdapter)
  }

  override fun deserializeUnsafe(context: Any?, element: XmlElement): Any {
    return deserialize(host = context!!, element = element, domAdapter = XmlDomAdapter)
  }

  override fun setValue(bean: Any, value: String?) {
    if (converter == null) {
      try {
        XmlSerializerImpl.doSet(bean, value, accessor, ClassUtil.typeToClass(accessor.genericType))
      }
      catch (e: Exception) {
        throw RuntimeException("Cannot set value for field ${accessor.name}", e)
      }
    }
    else {
      accessor.set(bean, value?.let { converter.fromString(it) })
    }
  }

  override fun toJson(bean: Any, filter: SerializationFilter?): JsonElement? {
    if (binding == null || converter != null) {
      return toJson(bean = bean, accessor = accessor, converter = converter)
    }
    else {
      val value = accessor.read(bean) ?: return null
      return binding.toJson(value, filter)
    }
  }

  override fun fromJson(bean: Any?, element: JsonElement): Any {
    if (binding == null || converter != null) {
      fromJson(bean = bean!!, data = element, accessor = accessor, valueClass = ClassUtil.typeToClass(accessor.genericType), converter = converter)
    }
    else {
      val value = binding.fromJson(accessor.read(bean!!), element)
      check(value !== Unit)
      accessor.set(bean, value)
    }
    return Unit
  }

  override val propertyName: String
    get() = nameAttributeValue ?: tag

  override fun serialize(bean: Any, parent: Element, filter: SerializationFilter?) {
    val value = accessor.read(bean)
    val targetElement = Element(true, tag, Namespace.NO_NAMESPACE)
    if (nameAttribute != null) {
      targetElement.setAttribute(nameAttribute, nameAttributeValue)
    }

    if (value == null) {
      parent.addContent(targetElement)
      return
    }

    if (valueAttribute == null) {
      if (converter == null) {
        if (binding == null) {
          targetElement.addContent(Text(XmlSerializerImpl.convertToString(value)))
        }
        else if (serializeBeanBindingWithoutWrapperTag) {
          (binding as BeanBinding).serializeProperties(bean = value, preCreatedElement = targetElement, filter = filter)
        }
        else {
          binding.serialize(bean = value, parent = targetElement, filter = filter)
        }
      }
      else {
        converter.toString(value)?.let {
          targetElement.addContent(Text(it))
        }
      }
    }
    else {
      if (converter == null) {
        if (binding == null) {
          targetElement.setAttribute(valueAttribute, JDOMUtil.removeControlChars(XmlSerializerImpl.convertToString(value)))
        }
        else {
          binding.serialize(bean = value, parent = targetElement, filter = filter)
        }
      }
      else {
        converter.toString(value)?.let {
          targetElement.setAttribute(valueAttribute, JDOMUtil.removeControlChars(it))
        }
      }
    }

    parent.addContent(targetElement)
  }

  private fun <T : Any> deserialize(host: Any, element: T, domAdapter: DomAdapter<T>): Any {
    if (valueAttribute == null) {
      if (converter == null && binding != null) {
        if (binding is BeanBinding) {
          // yes, we must set `null` as well
          val value = (if (serializeBeanBindingWithoutWrapperTag) element else domAdapter.firstElement(element))?.let {
            binding.deserialize(context = null, element = it as Element)
          }
          accessor.set(host, value)
        }
        else if (domAdapter.hasElementContent(element)) {
          val oldValue = accessor.read(host)
          val newValue = domAdapter.deserializeList(binding = binding, oldValue = oldValue, element = element)
          if (oldValue !== newValue) {
            accessor.set(host, newValue)
          }
        }
        else if (binding is CollectionBinding || binding is MapBinding) {
          val oldValue = accessor.read(host)
          // do nothing if the field is already null
          if (oldValue != null) {
            val newValue = domAdapter.deserializeEmptyList(oldValue, element, binding as MultiNodeBinding)
            if (oldValue !== newValue) {
              accessor.set(host, newValue)
            }
          }
        }
        else {
          accessor.set(host, null)
        }
      }
      else {
        setValue(bean = host, value = domAdapter.getTextValue(element = element, defaultText = textIfTagValueEmpty))
      }
    }
    else {
      val value = domAdapter.getAttributeValue(valueAttribute, element)
      if (converter == null) {
        if (binding == null) {
          XmlSerializerImpl.doSet(host, value, accessor, ClassUtil.typeToClass(accessor.genericType))
        }
        else {
          accessor.set(host, domAdapter.deserializeUnsafe(host, element, binding))
        }
      }
      else {
        accessor.set(host, value?.let { converter.fromString(it) })
      }
    }
    return host
  }

  override fun isBoundTo(element: Element): Boolean {
    return element.name == tag && (nameAttribute == null || element.getAttributeValue(nameAttribute) == nameAttributeValue)
  }

  override fun isBoundTo(element: XmlElement): Boolean {
    return element.name == tag && (nameAttribute == null || element.getAttributeValue(nameAttribute) == nameAttributeValue)
  }

  override fun toString(): String = "OptionTagBinding(nameAttributeValue=$nameAttributeValue, tag=$tag, binding=$binding)"
}

private sealed interface DomAdapter<T : Any> {
  fun getTextValue(element: T, defaultText: String): String

  fun firstElement(element: T): T?

  fun hasElementContent(element: T): Boolean

  fun deserializeEmptyList(oldValue: Any?, element: T, binding: MultiNodeBinding): Any?

  fun deserializeList(oldValue: Any?, element: T, binding: Binding): Any?

  fun getAttributeValue(name: String, element: T): String?

  fun deserializeUnsafe(host: Any, element: T, binding: Binding): Any?
}

private data object JdomAdapter : DomAdapter<Element> {
  override fun getTextValue(element: Element, defaultText: String): String {
    return XmlSerializerImpl.getTextValue(/* element = */ element, /* defaultText = */ defaultText)
  }

  override fun firstElement(element: Element) = element.content.firstOrNull() as Element?

  override fun hasElementContent(element: Element) = element.content.any { it is Element }

  override fun deserializeEmptyList(oldValue: Any?, element: Element, binding: MultiNodeBinding): Any? {
    return binding.deserializeJdomList(oldValue, Collections.emptyList())
  }

  override fun deserializeList(oldValue: Any?, element: Element, binding: Binding): Any? {
    return deserializeJdomList(binding = binding, context = oldValue, nodes = element.children)
  }

  override fun getAttributeValue(name: String, element: Element): String? = element.getAttributeValue(name)

  override fun deserializeUnsafe(host: Any, element: Element, binding: Binding): Any? = binding.deserializeUnsafe(host, element)
}

private data object XmlDomAdapter : DomAdapter<XmlElement> {
  override fun getTextValue(element: XmlElement, defaultText: String) = element.content ?: defaultText

  override fun firstElement(element: XmlElement) = element.children.firstOrNull()

  override fun hasElementContent(element: XmlElement) = element.children.isNotEmpty()

  override fun deserializeEmptyList(oldValue: Any?, element: XmlElement, binding: MultiNodeBinding): Any? {
    return binding.deserializeList(oldValue, Collections.emptyList())
  }

  override fun deserializeList(oldValue: Any?, element: XmlElement, binding: Binding): Any? {
    return deserializeList(binding = binding, context = oldValue, nodes = element.children)
  }

  override fun getAttributeValue(name: String, element: XmlElement): String? = element.getAttributeValue(name)

  override fun deserializeUnsafe(host: Any, element: XmlElement, binding: Binding): Any? = binding.deserializeUnsafe(host, element)
}

internal fun addContent(targetElement: Element, node: Any) {
  when (node) {
    is Content -> targetElement.addContent(node)
    is List<*> -> {
      @Suppress("UNCHECKED_CAST")
      targetElement.addContent(node as Collection<Content>)
    }
    else -> throw IllegalArgumentException("Wrong node: $node")
  }
}


internal fun deserializeJdomList(binding: Binding, context: Any?, nodes: List<Element>): Any? {
  return when {
    binding is MultiNodeBinding -> binding.deserializeJdomList(context = context, elements = nodes)
    nodes.size == 1 -> binding.deserializeUnsafe(context, nodes.get(0))
    nodes.isEmpty() -> null
    else -> throw AssertionError("Duplicate data for $binding will be ignored")
  }
}

internal fun deserializeList(binding: Binding, context: Any?, nodes: List<XmlElement>): Any? {
  return when {
    binding is MultiNodeBinding -> binding.deserializeList(context = context, elements = nodes)
    nodes.size == 1 -> binding.deserializeUnsafe(context, nodes.get(0))
    nodes.isEmpty() -> null
    else -> throw AssertionError("Duplicate data for $binding will be ignored")
  }
}