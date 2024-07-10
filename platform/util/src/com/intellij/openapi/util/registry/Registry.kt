// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "ReplacePutWithAssignment")

package com.intellij.openapi.util.registry

import com.intellij.diagnostic.LoadingState
import com.intellij.openapi.util.NlsSafe
import kotlinx.coroutines.CompletableDeferred
import org.jdom.Element
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.TestOnly
import java.awt.Color
import java.io.IOException
import java.lang.ref.Reference
import java.lang.ref.SoftReference
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.Volatile

/**
 * Provides a UI to configure internal settings of the IDE.
 *
 *
 * Plugins can provide their own registry keys using the
 * `com.intellij.registryKey` extension point (see [com.intellij.openapi.util.registry.RegistryKeyBean] for more details).
 */
class Registry {
  private val userProperties = LinkedHashMap<String, String>()
  private val values = ConcurrentHashMap<String, RegistryValue>()
  private var contributedKeys = emptyMap<String, RegistryKeyDescriptor>()

  @Volatile
  var isLoaded: Boolean = false
    private set

  @Volatile
  private var loadFuture = CompletableFuture<Void?>()

  @Volatile
  var valueChangeListener: RegistryValueListener = EMPTY_VALUE_LISTENER
    private set

  companion object {
    private var bundledRegistry: Reference<Map<String, String>>? = null

    const val REGISTRY_BUNDLE: @NonNls String = "misc.registry"

    private val EMPTY_VALUE_LISTENER: RegistryValueListener = object : RegistryValueListener {
    }

    private val registry = Registry()

    @JvmStatic
    fun get(key: @NonNls String): RegistryValue = getInstance().doGet(key)

    @Suppress("FunctionName")
    @Internal
    @JvmStatic
    fun _getWithoutStateCheck(key: @NonNls String): RegistryValue = registry.doGet(key)

    @Throws(MissingResourceException::class)
    @JvmStatic
    fun `is`(key: @NonNls String): Boolean = get(key).asBoolean()

    @JvmStatic
    fun `is`(key: @NonNls String, defaultValue: Boolean): Boolean {
      if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
        return defaultValue
      }

      try {
        return registry.doGet(key).asBoolean()
      }
      catch (ignore: MissingResourceException) {
        return defaultValue
      }
    }

    @Throws(MissingResourceException::class)
    @JvmStatic
    fun intValue(key: @NonNls String): Int = getInstance().doGet(key).asInteger()

    @JvmStatic
    fun intValue(key: @NonNls String, defaultValue: Int): Int {
      if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
        LoadingState.COMPONENTS_REGISTERED.checkOccurred()
        return defaultValue
      }

