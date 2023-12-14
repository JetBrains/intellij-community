// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus.Internal

// Implementation note: API to use when you have a service. We create this service for each request to a service coroutine scope.
sealed interface SettingDescriptorFactory {
  fun settingDescriptor(key: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<String>

  fun <T : Any> settingDescriptor(key: String,
                                  serializer: SettingSerializerDescriptor<T>,
                                  block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<T>

  fun group(groupKey: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptorTemplateFactory

  /**
   * See [com.intellij.platform.settings.mapSerializer]
   */
  fun <T: Any> objectSerializer(aClass: Class<T>): SettingSerializerDescriptor<T>

  /**
   * See [com.intellij.platform.settings.objectSerializer]
   */
  fun <K : Any, V: Any?> mapSerializer(keyClass: Class<K>, valueClass: Class<V>): SettingSerializerDescriptor<Map<K, V>>
}

sealed interface SettingDescriptorTemplateFactory {
  fun <T : Any> setting(subKey: String, serializer: SettingSerializerDescriptor<T>): Setting<T>

  fun setting(subKey: String): Setting<String> = setting(subKey, StringSettingSerializerDescriptor)
}

@Internal
inline fun <T : Any> settingDescriptor(key: String,
                                       pluginId: PluginId,
                                       serializer: SettingSerializerDescriptor<T>,
                                       block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<T> {
  return SettingDescriptor.Builder().apply(block).build(key = key, pluginId = pluginId, serializer = serializer)
}