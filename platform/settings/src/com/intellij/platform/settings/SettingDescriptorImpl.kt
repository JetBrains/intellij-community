// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

//todo this will be injected by ComponentManager (a client will request it from a coroutine scope as a service),
// pluginId will be from coroutine scope
@ApiStatus.Internal
fun settingDescriptorFactoryFactory(pluginId: PluginId): SettingDescriptorFactory = SettingDescriptorFactoryImpl(pluginId)

private class SettingDescriptorFactoryImpl(private val pluginId: PluginId) : SettingDescriptorFactory {
  override fun settingDescriptor(key: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<String> {
    return settingDescriptor(key = key, pluginId = pluginId, block = block)
  }

  override fun <T : Any> settingDescriptor(key: String,
                                           serializer: SettingValueSerializer<T>,
                                           block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<T> {
    return settingDescriptor(key = key, pluginId = pluginId, serializer = serializer, block = block)
  }

  override fun group(key: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptorTemplateFactory {
    return SettingDescriptorTemplateFactoryImpl(key = key, pluginId = pluginId, block = block)
  }
}

private class SettingDescriptorTemplateFactoryImpl(
  private val pluginId: PluginId,
  private val key: String,
  private val block: SettingDescriptor.Builder.() -> Unit,
) : SettingDescriptorTemplateFactory {
  override fun <T : Any> setting(subKey: String, serializer: SettingValueSerializer<T>): Setting<T> {
    return SettingImpl(group = this, subKey = "$key.$subKey", serializer = serializer)
  }

  fun <T : Any> createSettingDescriptor(subKey: String, serializer: SettingValueSerializer<T>): SettingDescriptor<T> {
    return settingDescriptor(key = "$key.$subKey", pluginId = pluginId, serializer = serializer, block = block)
  }
}

//todo SettingsController will be injected by ComponentManager, will be no `serviceAsync<SettingsController>()`
// or we will request it from passed coroutine scope (to achieve lazy service loading), but definitely not from a global scope
private class SettingImpl<T : Any>(
  private val serializer: SettingValueSerializer<T>,
  private val group: SettingDescriptorTemplateFactoryImpl,
  private val subKey: String,
) : Setting<T> {
  private val settingDescriptor by lazy(LazyThreadSafetyMode.NONE) {
    group.createSettingDescriptor(subKey, serializer)
  }

  override suspend fun get(): T? = serviceAsync<SettingsController>().getItem(settingDescriptor)

  override suspend fun set(value: T?) = serviceAsync<SettingsController>().setItem(settingDescriptor, value)
}