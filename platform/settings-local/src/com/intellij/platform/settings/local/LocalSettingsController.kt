// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package com.intellij.platform.settings.local

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.platform.settings.CacheTag
import com.intellij.platform.settings.ChainedSettingsController
import com.intellij.platform.settings.PropertyManagerAdapterTag
import com.intellij.platform.settings.SettingDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.TestOnly

@TestOnly
fun clearCacheStore() {
  service<LocalSettingsControllerService>().storage.clear()
}

@TestOnly
internal suspend fun compactCacheStore() {
  service<LocalSettingsControllerService>().storage.compactStore()
}

private class LocalSettingsController : ChainedSettingsController {
  private val service = service<LocalSettingsControllerService>()

  override fun <T : Any> getItem(key: SettingDescriptor<T>, chain: List<ChainedSettingsController>): T? {
    return service.getItem(key)
  }

  override suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?, chain: List<ChainedSettingsController>) {
    service.setItem(key, value)
  }

  override fun hasKeyStartsWith(key: String) = service.storage.hasKeyStartsWith(key)

  override fun <T : Any> putIfDiffers(key: SettingDescriptor<T>, value: T?, chain: List<ChainedSettingsController>) {
    service.putIfDiffers(key, value)
  }
}

@Service(Service.Level.APP)
private class LocalSettingsControllerService(coroutineScope: CoroutineScope) : SettingsSavingComponent {
  @JvmField
  val storage: MvStoreStorage = MvStoreStorage()

  // Telemetry is not ready at this point yet
  val cacheStore by lazy { CacheStateStorageService(storage) }

  init {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      coroutineScope.coroutineContext.job.invokeOnCompletion {
        cacheStore.storage.close()
      }
    }
  }

  override suspend fun save() {
    cacheStore.storage.save()
  }

  fun <T : Any> getItem(key: SettingDescriptor<T>): T? {
    for (tag in key.tags) {
      if (tag is PropertyManagerAdapterTag) {
        val propertyManager = PropertiesComponent.getInstance()
        @Suppress("UNCHECKED_CAST")
        return propertyManager.getValue(tag.oldKey) as T?
      }
      else if (tag is CacheTag) {
        return cacheStore.getValue(key = getEffectiveKey(key = key), serializer = key.serializer, pluginId = key.pluginId)
      }
    }

    thisLogger().error("Getting of $key is not supported")
    return null
  }

  fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?) {
    operate(key, cacheOperation = {
      cacheStore.setValue(key = getEffectiveKey(key), value = value, serializer = key.serializer, pluginId = key.pluginId)
    })
  }

  fun <T : Any> putIfDiffers(key: SettingDescriptor<T>, value: T?) {
    operate(key, cacheOperation = {
      cacheStore.putIfDiffers(key = getEffectiveKey(key = key), value = value, serializer = key.serializer, pluginId = key.pluginId)
    })
  }

  fun invalidateCaches() {
    cacheStore.invalidate()
  }

  private fun getEffectiveKey(key: SettingDescriptor<*>): String = "${key.pluginId.idString}.${key.key}"
}

private inline fun <R : Any, T: Any> operate(key: SettingDescriptor<T>, cacheOperation: () -> R): R? {
  for (tag in key.tags) {
    if (tag is CacheTag) {
      return cacheOperation()
    }
  }

  logger<LocalSettingsController>().error("Saving of $key is not supported")
  return null
}

private class CacheStateStorageInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    service<LocalSettingsControllerService>().invalidateCaches()
  }
}