// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.platform.settings

import com.intellij.openapi.extensions.PluginId

class SettingDescriptor<T : Any> private constructor(
  /**
   * The key provided is not the final effective key. The plugin ID is automatically and implicitly prepended to it.
   */
  val key: String,
  val pluginId: PluginId,
  /**
   * A settings descriptor can possess an arbitrary list of tags, and a tag can be defined not solely by the IntelliJ Platform.
   * The implementation of a settings controller may vary significantly.
   * Tags should be considered as hints.
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

  override fun toString() = "SettingDescriptor(key=$key, pluginId=$pluginId, tags=$tags, serializer=$serializer)"
}

sealed interface Setting<T : Any> {
  suspend fun get(): T?

  suspend fun set(value: T?)
}
