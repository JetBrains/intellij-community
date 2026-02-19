// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.io.Reader
import java.util.Collections
import java.util.Enumeration
import java.util.Properties
import java.util.ResourceBundle

@ApiStatus.Internal
class IntelliJResourceBundle internal constructor(reader: Reader) : ResourceBundle() {
  private val lookup: Map<String, String>

  init {
    val properties = Properties()
    properties.load(reader)
    // don't use immutable map - HashMap performance is better (with String keys)
    @Suppress("UNCHECKED_CAST")
    lookup = HashMap(properties as Map<String, String>)
  }

  val parent: ResourceBundle?
    get() = super.parent

  @NlsSafe
  fun getMessageOrNull(key: String): String? {
    val value = lookup.get(key) ?: return null
    return postProcessResolvedValue(value = value, key = key, resourceFound = true, bundle = this)
  }

  @NlsSafe
  fun getMessage(key: String, defaultValue: @Nls String?, params: Array<out Any?>?): String {
    @Suppress("HardCodedStringLiteral")
    var value = lookup.get(key)
    val resourceFound = if (value == null) {
      value = defaultValue ?: useDefaultValue(bundle = this, key = key)
      false
    }
    else {
      true
    }

    return postProcessResolvedValue(
      value = postprocessValue(bundle = this, value = value, params = params),
      key = key,
      resourceFound = resourceFound,
      bundle = this,
    )
  }

  // UI Designer uses ResourceBundle directly, via Java API.
  // `getMessage` is not called, so we have to provide our own implementation of ResourceBundle
  override fun handleGetObject(key: String): String? = getMessageOrNull(key = key)

  override fun getKeys(): Enumeration<String> {
    val parent = super.parent
    return if (parent == null) Collections.enumeration(lookup.keys) else ResourceBundleWithParentEnumeration(lookup.keys, parent.keys)
  }

  override fun handleKeySet(): Set<String> = lookup.keys
}

private class ResourceBundleWithParentEnumeration(
  private var set: Set<String?>,
  private var enumeration: Enumeration<String>,
) : Enumeration<String> {
  private var iterator: Iterator<String?> = set.iterator()
  private var next: String? = null

  override fun hasMoreElements(): Boolean {
    if (next == null) {
      if (iterator.hasNext()) {
        next = iterator.next()
      }
      else {
        while (next == null && enumeration.hasMoreElements()) {
          next = enumeration.nextElement()
          if (set.contains(next)) {
            next = null
          }
        }
      }
    }
    return next != null
  }

  override fun nextElement(): String? {
    if (hasMoreElements()) {
      val result = next
      next = null
      return result
    }
    else {
      throw NoSuchElementException()
    }
  }
}