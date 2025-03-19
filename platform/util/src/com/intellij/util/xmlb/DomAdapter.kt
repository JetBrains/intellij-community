// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb

import com.intellij.util.xml.dom.XmlElement
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
sealed interface DomAdapter<T : Any> {
  fun getTextValue(element: T, defaultText: String): String

  fun firstElement(element: T): T?

  fun getAttributeValue(element: T, name: String): String?

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

  override fun getAttributeValue(element: Element, name: String): String? = element.getAttributeValue(name)

  override fun getChildren(element: Element): List<Element> = element.children

  override fun getChild(element: Element, name: String): Element? = element.getChild(name)
}

internal data object XmlDomAdapter : DomAdapter<XmlElement> {
  override fun getName(element: XmlElement): String = element.name

  override fun getTextValue(element: XmlElement, defaultText: String): String = element.content ?: defaultText

  override fun firstElement(element: XmlElement): XmlElement? = element.children.firstOrNull()

  override fun getAttributeValue(element: XmlElement, name: String): String? = element.getAttributeValue(name)

  override fun getChildren(element: XmlElement): List<XmlElement> = element.children

  override fun getChild(element: XmlElement, name: String): XmlElement? = element.getChild(name)
}
