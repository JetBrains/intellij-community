// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.xml.dom

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
data class XmlElement(
  @JvmField val name: String,
  @JvmField val attributes: Map<String, String>,
  @JvmField val children: List<XmlElement>,
  @JvmField val content: String?,
) {
  fun count(name: String): Int = children.count { it.name == name }

  fun getAttributeValue(name: String): String? = attributes[name]

  fun getAttributeValue(name: String, defaultValue: String?): String? = attributes[name] ?: defaultValue

  fun getChild(name: String): XmlElement? = children.firstOrNull { it.name == name }

  // should not be used - uncomment for migration
  fun getChildren(name: String): List<XmlElement> = children.filter { it.name == name }
}