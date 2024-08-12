// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.util.registry

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorHexUtil
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.Color
import java.util.*

private val LOG = logger<RegistryValue>()

open class RegistryValue @Internal constructor(
  private val registry: Registry,
  val key: @NonNls String,
  private val keyDescriptor: RegistryKeyDescriptor?
) {
  private val listeners: MutableList<RegistryValueListener> = ContainerUtil.createLockFreeCopyOnWriteList()

  var isChangedSinceAppStart: Boolean = false
    private set

  private var stringCachedValue: String? = null
  private var intCachedValue: Int? = null
  private var doubleCachedValue = Double.NaN
  private var booleanCachedValue: Boolean? = null

  open fun asString(): @NlsSafe String {
    var result = stringCachedValue
    if (result == null) {
      result = resolveRequiredValue(key)
      stringCachedValue = result
    }
    return result
  }

  open fun asBoolean(): Boolean {
    var result = booleanCachedValue
    if (result == null) {
      result = resolveRequiredValue(key = key).toBoolean()
      booleanCachedValue = result
    }
    return result
  }

  fun asInteger(): Int {
    var result = intCachedValue
    if (result == null) {
      result = try {
        resolveRequiredValue(key = key).toInt()
      }
      catch (e: NumberFormatException) {
        registry.getBundleValue(key, keyDescriptor).toInt()
      }
      intCachedValue = result!!
    }
    return result
  }

  val isMultiValue: Boolean
    get() = selectedOption != null

  fun asOptions(): List<String> {
    val value = registry.getBundleValue(key, keyDescriptor)
    if (value.startsWith('[') && value.endsWith(']')) {
      return value.substring(1, value.length - 1).split("|").dropLastWhile { it.isEmpty() }
    }
    return emptyList()
  }

  var selectedOption: @NlsSafe String?
    get() {
      // [opt1|opt2|selectedOpt*|opt4]
      val value = asString()
      val length = value.length
      if (length < 3 || value[0] != '[' || value[length - 1] != ']') return null
      var pos = 1
      while (pos < length) {
        var end = value.indexOf('|', pos)
        if (end == -1) {
          end = length - 1
        }
        if (value[end - 1] == '*') {
          return value.substring(pos, end - 1)
        }
        pos = end + 1
      }
      return null
    }
    set(selected) {
      val options = asOptions().toMutableList()
      for ((i, option) in options.withIndex()) {
        val v = option.trimEnd('*')
        options.set(i, v)
        if (v == selected) {
          options.set(i, v.plus("*"))
        }
      }
      setValue("[" + options.joinToString(separator = "|") + "]")
    }

  fun isOptionEnabled(option: String): Boolean = selectedOption == option

  fun asDouble(): Double {
    var result = doubleCachedValue
    if (result.isNaN()) {
      result = computeDouble()
      doubleCachedValue = result
    }
    return result
  }

  private fun computeDouble(): Double {
    return resolveNotRequiredValue(key)?.toDoubleOrNull()
           ?: keyDescriptor?.defaultValue?.toDouble()
           ?: registry.getBundleValueOrNull(key)?.toDouble()
           ?: 0.0
  }

  fun asColor(defaultValue: Color?): Color? {
    val s = getAsValue(key) ?: return defaultValue
    val color = ColorHexUtil.fromHex(s, null)
    if (color != null && (key.endsWith(".color") || key.endsWith(".color.dark") || key.endsWith(".color.light"))) {
      return color
    }

    val rgb = s.split(',').dropLastWhile { it.isEmpty() }
    if (rgb.size == 3) {
      try {
        return Color(rgb[0].toInt(), rgb[1].toInt(), rgb[2].toInt())
      }
      catch (ignored: Exception) {
      }
    }
    return defaultValue
  }

  open val description: @NlsSafe String
    get() = keyDescriptor?.description ?: resolveNotRequiredValue(key = "$key.description") ?: ""

  open fun isRestartRequired(): Boolean {
    if (keyDescriptor == null) {
      return resolveNotRequiredValue(key = "$key.restartRequired").toBoolean()
    }
    else {
      return keyDescriptor.isRestartRequired
    }
  }

  open fun isChangedFromDefault(): Boolean {
    return (stringCachedValue ?: resolveNotRequiredValue(key)) != registry.getBundleValueOrNull(key)
  }

  val pluginId: String?
    get() = keyDescriptor?.pluginId

  private fun getAsValue(key: @NonNls String): String? {
    if (stringCachedValue == null) {
      stringCachedValue = resolveNotRequiredValue(key)
    }
    return stringCachedValue?.takeIf { it.isNotEmpty() }
  }

  @Internal
  fun resolveNotRequiredValue(key: @NonNls String): String? {
    registry.getUserProperties().get(key)?.let {
      return it
    }

    System.getProperty(key)?.let {
      return it
    }

    checkIsLoaded(key)
    return registry.getBundleValueOrNull(key)
  }

  @Throws(MissingResourceException::class)
  private fun resolveRequiredValue(key: @NonNls String): String {
    registry.getUserProperties().get(key)?.let {
      return it
    }

    System.getProperty(key)?.let {
      return it
    }

    checkIsLoaded(key)
    return registry.getBundleValue(key, keyDescriptor)
  }

  private fun checkIsLoaded(key: @NonNls String) {
    if (registry.isLoaded) {
      return
    }

    val message = "Attempt to load key '$key' for not yet loaded registry"
    // use Disposer.isDebugMode as "we are in internal mode or inside test" flag
    if (Disposer.isDebugMode()) {
      LOG.error("$message. Use system properties instead of registry values to configure behaviour at early startup stages.")
    }
    else {
      LOG.warn(message)
    }
  }

  fun setValue(value: Boolean) {
    setValue(value.toString())
  }

  fun setValue(value: Int) {
    setValue(value.toString())
  }

  open fun setValue(value: String) {
    val globalValueChangeListener = registry.valueChangeListener
    globalValueChangeListener.beforeValueChanged(this)
    for (each in listeners) {
      each.beforeValueChanged(this)
    }
    resetCache()
    registry.getUserProperties().put(key, value)
    LOG.info("Registry value '$key' has changed to '$value'")

    globalValueChangeListener.afterValueChanged(this)
    for (listener in listeners) {
      listener.afterValueChanged(this)
    }

    if (!isRestartRequired() && resolveNotRequiredValue(key) == registry.getBundleValueOrNull(key)) {
      registry.getUserProperties().remove(key)
    }

    isChangedSinceAppStart = true
  }

  fun setValue(value: Boolean, parentDisposable: Disposable) {
    val prev = asBoolean()
    setValue(value)
    Disposer.register(parentDisposable) { setValue(prev) }
  }

  fun setValue(value: Int, parentDisposable: Disposable) {
    val prev = asInteger()
    setValue(value)
    Disposer.register(parentDisposable) { setValue(prev) }
  }

  @TestOnly
  fun setValue(value: String, parentDisposable: Disposable) {
    val prev = stringCachedValue ?: resolveRequiredValue(key)
    setValue(value)
    Disposer.register(parentDisposable) { setValue(prev) }
  }

  fun resetToDefault() {
    val value = registry.getBundleValueOrNull(key)
    if (value == null) {
      registry.getUserProperties().remove(key)
    }
    else {
      setValue(value)
    }
  }

  fun addListener(listener: RegistryValueListener, parent: Disposable) {
    listeners.add(listener)
    Disposer.register(parent) { listeners.remove(listener) }
  }

  fun addListener(listener: RegistryValueListener, coroutineScope: CoroutineScope) {
    listeners.add(listener)
    coroutineScope.coroutineContext.get(Job)!!.invokeOnCompletion {
      listeners.remove(listener)
    }
  }

  internal fun resetCache() {
    stringCachedValue = null
    intCachedValue = null
    doubleCachedValue = Double.NaN
    booleanCachedValue = null
  }

  open val isBoolean: Boolean
    get() = isBoolean(asString())

  override fun toString(): String = "$key=${asString()}"
}

private fun isBoolean(s: String): Boolean {
  return "true".equals(s, ignoreCase = true) || "false".equals(s, ignoreCase = true)
}