      try {
        return registry.doGet(key).asInteger()
      }
      catch (ignore: MissingResourceException) {
        return defaultValue
      }
    }

    @JvmStatic
    fun doubleValue(key: @NonNls String, defaultValue: Double): Double {
      if (!LoadingState.COMPONENTS_LOADED.isOccurred) {
        LoadingState.COMPONENTS_REGISTERED.checkOccurred()
        return defaultValue
      }

      try {
        return registry.doGet(key).asDouble()
      }
      catch (ignore: MissingResourceException) {
        return defaultValue
      }
    }

    @Throws(MissingResourceException::class)
    @JvmStatic
    fun doubleValue(key: @NonNls String): Double = get(key).asDouble()

    @Throws(MissingResourceException::class)
    @JvmStatic
    fun stringValue(key: @NonNls String): String = get(key).asString()

    @Throws(MissingResourceException::class)
    @JvmStatic
    fun getColor(key: @NonNls String, defaultValue: Color?): Color = get(key).asColor(defaultValue)

    @Throws(IOException::class)
    private fun loadFromBundledConfig(): Map<String, String>? {
      bundledRegistry?.get()?.let {
        return it
      }

      val map: MutableMap<String, String> = LinkedHashMap(1800)
      val mainFound = loadFromResource("misc/registry.properties", map)
      val overrideFound = loadFromResource("misc/registry.override.properties", map)
      if (!mainFound && !overrideFound) {
        return null
      }

      bundledRegistry = SoftReference(map)
      return map
    }

    private fun loadFromResource(sourceResourceName: String, targetMap: MutableMap<String, String>): Boolean {
      val stream = Registry::class.java.classLoader.getResourceAsStream(sourceResourceName) ?: return false
      stream.use {
        object : Properties() {
          override fun put(key: Any, value: Any): Any? {
            return targetMap.put(key as String, value as String)
          }
        }.load(stream)
      }
      return true
    }

    @JvmStatic
    fun getInstance(): Registry {
      LoadingState.COMPONENTS_LOADED.checkOccurred()
      return registry
    }

    @JvmStatic
    fun intValue(key: @NonNls String, defaultValue: Int, minValue: Int, maxValue: Int): Int {
      require(!(defaultValue < minValue || defaultValue > maxValue)) {
        "Wrong values for default:min:max ($defaultValue:$minValue:$maxValue)"
      }
      return intValue(key, defaultValue).coerceIn(minValue, maxValue)
    }

    private fun fromState(state: Element): Map<String, String> {
      val map = LinkedHashMap<String, String>()
      for (eachEntry in state.getChildren("entry")) {
        val key = eachEntry.getAttributeValue("key") ?: continue
        val value = eachEntry.getAttributeValue("value") ?: continue
        map.put(key, value)
      }
      return map
    }

    private fun updateStateInternal(registry: Registry, state: Element?): Map<String, String> {
      val userProperties = registry.userProperties
      if (state == null) {
        userProperties.clear()
        return userProperties
      }

      val map = fromState(state)
      val keysToProcess = HashSet(userProperties.keys)
      for ((key, value) in map) {
        val registryValue = registry.doGet(key)
        val currentValue = registryValue.get(key, null, false)
        // currentValue == null means value is not in the bundle. Ignore it
        if (currentValue != null && currentValue != value) {
          registryValue.setValue(value)
        }
        keysToProcess.remove(key)
      }

      // keys that are not in the state; we need to reset them to default value
      for (key in keysToProcess) {
        registry.doGet(key).resetToDefault()
      }

      return userProperties
    }

    @Internal
    fun loadState(state: Element?, earlyAccess: Map<String, String>?): Map<String, String> {
      val registry = registry
      if (registry.isLoaded) {
        return updateStateInternal(registry, state)
      }
      else {
        return loadStateInternal(registry = registry, state = state, earlyAccess = earlyAccess)
      }
    }

    @Internal
    @JvmStatic
    fun markAsLoaded() {
      registry.isLoaded = true
      registry.loadFuture.complete(null)
    }

    fun awaitLoad(): CompletionStage<Void?> = registry.loadFuture

    @Internal
    @JvmStatic
    fun getAll(): List<RegistryValue> {
      var bundle: Map<String, String>? = null
      try {
        bundle = loadFromBundledConfig()
      }
      catch (ignored: IOException) {
      }
      val keys = bundle?.keys ?: emptySet()
      val result = ArrayList<RegistryValue>()
      // don't use getInstance here - https://youtrack.jetbrains.com/issue/IDEA-271748
      val instance = registry
      val contributedKeys = instance.contributedKeys
      for (key in keys) {
        if (key.endsWith(".description") || key.endsWith(".restartRequired") || contributedKeys.containsKey(key)) {
          continue
        }
        result.add(instance.doGet(key))
      }

      for (key in contributedKeys.keys) {
        result.add(instance.doGet(key))
      }

      return result
    }

    private fun isRestartNeeded(map: Map<String, String>): Boolean {
      val instance = getInstance()
      for (s in map.keys) {
        val eachValue = instance.doGet(s)
        if (eachValue.isRestartRequired && eachValue.isChangedSinceAppStart) {
          return true
        }
      }

      return false
    }

    @Internal
    @Synchronized
    fun setKeys(descriptors: Map<String, RegistryKeyDescriptor>) {
      // getInstance must be not used here - phase COMPONENT_REGISTERED is not yet completed
      registry.contributedKeys = descriptors
    }

    @Internal
    @Synchronized
    fun mutateContributedKeys(mutator: (Map<String, RegistryKeyDescriptor>) -> Map<String, RegistryKeyDescriptor>) {
      // getInstance must be not used here - phase COMPONENT_REGISTERED is not yet completed
      registry.contributedKeys = mutator(registry.contributedKeys)
    }

    @Internal
    fun setValueChangeListener(listener: RegistryValueListener?) {
      registry.valueChangeListener = listener ?: EMPTY_VALUE_LISTENER
    }

    private fun loadStateInternal(
      registry: Registry,
      state: Element?,
      earlyAccess: Map<String, String>?
    ): Map<String, String> {
      val userProperties = registry.userProperties
      userProperties.clear()
      if (state != null) {
        val map = fromState(state)
        for ((key, value) in map) {
          val registryValue = registry.doGet(key)
          if (registryValue.isChangedFromDefault(value, registry)) {
            userProperties[key] = value
            registryValue.resetCache()
          }
        }
      }

      if (earlyAccess != null) {
        // yes, earlyAccess overrides user properties
        userProperties.putAll(earlyAccess)
      }
      registry.isLoaded = true
      registry.loadFuture.complete(null)
      return userProperties
    }
  }

  private fun doGet(key: @NonNls String): RegistryValue {
    return values.computeIfAbsent(key) {
      RegistryValue(this, it, contributedKeys.get(it))
    }
  }

  @TestOnly
  fun reset() {
    userProperties.clear()
    values.clear()
    isLoaded = false
    loadFuture.cancel(false)
    loadFuture = CompletableFuture()
  }

  fun getBundleValueOrNull(key: @NonNls String): @NlsSafe String? {
    contributedKeys.get(key)?.let {
      return it.defaultValue
    }

    return loadFromBundledConfig()?.get(key)
  }

  @Throws(MissingResourceException::class)
  fun getBundleValue(key: @NonNls String): @NlsSafe String {
    contributedKeys.get(key)?.let {
      return it.defaultValue
    }

    return getBundleValueOrNull(key) ?: throw MissingResourceException("Registry key $key is not defined", REGISTRY_BUNDLE, key)
  }

  fun getState(): Element {
    val state = Element("registry")
    for ((key, value) in userProperties) {
      val registryValue = getInstance().doGet(key)
      if (registryValue.isChangedFromDefault) {
        val entryElement = Element("entry")
        entryElement.setAttribute("key", key)
        entryElement.setAttribute("value", value)
        state.addContent(entryElement)
      }
    }
    return state
  }

  @Internal
  fun getUserProperties(): MutableMap<String, String> = userProperties

  fun restoreDefaults() {
    val old = LinkedHashMap(userProperties)
    val registry = getInstance()
    for (key in old.keys) {
      val v = registry.getBundleValueOrNull(key)
      if (v == null) {
        // outdated property that is not declared in registry.properties anymore
        values.remove(key)
      }
      else {
        registry.values.get(key)?.setValue(v)
      }
    }
  }

  val isInDefaultState: Boolean
    get() = userProperties.isEmpty()

  val isRestartNeeded: Boolean
    get() = isRestartNeeded(userProperties)
}
