// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.lang

import com.intellij.openapi.util.text.StringUtil
import org.jdom.Element
import java.util.*

internal class PerFileMappingState(var url: String, var value: String? = null) {
  companion object {
    @JvmStatic
    fun write(list: List<PerFileMappingState>, valueAttributeName: String): Element {
      val element = Element("state")
      for (entry in list) {
        val value = entry.value
        if (value == null) {
          continue
        }

        val entryElement = Element("file")
        entryElement.setAttribute("url", entry.url)
        entryElement.setAttribute(valueAttributeName, value)
        element.addContent(entryElement)
      }
      return element
    }

    @JvmStatic
    fun read(element: Element, valueAttributeName: String): List<PerFileMappingState> {
      val entries = element.getChildren("file")
      if (entries.isEmpty()) {
        return emptyList()
      }

      val result = ArrayList<PerFileMappingState>()
      for (child in entries) {
        val url = child.getAttributeValue("url")
        val value = child.getAttributeValue(valueAttributeName)
        if (StringUtil.isEmpty(url) || value == null) {
          continue
        }

        result.add(PerFileMappingState(url!!, value))
      }
      return result
    }
  }
}

