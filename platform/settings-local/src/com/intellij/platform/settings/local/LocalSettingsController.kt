// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.settings.local

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.settings.*
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly

private val SETTINGS_CONTROLLER_EP_NAME: ExtensionPointName<ChainedSettingsController> =
  ExtensionPointName("com.intellij.settingsController")

private class SettingsControllerMediator : SettingsController {
  private val first: ChainedSettingsController
  private val chain: List<ChainedSettingsController>

  init {
    val extensions = SETTINGS_CONTROLLER_EP_NAME.extensionList
    first = extensions.first()
    chain = extensions.subList(1, extensions.size)
  }

  override suspend fun <T : Any> getItem(key: SettingDescriptor<T>): T? {
    return first.getItem(key, chain)
  }

  override suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?) {
    return first.setItem(key, value, chain)
  }

  override fun createStateStorage(collapsedPath: String): Any? {
    //return StateStorageBackedByController()
    return null
  }
}

@TestOnly
internal fun clearCacheStore() {
  SETTINGS_CONTROLLER_EP_NAME.findExtensionOrFail(LocalSettingsController::class.java).clear()
}

private class LocalSettingsController(coroutineScope: CoroutineScope) : ChainedSettingsController, SettingsSavingComponent {
  private val componentManager: ComponentManager = ApplicationManager.getApplication()

  private val cacheStore = CacheStateStorageService(MvStoreStorage(coroutineScope))

  @TestOnly
  fun clear() {
    cacheStore.clear()
  }

  override suspend fun save() {
    cacheStore.save()
  }

  override suspend fun <T : Any> getItem(key: SettingDescriptor<T>, chain: List<ChainedSettingsController>): T? {
    for (tag in key.tags) {
      if (tag is PropertyManagerAdapterTag) {
        val propertyManager = componentManager.getService(PropertiesComponent::class.java)
        @Suppress("UNCHECKED_CAST")
        return propertyManager.getValue(tag.oldKey) as T?
      }
      else if (tag is CacheTag) {
        return cacheStore.getValue(getEffectiveKey(key = key), key.serializer, key.pluginId)
      }
    }

    thisLogger().error("Getting of $key is not supported")
    return null
  }

  override suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?, chain: List<ChainedSettingsController>) {
    for (tag in key.tags) {
      if (tag is CacheTag) {
        cacheStore.setValue(key = getEffectiveKey(key), value = value, serializer = key.serializer, pluginId = key.pluginId)
        return
      }
    }

    thisLogger().error("Saving of $key is not supported")
  }

  override fun invalidateCaches() {
    cacheStore.invalidate()
  }

  private fun getEffectiveKey(key: SettingDescriptor<*>): String = "${key.pluginId.idString}.${key.key}"
}

private class CacheStateStorageInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    for (controller in SETTINGS_CONTROLLER_EP_NAME.extensionList) {
      controller.invalidateCaches()
    }
  }
}