// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.xmlb

import com.intellij.openapi.util.JDOMUtil
import com.intellij.serialization.ClassUtil
import com.intellij.serialization.MutableAccessor
import kotlinx.serialization.json.JsonElement
import org.jdom.Content
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Text

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

  override fun <T : Any> deserializeUnsafe(context: Any?, element: T, adapter: DomAdapter<T>): Any {
    return deserialize(host = context!!, element = element, adapter = adapter)
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

  override fun setFromJson(bean: Any, element: JsonElement) {
    if (binding == null || converter != null) {
      setFromJson(bean = bean, data = element, accessor = accessor, valueClass = ClassUtil.typeToClass(accessor.genericType), converter = converter)
    }
    else {
      if (binding is RootBinding) {
        val currentValue = accessor.read(bean)
        val value = binding.fromJson(currentValue = currentValue, element = element)
        check(value !== Unit)
        if (currentValue !== value) {
          accessor.set(bean, value)
        }
      }
      else {
        // e.g., JDOMElementBinding wrapped by OptionTagBinding (it makes sense for XML format, but doesn't make sense for JSON)
        (binding as NestedBinding).setFromJson(bean, element)
      }
    }
    return
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

  private fun <T : Any> deserialize(host: Any, element: T, adapter: DomAdapter<T>): Any {
    if (valueAttribute == null) {
      if (converter == null && binding != null) {
        if (binding is BeanBinding) {
          // yes, we must set `null` as well
          val value = (if (serializeBeanBindingWithoutWrapperTag) element else adapter.firstElement(element))?.let {
            binding.deserialize(context = null, element = it, adapter)
          }
          accessor.set(host, value)
        }
        else if (adapter.hasElementContent(element)) {
          val oldValue = accessor.read(host)
          val newValue = adapter.deserializeList(binding = binding, oldValue = oldValue, element = element)
          if (oldValue !== newValue) {
            accessor.set(host, newValue)
          }
        }
        else if (binding is CollectionBinding || binding is MapBinding) {
          val oldValue = accessor.read(host)
          // do nothing if the field is already null
          if (oldValue != null) {
            val newValue = adapter.deserializeEmptyList(oldValue, element, binding as MultiNodeBinding)
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
        setValue(bean = host, value = adapter.getTextValue(element = element, defaultText = textIfTagValueEmpty))
      }
    }
    else {
      val value = adapter.getAttributeValue(element, valueAttribute)
      if (converter == null) {
        if (binding == null) {
          XmlSerializerImpl.doSet(host, value, accessor, ClassUtil.typeToClass(accessor.genericType))
        }
        else {
          accessor.set(host, adapter.deserializeUnsafe(host, element, binding))
        }
      }
      else {
        accessor.set(host, value?.let { converter.fromString(it) })
      }
    }
    return host
  }

  override fun <T : Any> isBoundTo(element: T, adapter: DomAdapter<T>): Boolean {
    return adapter.getName(element) == tag && (nameAttribute == null || adapter.getAttributeValue(element, nameAttribute) == nameAttributeValue)
  }

  override fun toString(): String = "TagBinding(nameAttributeValue=$nameAttributeValue, tag=$tag, binding=$binding)"
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

internal fun <T : Any> deserializeList(binding: Binding, context: Any?, nodes: List<T>, adapter: DomAdapter<T>): Any? {
  return when {
    binding is MultiNodeBinding -> binding.deserializeList(bean = context, elements = nodes, adapter)
    nodes.size == 1 -> binding.deserializeUnsafe(context, nodes.get(0), adapter)
    nodes.isEmpty() -> null
    else -> throw AssertionError("Duplicate data for $binding will be ignored")
  }
}