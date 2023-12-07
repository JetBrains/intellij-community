// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.components.serviceAsync
import com.intellij.platform.settings.CacheStateTag
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.settings.objectSettingValueSerializer
import com.intellij.platform.settings.settingDescriptor
import com.intellij.testFramework.junit5.TestApplication
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
class LocalSettingsControllerTest {
  @Test
  fun `cache string value state`() = runBlocking<Unit> {
    serviceAsync<CacheStatePropertyService>().clear()

    val controller = serviceAsync<SettingsController>()
    val settingsDescriptor = settingDescriptor("test.flag", PluginManagerCore.CORE_ID) {
      tags = listOf(CacheStateTag)
    }

    assertThat(controller.getItem(settingsDescriptor)).isNull()
    controller.setItem(settingsDescriptor, "test")
    assertThat(controller.getItem(settingsDescriptor)).isEqualTo("test")
    assertThat(getCacheStorageAsMap()).isEqualTo(java.util.Map.of(PluginManagerCore.CORE_ID.idString + ".test.flag", "test"))
  }

  @Test
  fun `cache object value state`() = runBlocking<Unit> {
    serviceAsync<CacheStatePropertyService>().clear()

    val controller = serviceAsync<SettingsController>()
    val settingsDescriptor = settingDescriptor("test.flag", PluginManagerCore.CORE_ID, objectSettingValueSerializer<CustomObject>()) {
      tags = listOf(CacheStateTag)
    }

    assertThat(controller.getItem(settingsDescriptor)).isNull()

    val cats = CustomObject(cats = listOf("red", "blue", "white"))
    controller.setItem(settingsDescriptor, cats)
    assertThat(controller.getItem(settingsDescriptor)).isEqualTo(cats)
  }
}

private suspend fun getCacheStorageAsMap(): Map<String, String> {
  return serviceAsync<CacheStatePropertyService>().getCacheStorageAsMap()
}

@Serializable
private data class CustomObject(@JvmField val cats: List<String> = emptyList())
