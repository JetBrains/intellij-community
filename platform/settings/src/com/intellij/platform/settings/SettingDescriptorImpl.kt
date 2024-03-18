// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.serializer
import org.jetbrains.annotations.ApiStatus.Internal
import java.lang.invoke.MethodHandles
import java.lang.invoke.MethodType

//todo this will be injected by ComponentManager (a client will request it from a coroutine scope as a service),
// pluginId will be from coroutine scope
@Internal
fun settingDescriptorFactory(pluginId: PluginId): SettingDescriptorFactory {
  return SettingDescriptorFactoryImpl(pluginId, ApplicationManager.getApplication().getService(SettingsController::class.java))
}

private class SettingDescriptorFactoryImpl(
  private val pluginId: PluginId,
  private val controller: SettingsController,
) : SettingDescriptorFactory {
  override fun settingDescriptor(key: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<String> {
    return SettingDescriptor.Builder().apply(block = block).build(key = key, pluginId = pluginId, serializer = StringSettingSerializerDescriptor)
  }

  override fun <T : Any> settingDescriptor(key: String,
                                           serializer: SettingSerializerDescriptor<T>,
                                           block: SettingDescriptor.Builder.() -> Unit): SettingDescriptor<T> {
    return settingDescriptor(key = key, pluginId = pluginId, serializer = serializer, block = block)
  }

  override fun group(groupKey: String, block: SettingDescriptor.Builder.() -> Unit): SettingDescriptorTemplateFactory {
    return SettingDescriptorTemplateFactoryImpl(prefix = groupKey, pluginId = pluginId, controller = controller, block = block)
  }

  override fun <T : Any> objectSerializer(aClass: Class<T>): SettingSerializerDescriptor<T> {
    return ObjectSettingSerializerDescriptor(aClass)
  }

  override fun <K : Any, V> mapSerializer(keyClass: Class<K>, valueClass: Class<V>): SettingSerializerDescriptor<Map<K, V>> {
    return MapSettingSerializerDescriptor(keyClass, valueClass)
  }
}

@Internal
interface SettingValueSerializer<T : Any> {
  val serializer: KSerializer<T>
}

// see KotlinxSerializationBinding
private val lookup = MethodHandles.lookup()
private val kotlinMethodType = MethodType.methodType(KSerializer::class.java)

// reflection-free
private fun <T> resolveKotlinSerializer(aClass: Class<T>): KSerializer<T> {
  val classLoader = aClass.classLoader
  if (classLoader == null || aClass.isPrimitive || aClass.isEnum) {
    // string
    @Suppress("UNCHECKED_CAST")
    return serializer(aClass) as KSerializer<T>
  }

  val lookup = MethodHandles.privateLookupIn(aClass, lookup)
  val companionGetter = lookup.findStaticGetter(aClass, "Companion", classLoader.loadClass(aClass.name + "\$Companion"))
  val companion = companionGetter.invoke()
  @Suppress("UNCHECKED_CAST")
  return lookup.findVirtual(companion.javaClass, "serializer", kotlinMethodType).invoke(companion) as KSerializer<T>
}

private class ObjectSettingSerializerDescriptor<T : Any>(private val aClass: Class<T>) : SettingSerializerDescriptor<T>,
                                                                                         SettingValueSerializer<T> {
  override val serializer by lazy(LazyThreadSafetyMode.NONE) {
    resolveKotlinSerializer(aClass)
  }
}

private class MapSettingSerializerDescriptor<K : Any, V : Any?>(
  private val keyClass: Class<K>,
  private val valueClass: Class<V>,
) : SettingSerializerDescriptor<Map<K, V>>, SettingValueSerializer<Map<K, V>> {
  override val serializer by lazy(LazyThreadSafetyMode.NONE) {
    MapSerializer(resolveKotlinSerializer(keyClass), resolveKotlinSerializer(valueClass))
  }
}

private class SettingDescriptorTemplateFactoryImpl(
  private val pluginId: PluginId,
  private val prefix: String,
  private val controller: SettingsController,
  private val block: SettingDescriptor.Builder.() -> Unit,
) : SettingDescriptorTemplateFactory {
  override fun <T : Any> setting(subKey: String, serializer: SettingSerializerDescriptor<T>): Setting<T> {
    return SettingImpl(group = this, subKey = subKey, serializer = serializer, controller = controller)
  }

  fun <T : Any> createSettingDescriptor(subKey: String, serializer: SettingSerializerDescriptor<T>): SettingDescriptor<T> {
    return settingDescriptor(key = "$prefix.$subKey", pluginId = pluginId, serializer = serializer, block = block)
  }
}

//todo SettingsController will be injected by ComponentManager, will be no `serviceAsync<SettingsController>()`
// or we will request it from passed coroutine scope (to achieve lazy service loading), but definitely not from a global scope
private class SettingImpl<T : Any>(
  private val serializer: SettingSerializerDescriptor<T>,
  private val group: SettingDescriptorTemplateFactoryImpl,
  private val subKey: String,
  private val controller: SettingsController,
) : Setting<T> {
  private val settingDescriptor by lazy(LazyThreadSafetyMode.NONE) {
    group.createSettingDescriptor(subKey = subKey, serializer = serializer)
  }

  override fun get(): T? = controller.getItem(settingDescriptor)

  override suspend fun set(value: T?) = controller.setItem(settingDescriptor, value)
}

@Internal
object StringSettingSerializerDescriptor : SettingSerializerDescriptor<String>, SettingValueSerializer<String> {
  override val serializer: KSerializer<String> = serializer()

  override fun toString(): String = "StringSettingValueSerializer"
}