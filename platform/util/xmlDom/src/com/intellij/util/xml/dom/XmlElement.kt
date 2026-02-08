// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.util.xml.dom

import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus
import java.util.Collections

// defaults for kotlinx serialization
@ApiStatus.Internal
@Serializable
data class XmlElement(
  @JvmField val name: String,
  @JvmField val attributes: Map<String, String> = Collections.emptyMap(),
  @JvmField val children: List<XmlElement> = Collections.emptyList(),
  @JvmField val content: String? = null,
) {
  fun count(name: String): Int = children.count { it.name == name }

  fun getAttributeValue(name: String): String? = attributes.get(name)

  fun getAttributeValue(name: String, defaultValue: String?): String? = attributes.get(name) ?: defaultValue

  fun getChild(name: String): XmlElement? = children.firstOrNull { it.name == name }

  // should not be used - uncomment for migration
  //fun getChildren(name: String): List<XmlElement> = children.filter { it.name == name }

  fun children(name: String): Sequence<XmlElement> = children.asSequence().filter { it.name == name }
}