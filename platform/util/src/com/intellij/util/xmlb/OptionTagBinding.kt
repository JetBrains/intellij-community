// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb

import com.intellij.openapi.util.JDOMUtil
import com.intellij.serialization.ClassUtil
import com.intellij.serialization.MutableAccessor
import com.intellij.util.xml.dom.XmlElement
import com.intellij.util.xmlb.annotations.OptionTag
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class OptionTagBinding internal constructor(accessor: MutableAccessor, optionTag: OptionTag?)
  : BasePrimitiveBinding(accessor, optionTag?.value, optionTag?.converter?.java) {
  private val tag: String
  private val nameAttribute: String?
  private val valueAttribute: String?

  init {
    if (optionTag == null) {
      tag = Constants.OPTION
      nameAttribute = Constants.NAME
      valueAttribute = Constants.VALUE
    }
    else {
      nameAttribute = optionTag.nameAttribute.takeIf { it.isNotEmpty() }
      valueAttribute = optionTag.valueAttribute.takeIf { it.isNotEmpty() }
      val customTag = optionTag.tag
      tag = if (nameAttribute == null && customTag == Constants.OPTION) accessor.name else customTag
    }
  }

  override fun setValue(bean: Any, value: String) {
    if (converter == null) {
      try {
        XmlSerializerImpl.doSet(bean, value, accessor, ClassUtil.typeToClass(accessor.genericType))
      }
      catch (e: Exception) {
        throw RuntimeException("Cannot set value for field $name", e)
      }
    }
    else {
      accessor.set(bean, converter.fromString(value))
    }
  }

  override fun serialize(o: Any, filter: SerializationFilter?): Any {
    val value = accessor.read(o)
    val targetElement = Element(tag)
    if (nameAttribute != null) {
      targetElement.setAttribute(nameAttribute, name)
    }

    if (value == null) {
      return targetElement
    }

    val converter = getConverter()
    if (converter == null) {
      val binding = binding
      if (binding == null) {
        targetElement.setAttribute(valueAttribute!!, JDOMUtil.removeControlChars(XmlSerializerImpl.convertToString(value)))
      }
      else if (binding is BeanBinding && valueAttribute == null) {
        binding.serializeInto(bean = value, preCreatedElement = targetElement, filter = filter)
      }
      else {
        val node = binding.serialize(value, targetElement, filter)
        if (node != null && targetElement !== node) {
          addContent(targetElement, node)
        }
      }
    }
    else {
      val text = converter.toString(value)
      if (text != null) {
        targetElement.setAttribute(valueAttribute, JDOMUtil.removeControlChars(text))
      }
    }
    return targetElement
  }

  override fun deserialize(context: Any, element: Element): Any {
    val value = valueAttribute?.let { element.getAttributeValue(it) }
    if (value == null) {
      if (valueAttribute == null) {
        accessor.set(context, binding!!.deserializeUnsafe(context, element))
      }
      else {
        val children = element.children
        if (children.isEmpty()) {
          if (binding is CollectionBinding || binding is MapBinding) {
            val oldValue = accessor.read(context)
            // do nothing if the field is already null
            if (oldValue != null) {
              val newValue = (binding as MultiNodeBinding).deserializeJdomList(oldValue, children)
              if (oldValue !== newValue) {
                accessor.set(context, newValue)
              }
            }
          }
          else {
            accessor.set(context, null)
          }
        }
        else {
          val oldValue = accessor.read(context)
          val newValue = deserializeJdomList(binding = binding!!, context = oldValue, nodes = children)
          if (oldValue !== newValue) {
            accessor.set(context, newValue)
          }
        }
      }
    }
    else {
      setValue(bean = context, value = value)
    }
    return context
  }

  override fun deserialize(context: Any, element: XmlElement): Any {
    val value = valueAttribute?.let { element.getAttributeValue(it) }
    if (value == null) {
      if (valueAttribute == null) {
        accessor.set(context, binding!!.deserializeUnsafe(context, element))
      }
      else {
        val children = element.children
        if (children.isEmpty()) {
          if (binding is CollectionBinding || binding is MapBinding) {
            val oldValue = accessor.read(context)
            // do nothing if the field is already null
            if (oldValue != null) {
              val newValue = (binding as MultiNodeBinding).deserializeList(oldValue, children)
              if (oldValue !== newValue) {
                accessor.set(context, newValue)
              }
            }
          }
          else {
            accessor.set(context, null)
          }
        }
        else {
          val oldValue = accessor.read(context)
          val newValue = deserializeList(binding = binding!!, context = oldValue, nodes = children)
          if (oldValue !== newValue) {
            accessor.set(context, newValue)
          }
        }
      }
    }
    else {
      setValue(bean = context, value = value)
    }
    return context
  }

  override fun isBoundTo(element: Element): Boolean {
    if (element.name != tag) {
      return false
    }
    return nameAttribute == null || element.getAttributeValue(nameAttribute) == this.name
  }

  override fun isBoundTo(element: XmlElement): Boolean {
    if (element.name != tag) {
      return false
    }
    return nameAttribute == null || element.getAttributeValue(nameAttribute) == this.name
  }

  override fun toString(): String = "OptionTagBinding(name=$name, binding=$binding)"
}
