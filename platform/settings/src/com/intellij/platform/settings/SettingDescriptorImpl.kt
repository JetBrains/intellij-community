// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.PluginId
import org.jetbrains.annotations.ApiStatus

//todo this will be injected by ComponentManager (a client will request it from a coroutine scope as a service),
// pluginId will be from coroutine scope
@ApiStatus.Internal
fun settingDescriptorFactory(pluginId: PluginId): SettingDescriptorFactory = SettingDescriptorFactoryImpl(pluginId)

private class SettingDescriptorFactoryImpl(private val pluginId: PluginId) : SettingDescriptorFactory {
  override fun settingDescriptor(key: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<String> {
    return SettingDescriptor.Builder().apply(block = block).build(key = key, pluginId = pluginId, serializer = StringSettingValueSerializer)
  }

  override fun <T : Any> settingDescriptor(key: String,
                                           serializer: SettingValueSerializer<T>,
                                           block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<T> {
    return settingDescriptor(key = key, pluginId = pluginId, serializer = serializer, block = block)
  }

  override fun group(key: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptorTemplateFactory {
    return SettingDescriptorTemplateFactoryImpl(key = key, pluginId = pluginId, block = block)
  }

  override fun <T : Any> objectSerializer(aClass: Class<T>): SettingValueSerializer<T> {
    return ObjectSettingValueSerializer(aClass)
  }

  override fun <K : Any, V> mapSerializer(keyClass: Class<K>, valueClass: Class<V>): SettingValueSerializer<Map<K, V>> {
    return MapSettingValueSerializer(keyClass, valueClass)
  }
}

private class ObjectSettingValueSerializer<T : Any>(private val aClass: Class<T>) : SettingValueSerializer<T> {
  // don't resolve service as a part of service descriptor creation
  private val impl by lazy(LazyThreadSafetyMode.NONE) {
    ApplicationManager.getApplication().getService(ObjectSettingValueSerializerFactory::class.java).objectSerializer(aClass)
  }

  override fun encode(value: T): ByteArray = impl.encode(value)

  override fun decode(input: ByteArray): T = impl.decode(input)
}

private class MapSettingValueSerializer<K : Any, V : Any?>(
  keyClass: Class<K>,
  valueClass: Class<V>,
) : SettingValueSerializer<Map<K, V>> {
  private val impl by lazy(LazyThreadSafetyMode.NONE) {
    ApplicationManager.getApplication().getService(ObjectSettingValueSerializerFactory::class.java).mapSerializer(keyClass, valueClass)
  }

  override fun encode(value: Map<K, V>): ByteArray = impl.encode(value)

  override fun decode(input: ByteArray): Map<K, V> = impl.decode(input)
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

@Suppress("ConvertObjectToDataObject")
internal object StringSettingValueSerializer : SettingValueSerializer<String> {
  override fun encode(value: String): ByteArray = value.encodeToByteArray()

  override fun decode(input: ByteArray): String = input.decodeToString()

  override fun toString(): String = "StringSettingValueSerializer"
}