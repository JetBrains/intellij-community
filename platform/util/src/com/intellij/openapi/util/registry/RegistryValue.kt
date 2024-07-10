// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.util.registry

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColorHexUtil
import com.intellij.util.ArrayUtilRt
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
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
    return checkNotNull(get(key = key, defaultValue = null, isValue = true)) { key }
  }

  open fun asBoolean(): Boolean {
    var result = booleanCachedValue
    if (result == null) {
      result = get(key = key, defaultValue = "false", isValue = true).toBoolean()
      booleanCachedValue = result
    }
    return result
  }

  fun asInteger(): Int {
    var result = intCachedValue
    if (result == null) {
      result = computeInt()
      intCachedValue = result
    }
    return result
  }

  private fun computeInt(): Int {
    try {
      return get(key = key, defaultValue = "0", isValue = true)!!.toInt()
    }
    catch (e: NumberFormatException) {
      return registry.getBundleValue(key).toInt()
    }
  }

  val isMultiValue: Boolean
    get() = selectedOption != null

  val options: Array<String>
    get() = getOptions(registry.getBundleValue(key))

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
      val options = options
      for (i in options.indices) {
        options[i] = options[i].trimEnd('*')
        if (options[i] == selected) {
          options[i] += "*"
        }
      }
      setValue("[" + options.joinToString(separator = "|") + "]")
    }

  fun isOptionEnabled(option: String): Boolean = selectedOption == option

  fun asDouble(): Double {
    var result = doubleCachedValue
    if (result.isNaN()) {
      result = calcDouble()
      doubleCachedValue = result
    }
    return result
  }

  private fun calcDouble(): Double {
    try {
      return get(key = key, defaultValue = "0.0", isValue = true)!!.toDouble()
    }
    catch (e: NumberFormatException) {
      return registry.getBundleValue(key).toDouble()
    }
  }

  fun asColor(defaultValue: Color?): Color? {
    val s = get(key, null, true) ?: return defaultValue
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
    get() {
      if (keyDescriptor != null) {
        return keyDescriptor.description
      }
      return get("$key.description", "", false)!!
    }

  open val isRestartRequired: Boolean
    get() {
      if (keyDescriptor != null) {
        return keyDescriptor.isRestartRequired
      }
      return get("$key.restartRequired", "false", false).toBoolean()
    }

  open val isChangedFromDefault: Boolean
    get() = isChangedFromDefault(asString(), registry)

  val pluginId: String?
    get() = keyDescriptor?.pluginId

  fun isChangedFromDefault(newValue: String, registry: Registry): Boolean {
    return newValue != registry.getBundleValueOrNull(key)
  }

  @Throws(MissingResourceException::class)
  open fun get(key: @NonNls String, defaultValue: String?, isValue: Boolean): String? {
    if (isValue) {
      if (stringCachedValue == null) {
        stringCachedValue = _get(key = key, defaultValue = defaultValue, mustExistInBundle = true)
      }
      return stringCachedValue
    }
    return _get(key = key, defaultValue = defaultValue, mustExistInBundle = false)
  }

  @Suppress("FunctionName")
  @Throws(MissingResourceException::class)
  private fun _get(key: @NonNls String, defaultValue: String?, mustExistInBundle: Boolean): String? {
    registry.getUserProperties().get(key)?.let {
      return it
    }

    System.getProperty(key)?.let {
      return it
    }

    if (!registry.isLoaded) {
      val message = "Attempt to load key '$key' for not yet loaded registry"
      // use Disposer.isDebugMode as "we are in internal mode or inside test" flag
      if (Disposer.isDebugMode()) {
        LOG.error("$message. Use system properties instead of registry values to configure behaviour at early startup stages.")
      }
      else {
        LOG.warn(message)
      }
    }

    if (mustExistInBundle) {
      return registry.getBundleValue(key)
    }
    else {
      return registry.getBundleValueOrNull(key) ?: defaultValue
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
    for (each in listeners) {
      each.afterValueChanged(this)
    }

    if (!isChangedFromDefault && !isRestartRequired) {
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

  fun setValue(value: String, parentDisposable: Disposable) {
    val prev = asString()
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

  override fun toString(): String = "$key=${asString()}"

  fun resetCache() {
    stringCachedValue = null
    intCachedValue = null
    doubleCachedValue = Double.NaN
    booleanCachedValue = null
  }

  open val isBoolean: Boolean
    get() = isBoolean(asString())
}

private fun getOptions(value: String?): Array<String> {
  if (value != null && value.startsWith('[') && value.endsWith(']')) {
    return value.substring(1, value.length - 1).split("\\|").dropLastWhile { it.isEmpty() }.toTypedArray()
  }
  return ArrayUtilRt.EMPTY_STRING_ARRAY
}

private fun isBoolean(s: String): Boolean {
  return "true".equals(s, ignoreCase = true) || "false".equals(s, ignoreCase = true)
}
