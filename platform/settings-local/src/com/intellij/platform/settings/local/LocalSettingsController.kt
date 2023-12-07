// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.settings.local

import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.settings.CacheStateTag
import com.intellij.platform.settings.PropertyManagerAdapterTag
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingsController

@Suppress("NonDefaultConstructor")
internal class LocalSettingsController(private val componentManager: ComponentManager) : SettingsController {
  override suspend fun <T : Any> getItem(key: SettingDescriptor<T>): T? {
    for (tag in key.tags) {
      if (tag is PropertyManagerAdapterTag) {
        val propertyManager = componentManager.getService(PropertiesComponent::class.java)
        @Suppress("UNCHECKED_CAST")
        return propertyManager.getValue(tag.oldKey) as T?
      }
      else if (tag is CacheStateTag) {
        val store = componentManager.service<CacheStatePropertyService>()
        return store.getValue(getEffectiveKey(key), key.serializer)
      }
    }

    thisLogger().error("Getting of $key is not supported")
    return null
  }

  override suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?) {
    for (tag in key.tags) {
      if (tag is CacheStateTag) {
        val store = componentManager.service<CacheStatePropertyService>()
        store.setValue(key = getEffectiveKey(key), value = value, serializer = key.serializer)
        return
      }
    }

    thisLogger().error("Saving of $key is not supported")
  }

  private fun getEffectiveKey(key: SettingDescriptor<*>): String = "${key.pluginId.idString}.${key.key}"
}