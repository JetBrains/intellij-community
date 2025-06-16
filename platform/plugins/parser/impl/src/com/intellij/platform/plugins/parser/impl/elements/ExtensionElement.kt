// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl.elements

import com.intellij.util.xml.dom.XmlElement

class ExtensionElement(
   @JvmField val implementation: String?,
   @JvmField val os: OS?,
   @JvmField val orderId: String?,
   // TODO return it back to parsed LoadingOrder after extracting the parser into a separate module
   @JvmField val order: String?,
   @JvmField val element: XmlElement?,
   @JvmField val hasExtraAttributes: Boolean,
) {
  override fun toString(): String {
    return buildString {
      append("ExtensionElement(implementation=$implementation")
      if (os != null) append(", os=$os")
      if (orderId != null) append(", orderId=$orderId")
      if (order != null) append(", order=$order")
      if (hasExtraAttributes) append(", hasExtraAttributes=true")
      if (element != null) append(", element=$element")
      append(")")
    }
  }
}