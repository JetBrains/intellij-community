// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog", "ConvertObjectToDataObject")

package com.intellij.platform.settings

import com.intellij.openapi.extensions.PluginId

inline fun <T : Any> settingDescriptor(key: String,
                                       pluginId: PluginId,
                                       serializer: SettingValueSerializer<T>,
                                       block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<T> {
  return SettingDescriptor.Builder().apply(block).build(key = key, pluginId = pluginId, serializer = serializer)
}

sealed interface Setting<T : Any> {
  suspend fun get(): T?

  suspend fun set(value: T?)
}

// Implementation note: API to use when you have a service. We create this service for each request to a service coroutine scope.
sealed interface SettingDescriptorFactory {
  fun settingDescriptor(key: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<String>

  fun <T : Any> settingDescriptor(key: String,
                                  serializer: SettingValueSerializer<T>,
                                  block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<T>

  fun group(key: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptorTemplateFactory

  /**
   * See [com.intellij.platform.settings.mapSerializer]
   */
  fun <T: Any> objectSerializer(aClass: Class<T>): SettingValueSerializer<T>

  /**
   * See [com.intellij.platform.settings.objectSerializer]
   */
  fun <K : Any, V: Any?> mapSerializer(keyClass: Class<K>, valueClass: Class<V>): SettingValueSerializer<Map<K, V>>
}

sealed interface SettingDescriptorTemplateFactory {
  fun <T : Any> setting(subKey: String, serializer: SettingValueSerializer<T>): Setting<T>

  fun setting(subKey: String): Setting<String> = setting(subKey, StringSettingValueSerializer)
}

class SettingDescriptor<T : Any> private constructor(
  /**
   * The key provided is not the final effective key. The plugin ID is automatically and implicitly prepended to it.
   */
  val key: String,
  val pluginId: PluginId,
  /**
   * The implementation of the settings controller may vary significantly.
   * Tags should be considered as hints.
   * A settings descriptor can possess an arbitrary list of tags, and a tag can be defined not solely by the IntelliJ Platform.
   */
  // Implementation note: It is declared as `Collection` instead of `List` to imply that the order should not affect the behavior.
  val tags: Collection<SettingTag>,
  val serializer: SettingValueSerializer<T>,
) {
  class Builder @PublishedApi internal constructor() {
    var tags: Collection<SettingTag> = java.util.List.of()

    @PublishedApi
    internal fun <T : Any> build(key: String, pluginId: PluginId, serializer: SettingValueSerializer<T>): SettingDescriptor<T> {
      return SettingDescriptor(
        key = key,
        pluginId = pluginId,
        serializer = serializer,
        tags = tags,
      )
    }
  }

  // We cannot use data class as we should not expose `copy` method (https://youtrack.jetbrains.com/issue/KT-29675)
  // It will not be possible to preserve backward compatibility for the `copy` method.
  override fun equals(other: Any?): Boolean {
    if (this === other) return true
    if (other !is SettingDescriptor<*>) return false

    if (key != other.key) return false
    if (pluginId != other.pluginId) return false
    if (tags != other.tags) return false
    if (serializer != other.serializer) return false

    return true
  }

  override fun hashCode(): Int {
    var result = key.hashCode()
    result = 31 * result + pluginId.hashCode()
    result = 31 * result + tags.hashCode()
    result = 31 * result + serializer.hashCode()
    return result
  }

  override fun toString() = "SettingDescriptor(key='$key', pluginId=$pluginId, tags=$tags, serializer=$serializer)"
}

/**
 * See [SettingDescriptor.tags].
 */
interface SettingTag

/**
 * Adapter to get setting value from [com.intellij.ide.util.PropertiesComponent] if not defined.
 */
class PropertyManagerAdapterTag(val oldKey: String) : SettingTag

/**
 * This is a hint indicating that the setting is non-shareable (meaning it's stored only on the local machine).
 * It functions more like a state than a setting (isn't manageable by a user).
 * This concept is used for storing certain flags, such as "dialog was shown".
 */
// Implementation note 1: We don't use a tag with an enum field like RoamingType, but rather a bare tag, as it's more concise.
// Clients can add multiple tags in any case, so there's no concern that conflicting tags may be specified for the same setting.
// Implementation note 2: stored in a config dir rather in a system dir (see CacheStateTag)
object NonShareableStateTag : SettingTag {
  override fun toString() = "NonShareableStateTag"
}

/**
 * Similar to [NonShareableStateTag], but acts as a cache-like state. It isn't crucial if the stored value is lost frequently.
 */
// Implementation note: stored in a system dir (see NonShareableStateTag)
object CacheStateTag : SettingTag {
  override fun toString() = "CacheStateTag"
}