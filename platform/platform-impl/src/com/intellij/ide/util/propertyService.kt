// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplacePutWithAssignment", "ReplaceGetOrSet", "ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.ide.util

import com.intellij.openapi.components.PersistentStateComponentWithModificationTracker
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.util.SimpleModificationTracker
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.function.BiConsumer
import java.util.function.Predicate

@Internal
sealed class BasePropertyService : PropertiesComponent(), PersistentStateComponentWithModificationTracker<BasePropertyService.MyState> {
  private val tracker = SimpleModificationTracker()

  @Serializable
  data class MyState(
    val keyToString: Map<String, String> = emptyMap(),
    val keyToStringList: Map<String, List<String>> = emptyMap()
  )

  private val keyToString = ConcurrentHashMap<String, String>()
  private val keyToStringList = ConcurrentHashMap<String, List<String>>()

  override fun getStateModificationCount() = tracker.modificationCount

  fun removeIf(predicate: Predicate<String>) {
    keyToString.keys.removeIf(predicate)
  }

  fun forEachPrimitiveValue(consumer: BiConsumer<String, String>) {
    keyToString.forEach(consumer)
  }

  private fun doPut(key: String, value: String) {
    if (keyToString.put(key, value) !== value) {
      tracker.incModificationCount()
    }
  }

  override fun getState() = MyState(TreeMap(keyToString), TreeMap(keyToStringList))

  override fun loadState(state: MyState) {
    keyToString.clear()
    keyToString.putAll(state.keyToString)
    keyToStringList.putAll(state.keyToStringList)
  }

  override fun getValue(name: String): String? = keyToString.get(name)

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
    if (keyToString.remove(name) != null) {
      tracker.incModificationCount()
    }
  }

  override fun isValueSet(name: String) = keyToString.containsKey(name)

  override fun getValues(name: @NonNls String) = getList(name)?.toTypedArray()

  override fun setValues(name: @NonNls String, values: Array<String>?) {
    if (values.isNullOrEmpty()) {
      unsetValue(name)
    }
    else {
      keyToStringList.put(name, java.util.List.of(*values))
      tracker.incModificationCount()
    }
  }

  override fun getList(name: String) = keyToStringList.get(name)

  override fun setList(name: String, values: MutableCollection<String>?) {
    if (values.isNullOrEmpty()) {
      unsetValue(name)
    }
    else {
      keyToStringList.put(name, java.util.List.copyOf(values))
      tracker.incModificationCount()
    }
  }
}

@State(name = "PropertyService", reportStatistic = false, storages = [
  Storage(value = StoragePathMacros.NON_ROAMABLE_FILE),
  Storage(value = StoragePathMacros.CACHE_FILE, deprecated = true),
])
@Internal
class AppPropertyService : BasePropertyService()

@State(name = "PropertiesComponent", reportStatistic = false, storages = [Storage(StoragePathMacros.WORKSPACE_FILE)])
internal class ProjectPropertyService : BasePropertyService()