// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb

import com.intellij.util.xml.dom.XmlElement
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.*

@Internal
sealed interface DomAdapter<T : Any> {
  fun getTextValue(element: T, defaultText: String): String

  fun firstElement(element: T): T?

  fun hasElementContent(element: T): Boolean

  fun deserializeEmptyList(oldValue: Any?, element: T, binding: MultiNodeBinding): Any?

  fun deserializeList(oldValue: Any?, element: T, binding: Binding): Any?

  fun getAttributeValue(element: T, name: String): String?

  fun deserializeUnsafe(host: Any, element: T, binding: Binding): Any?

  fun getName(element: T): String

  fun getChildren(element: T): List<T>

  fun getChild(element: T, name: String): T?
}

@Internal
data object JdomAdapter : DomAdapter<Element> {
  override fun getName(element: Element): String = element.name

  override fun getTextValue(element: Element, defaultText: String): String {
    return XmlSerializerImpl.getTextValue(/* element = */ element, /* defaultText = */ defaultText)
  }

  override fun firstElement(element: Element): Element? = element.content.firstOrNull() as Element?

  override fun hasElementContent(element: Element): Boolean = element.content.any { it is Element }

  override fun deserializeEmptyList(oldValue: Any?, element: Element, binding: MultiNodeBinding): Any? {
    return binding.deserializeList(oldValue, Collections.emptyList(), XmlDomAdapter)
  }

  override fun deserializeList(oldValue: Any?, element: Element, binding: Binding): Any? {
    return deserializeList(binding = binding, context = oldValue, nodes = element.children, JdomAdapter)
  }

  override fun getAttributeValue(element: Element, name: String): String? = element.getAttributeValue(name)

  override fun deserializeUnsafe(host: Any, element: Element, binding: Binding): Any? = binding.deserializeUnsafe(host, element, JdomAdapter)

  override fun getChildren(element: Element): List<Element> = element.children

  override fun getChild(element: Element, name: String): Element? = element.getChild(name)
}

@Internal
data object XmlDomAdapter : DomAdapter<XmlElement> {
  override fun getName(element: XmlElement): String = element.name

  override fun getTextValue(element: XmlElement, defaultText: String): String = element.content ?: defaultText

  override fun firstElement(element: XmlElement): XmlElement? = element.children.firstOrNull()

  override fun hasElementContent(element: XmlElement): Boolean = element.children.isNotEmpty()

  override fun deserializeEmptyList(oldValue: Any?, element: XmlElement, binding: MultiNodeBinding): Any? {
    return binding.deserializeList(oldValue, Collections.emptyList(), XmlDomAdapter)
  }

  override fun deserializeList(oldValue: Any?, element: XmlElement, binding: Binding): Any? {
    return deserializeList(binding = binding, context = oldValue, nodes = element.children, adapter = XmlDomAdapter)
  }

  override fun getAttributeValue(element: XmlElement, name: String): String? = element.getAttributeValue(name)

  override fun deserializeUnsafe(host: Any, element: XmlElement, binding: Binding): Any? = binding.deserializeUnsafe(host, element, XmlDomAdapter)

  override fun getChildren(element: XmlElement): List<XmlElement> = element.children

  override fun getChild(element: XmlElement, name: String): XmlElement? = element.getChild(name)
}
