// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet")

package com.intellij.ide.util

import com.intellij.openapi.components.*
import com.intellij.openapi.diagnostic.logger
import org.jdom.Element
import org.jdom.Verifier
import org.jetbrains.annotations.NonNls
import java.util.concurrent.ConcurrentHashMap

private val LOG = logger<PropertiesComponentImpl>()

private const val ELEMENT_PROPERTY = "property"
private const val ATTRIBUTE_NAME = "name"
private const val ATTRIBUTE_VALUE = "value"

open class PropertiesComponentImpl internal constructor() : PropertiesComponent(), PersistentStateComponent<Element?> {
  private val keyToValue = ConcurrentHashMap<String, String>()

  val keys: Set<String>
    get() = keyToValue.keys

  private fun doPut(key: String, value: String) {
    Verifier.checkCharacterData(key)?.let(LOG::error)
    if (keyToValue.put(key, value) !== value) {
      incModificationCount()
    }
  }

  override fun getState(): Element? {
    val parentNode = Element("state")
    val keys = ArrayList<String>(keyToValue.keys)
    keys.sort()
    for (key in keys) {
      val value = keyToValue.get(key)
      if (value != null) {
        val element = Element(ELEMENT_PROPERTY)
        element.setAttribute(ATTRIBUTE_NAME, key)
        element.setAttribute(ATTRIBUTE_VALUE, value)
        parentNode.addContent(element)
      }
    }
    return parentNode
  }

  override fun loadState(parentNode: Element) {
    keyToValue.clear()
    for (e in parentNode.getChildren(ELEMENT_PROPERTY)) {
      val name = e.getAttributeValue(ATTRIBUTE_NAME) ?: continue
      keyToValue.put(name, e.getAttributeValue(ATTRIBUTE_VALUE) ?: continue)
    }
  }

  override fun getValue(name: String): String? = keyToValue.get(name)

  override fun setValue(name: String, value: String?) {
    if (value == null) {
      unsetValue(name)
    }
    else {
      doPut(name, value)
    }
  }

  override fun setValue(name: String, value: String?, defaultValue: String?) {
    if (value == null || value == defaultValue) {
      unsetValue(name)
    }
    else {
      doPut(name, value)
    }
  }

  override fun setValue(name: String, value: Float, defaultValue: Float) {
    if (value == defaultValue) {
      unsetValue(name)
    }
    else {
      doPut(name, value.toString())
    }
  }

  override fun setValue(name: String, value: Int, defaultValue: Int) {
    if (value == defaultValue) {
      unsetValue(name)
    }
    else {
      doPut(name, value.toString())
    }
  }

  override fun setValue(name: String, value: Boolean, defaultValue: Boolean) {
    if (value == defaultValue) {
      unsetValue(name)
    }
    else {
      setValue(name, value.toString())
    }
  }

  override fun unsetValue(name: String) {
    if (keyToValue.remove(name) != null) {
      incModificationCount()
    }
  }

  override fun isValueSet(name: String) = keyToValue.containsKey(name)

  override fun getValues(name: @NonNls String): Array<String>? {
    return getValue(name)?.split("\n")?.dropLastWhile { it.isEmpty() }?.toTypedArray()
  }

  override fun setValues(name: @NonNls String, values: Array<String>?) {
    if (values == null) {
      setValue(name, null)
    }
    else {
      setValue(name, values.joinToString(separator = "\n"))
    }
  }
}

@State(name = "PropertiesComponent", storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class ProjectPropertiesComponentImpl : PropertiesComponentImpl()

@State(name = "PropertiesComponent", storages = [Storage(value = StoragePathMacros.NON_ROAMABLE_FILE, roamingType = RoamingType.DISABLED)])
internal class AppPropertiesComponentImpl : PropertiesComponentImpl()