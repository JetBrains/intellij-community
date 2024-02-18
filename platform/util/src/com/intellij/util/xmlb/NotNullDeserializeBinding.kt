// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xmlb

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.diagnostic.logger
import com.intellij.util.xml.dom.XmlElement
import org.jdom.Element

@JvmField
internal val LOG: Logger = logger<Binding>()

interface NotNullDeserializeBinding : Binding {
  fun deserialize(context: Any?, element: Element): Any

  fun deserialize(context: Any?, element: XmlElement): Any

  override fun deserializeUnsafe(context: Any?, element: Element): Any = deserialize(context = context, element = element)

  override fun deserializeUnsafe(context: Any?, element: XmlElement): Any = deserialize(context = context, element = element)
}
