// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.xmlb

import com.intellij.openapi.util.JDOMUtil
import com.intellij.serialization.ClassUtil
import com.intellij.serialization.MutableAccessor
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import org.jdom.Content
import org.jdom.Element
import org.jdom.Namespace
import org.jdom.Text
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class TagBinding(
  @JvmField val binding: Binding?,
  override val accessor: MutableAccessor,
  private val nameAttributeValue: String?,
  converterClass: Class<out Converter<*>>?,
  private val tag: String,
  private val nameAttribute: String?,
  private val valueAttribute: String?,
  private val serializeBeanBindingWithoutWrapperTag: Boolean = false,
  private val textIfTagValueEmpty: String = "",
) : NestedBinding {
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

  private fun setValue(bean: Any, value: String?) {
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
      val value = accessor.read(bean) ?: return JsonNull
      return binding.toJson(value, filter)
    }
  }

  override fun setFromJson(bean: Any, element: JsonElement) {
    if (binding == null || converter != null) {
      setFromJson(bean = bean, data = element, accessor = accessor, valueClass = ClassUtil.typeToClass(accessor.genericType), converter = converter)
    }
    else {
      if (binding is RootBinding) {
        if (binding is BeanBinding) {
          // yes, we must set `null` as well
          val value = binding.fromJson(currentValue = null, element = element)
          accessor.set(bean, value)
        }
        else if (binding is CollectionBinding || binding is MapBinding) {
          // in-place mutation only for non-writable accessors (final) - getter can return a mutable list,
          // and if we mutate it in-place, the deserialization result may be lost (XmlSerializerListTest.elementTypes test)
          if (accessor.isWritable) {
            // we must pass the current value in any case - collection binding use it to infer a new collection type
            val oldValue = accessor.read(bean)
            if (oldValue == null && element == JsonNull) {
              // do nothing if the field is already null
            }
            else {
              val newValue = binding.fromJson(currentValue = oldValue, element = element)
              accessor.set(bean, newValue)
            }
          }
          else {
            val oldValue = accessor.read(bean)
            if (oldValue == null && element == JsonNull) {
              // do nothing if the field is already null
            }
            else {
              val newValue = binding.fromJson(currentValue = oldValue, element = element)
              if (oldValue !== newValue) {
                accessor.set(bean, newValue)
              }
            }
          }
        }
        else {
          throw UnsupportedOperationException("Binding $binding is not expected")
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

  override fun deserializeToJson(element: Element): JsonElement? {
    if (valueAttribute == null) {
      if (converter == null && binding != null) {
        when (binding) {
          is BeanBinding -> {
            val beanElement = if (serializeBeanBindingWithoutWrapperTag) element else (element.content.firstOrNull() as Element?) ?: return JsonNull
            return binding.deserializeToJson(element = beanElement, includeClassDiscriminator = false)
          }
          is CollectionBinding, is MapBinding -> {
            return (binding as MultiNodeBinding).deserializeListToJson(element.children)
          }
          else -> {
            throw UnsupportedOperationException("Binding $binding is not expected")
          }
        }
      }
      else {
        return element.content?.firstOrNull { it is Text }?.value?.let { JsonPrimitive(it) } ?: JsonNull
      }
    }
    else {
      return valueToJson(element.getAttributeValue(valueAttribute), accessor.valueClass)
    }
  }

  override fun <T : Any> deserialize(context: Any?, element: T, adapter: DomAdapter<T>): Any {
    context!!
    if (valueAttribute == null) {
      if (converter == null && binding != null) {
        if (binding is BeanBinding) {
          // yes, we must set `null` as well
          val value = (if (serializeBeanBindingWithoutWrapperTag) element else adapter.firstElement(element))?.let {
            binding.deserialize(context = null, element = it, adapter = adapter)
          }
          accessor.set(context, value)
        }
        else if (binding is CollectionBinding || binding is MapBinding) {
          val nodes = adapter.getChildren(element)
          // in-place mutation only for non-writable accessors (final) - getter can return a mutable list,
          // and if we mutate it in-place, the deserialization result may be lost (XmlSerializerListTest.elementTypes test)
          if (accessor.isWritable) {
            // we must pass the current value in any case - collection binding use it to infer a new collection type
            val oldValue = accessor.read(context)
            if (nodes.isEmpty() && oldValue == null) {
              // do nothing if the field is already null
            }
            else {
              val newValue = (binding as MultiNodeBinding).deserializeList(currentValue = oldValue, elements = nodes, adapter = adapter)
              accessor.set(context, newValue)
            }
          }
          else {
            val oldValue = accessor.read(context)
            if (oldValue == null && nodes.isEmpty()) {
              // do nothing if the field is already null
            }
            else {
              val newValue = (binding as MultiNodeBinding).deserializeList(currentValue = oldValue, elements = nodes, adapter = adapter)
              if (oldValue !== newValue) {
                accessor.set(context, newValue)
              }
            }
          }
        }
        else {
          throw UnsupportedOperationException("Binding $binding is not expected")
        }
      }
      else {
        setValue(bean = context, value = adapter.getTextValue(element = element, defaultText = textIfTagValueEmpty))
      }
    }
    else {
      val value = adapter.getAttributeValue(element, valueAttribute)
      if (converter == null) {
        if (binding == null) {
          XmlSerializerImpl.doSet(context, value, accessor, ClassUtil.typeToClass(accessor.genericType))
        }
        else {
          accessor.set(context, binding.deserialize(context, element, adapter))
        }
      }
      else {
        accessor.set(context, value?.let { converter.fromString(it) })
      }
    }
    return context
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