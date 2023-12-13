// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.*
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import kotlin.random.Random

@TestApplication
class LocalSettingsControllerTest {
  private val factory = settingDescriptorFactory(PluginManagerCore.CORE_ID)

  @Test
  fun `cache string value state`() = runBlocking<Unit> {
    serviceAsync<CacheStateStorageService>().clear()

    val controller = serviceAsync<SettingsController>()
    val settingsDescriptor = factory.settingDescriptor("test.flag") {
      tags = listOf(CacheTag)
    }

    assertThat(controller.getItem(settingsDescriptor)).isNull()
    controller.setItem(settingsDescriptor, "test")
    assertThat(controller.getItem(settingsDescriptor)).isEqualTo("test")
    assertThat(getCacheStorageAsMap()).isEqualTo(java.util.Map.of(PluginManagerCore.CORE_ID.idString + ".test.flag", "test"))
  }

  @Test
  fun `cache object value state`() = runBlocking<Unit> {
    serviceAsync<CacheStateStorageService>().clear()

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
  fun `cache object map value state`() = runBlocking<Unit> {
    serviceAsync<CacheStateStorageService>().clear()

    val factory = settingDescriptorFactory(PluginManagerCore.CORE_ID)

    val controller = serviceAsync<SettingsController>()
    val settingsDescriptor = factory.settingDescriptor("test.flag", factory.mapSerializer<String, String>()) {
      tags = listOf(CacheTag)
    }

    assertThat(controller.getItem(settingsDescriptor)).isNull()

    val map = java.util.Map.of("foo", "12", "bar", "42")
    controller.setItem(settingsDescriptor, map)
    assertThat(controller.getItem(settingsDescriptor)).isEqualTo(map)

    corruptValue("test.flag", controller)
    try {
      controller.getItem(settingsDescriptor)
    }
    catch (e: Throwable) {
      assertThat(e.message).startsWith("Cannot deserialize value for key com.intellij.test.flag (size=4096, value will be stored under key com.intellij.test.flag.__corrupted__) ")
    }
    assertThat(controller.getItem(settingsDescriptor)).isNull()
    assertThat(controller.getItem(settingDescriptor("test.flag.__corrupted__", PluginManagerCore.CORE_ID, ByteArraySettingValueSerializer) {
      tags = listOf(CacheTag)
    }
    )).hasSize(4096)
  }
}

private suspend fun corruptValue(key: String, controller: SettingsController) {
  val settingsDescriptor = settingDescriptor(key = key,
                                             pluginId = PluginManagerCore.CORE_ID,
                                             serializer = ByteArraySettingValueSerializer) {
    tags = listOf(CacheTag)
  }
  controller.setItem(settingsDescriptor, Random(42).nextBytes(4096))
}

private suspend fun getCacheStorageAsMap(): Map<String, String> {
  return serviceAsync<CacheStateStorageService>().getCacheStorageAsMap()
}

@Serializable
private data class CustomObject(@JvmField val cats: List<String> = emptyList())
