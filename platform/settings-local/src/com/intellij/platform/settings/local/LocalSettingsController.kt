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
import com.intellij.platform.settings.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.job
import org.jetbrains.annotations.TestOnly

@TestOnly
fun clearCacheStore() {
  service<LocalSettingsControllerService>().storeManager.clear()
}

@TestOnly
internal suspend fun compactCacheStore() {
  service<LocalSettingsControllerService>().storeManager.compactStore()
}

private class LocalSettingsController : DelegatedSettingsController {
  private val service = service<LocalSettingsControllerService>()

  override fun <T : Any> getItem(key: SettingDescriptor<T>) = GetResult.resolved(service.getItem(key))

  override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): Boolean {
    service.setItem(key, value)
    return false
  }

  override fun <T : Any> hasKeyStartsWith(key: SettingDescriptor<T>) = service.hasKeyStartsWith(key)
}

@Service(Service.Level.APP)
private class LocalSettingsControllerService(coroutineScope: CoroutineScope) : SettingsSavingComponent {
  @JvmField val storeManager: MvStoreManager = MvStoreManager()

  // Telemetry is not ready at this point yet
  private val cacheMap by lazy { InternalStateStorageService(storeManager.openMap("cache_v1"), telemetryScopeName = "cacheStateStorage") }
  private val internalMap by lazy {
    InternalStateStorageService(storeManager.openMap("internal_v1"), telemetryScopeName = "internalStateStorage")
  }

  init {
    if (!ApplicationManager.getApplication().isUnitTestMode) {
      coroutineScope.coroutineContext.job.invokeOnCompletion {
        storeManager.close()
      }
    }
  }

  override suspend fun save() {
    storeManager.save()
  }

  fun <T : Any> getItem(key: SettingDescriptor<T>): T? {
    for (tag in key.tags) {
      if (tag is PropertyManagerAdapterTag) {
        val propertyManager = PropertiesComponent.getInstance()
        @Suppress("UNCHECKED_CAST")
        return propertyManager.getValue(tag.oldKey) as T?
      }
    }

    operate(key, internalOperation = {
      return it.getValue(key = getEffectiveKey(key), serializer = key.serializer, pluginId = key.pluginId)
    })

    return null
  }

  fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?) {
    operate(key, internalOperation = {
      it.setValue(key = getEffectiveKey(key), value = value, serializer = key.serializer, pluginId = key.pluginId)
    })
  }

  fun <T : Any> hasKeyStartsWith(key: SettingDescriptor<T>): Boolean {
    operate(key, internalOperation = {
      return it.map.hasKeyStartsWith(getEffectiveKey(key) + ".")
    })

    return false
  }

  fun invalidateCaches() {
    cacheMap.clear()
  }

  private fun getEffectiveKey(key: SettingDescriptor<*>): String = "${key.pluginId.idString}.${key.key}"

  private inline fun  <T: Any> operate(key: SettingDescriptor<T>, internalOperation: (map: InternalStateStorageService) -> Unit) {
    for (tag in key.tags) {
      if (tag is CacheTag) {
        return internalOperation(cacheMap)
      }
      else if (tag is NonShareableInternalTag) {
        return internalOperation(internalMap)
      }
    }

    logger<LocalSettingsController>().error("Operation for $key is not supported")
  }
}

private class CacheStateStorageInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    service<LocalSettingsControllerService>().invalidateCaches()
  }
}