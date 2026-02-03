// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.plugins.parser.impl

import org.codehaus.stax2.XMLStreamReader2

internal object XmlReadUtils {
  fun getNullifiedContent(reader: XMLStreamReader2): String? = reader.elementText.trim().takeIf { !it.isEmpty() }
  fun getNullifiedAttributeValue(reader: XMLStreamReader2, i: Int): String? = reader.getAttributeValue(i).trim().takeIf { !it.isEmpty() }

  fun findAttributeValue(reader: XMLStreamReader2, name: String): String? {
    for (i in 0 until reader.attributeCount) {
      if (reader.getAttributeLocalName(i) == name) {
        return getNullifiedAttributeValue(reader, i)
      }
    }
    return null
  }
}