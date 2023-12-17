// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.extensions.PluginId
import com.intellij.platform.settings.*
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

@Suppress("RemoveExplicitTypeArguments")
@TestApplication
class LocalSettingsControllerTest {
  private val factory = settingDescriptorFactory(PluginManagerCore.CORE_ID)

  @BeforeEach
  fun clear() {
    clearCacheStore()
  }

  @Test
  fun `cache string value state`() = runBlocking<Unit> {
    val controller = serviceAsync<SettingsController>()
    val settingsDescriptor = factory.settingDescriptor("test.flag") {
      tags = listOf(CacheTag)
    }

    assertThat(controller.getItem(settingsDescriptor)).isNull()
    controller.setItem(settingsDescriptor, "test")
    assertThat(controller.getItem(settingsDescriptor)).isEqualTo("test")

    // test compact
    compactCacheStore()
  }

  @Test
  fun `same keys but different plugins`() = runBlocking<Unit> {
    val controller = serviceAsync<SettingsController>()
    val descriptors = sequenceOf("foo", "bar")
      .map { id ->
        settingDescriptorFactory(PluginId.getId(id)).settingDescriptor("test.flag") {
          tags = listOf(CacheTag)
        }
      }
      .toList()
    for (descriptor in descriptors) {
      assertThat(controller.getItem(descriptor)).isNull()
      controller.setItem(descriptor, "${descriptor.pluginId} is my creator")
    }
    for (descriptor in descriptors) {
      assertThat(controller.getItem(descriptor)).isEqualTo("${descriptor.pluginId} is my creator")
    }
  }

  @Test
  fun `cache object value state`() = runBlocking<Unit> {
    val controller = serviceAsync<SettingsController>()
    val settingsDescriptor = factory.settingDescriptor("test.flag", factory.objectSerializer<CustomObject>()) {
      tags = listOf(CacheTag)
    }

    assertThat(controller.getItem(settingsDescriptor)).isNull()

    val cats = CustomObject(cats = listOf("red", "blue", "white"))
    controller.setItem(settingsDescriptor, cats)
    assertThat(controller.getItem(settingsDescriptor)).isEqualTo(cats)
  }

  @Test
  fun `cache object value with missing properties`() = runBlocking<Unit> {
    val controller = serviceAsync<SettingsController>()
    val settingDescriptor = factory.settingDescriptor("test.flag", factory.objectSerializer<ObjectV1>()) {
      tags = listOf(CacheTag)
    }

    assertThat(controller.getItem(settingDescriptor)).isNull()

    val cats = ObjectV1(foo = "blue", uselessBar = "remove me")
    controller.setItem(settingDescriptor, cats)
    assertThat(controller.getItem(factory.settingDescriptor("test.flag", factory.objectSerializer<ObjectV2>()) {
      tags = listOf(CacheTag)
    }
    )).isEqualTo(ObjectV2(foo = "blue"))
  }

  @Test
  fun `cache object map value state`() = runBlocking<Unit> {
    val factory = settingDescriptorFactory(PluginManagerCore.CORE_ID)

    val controller = serviceAsync<SettingsController>()
    val settingDescriptor = factory.settingDescriptor("test.flag", factory.mapSerializer<String, String>()) {
      tags = listOf(CacheTag)
    }

    assertThat(controller.getItem(settingDescriptor)).isNull()

    val map = java.util.Map.of("foo", "12", "bar", "42")
    controller.setItem(settingDescriptor, map)
    assertThat(controller.getItem(settingDescriptor)).isEqualTo(map)

    corruptValue("test.flag", controller)
    try {
      controller.getItem(settingDescriptor)
    }
    catch (e: Throwable) {
      assertThat(e.message).startsWith("Cannot deserialize value for key com.intellij.test.flag (size=4096, value will be stored under key com.intellij.test.flag.__corrupted__) ")
    }

    assertThat(controller.getItem(settingDescriptor)).isNull()
    assertThat(controller.getItem(settingDescriptor("test.flag.__corrupted__", PluginManagerCore.CORE_ID, RawSettingSerializerDescriptor) {
      tags = listOf(CacheTag)
    }
    )).hasSize(4096)
  }
}

private suspend fun corruptValue(key: String, controller: SettingsController) {
  val settingsDescriptor = settingDescriptor(key = key,
                                             pluginId = PluginManagerCore.CORE_ID,
                                             serializer = RawSettingSerializerDescriptor) {
    tags = listOf(CacheTag)
  }
  controller.setItem(settingsDescriptor, Random(42).nextBytes(4096))
}

@Serializable
private data class CustomObject(@JvmField val cats: List<String> = emptyList())

@Serializable
private data class ObjectV1(@JvmField val foo: String = "", @JvmField val bar: String = "", @JvmField val uselessBar: String = "")
@Serializable
private data class ObjectV2(@JvmField val foo: String = "", @JvmField val bar: List<String> = emptyList())
