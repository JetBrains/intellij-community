// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package com.intellij.platform.settings.local

import com.intellij.configurationStore.SettingsSavingComponent
import com.intellij.ide.caches.CachesInvalidator
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.platform.settings.*
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

  override fun <T : Any> getItem(key: SettingDescriptor<T>) = service.getItem(key)

  override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?): Boolean {
    service.setItem(key, value)
    return false
  }

  override fun close() {
    service.storeManager.close()
  }
}

@Service(Service.Level.APP)
private class LocalSettingsControllerService : SettingsSavingComponent {
  @JvmField
  val storeManager: MvStoreManager = MvStoreManager()

  // Telemetry is not ready at this point yet
  private val cacheMap by lazy {
    InternalStateStorageService(storeManager.openMap("cache_v1"), telemetryScopeName = "cacheStateStorage")
  }

  private val internalMap by lazy {
    InternalStateStorageService(storeManager.openMap("internal_v1"), telemetryScopeName = "internalStateStorage")
  }

  override suspend fun save() {
    storeManager.save()
  }

  fun <T : Any> getItem(key: SettingDescriptor<T>): GetResult<T> {
    for (tag in key.tags) {
      if (tag is PropertyManagerAdapterTag) {
        val propertyManager = PropertiesComponent.getInstance()
        @Suppress("UNCHECKED_CAST")
        return GetResult.resolved(propertyManager.getValue(tag.oldKey) as T?)
      }
    }

    operate(key = key, internalOperation = { map, componentName ->
      val value = map.getValue(key = getEffectiveKey(key), serializer = key.serializer, pluginId = key.pluginId)
      if (value == null && componentName != null) {
        if (map.map.hasKeyStartsWith("${key.pluginId.idString}.$componentName.")) {
          return GetResult.resolved(null)
        }
        else {
          return GetResult.inapplicable()
        }
      }
      else {
        return GetResult.resolved(value)
      }
    })

    return GetResult.inapplicable()
  }

  fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?) {
    operate(key, internalOperation = { map, _ ->
      map.setValue(key = getEffectiveKey(key), value = value, serializer = key.serializer, pluginId = key.pluginId)
    })
  }

  fun invalidateCaches() {
    cacheMap.map.clear()
  }

  private fun getEffectiveKey(key: SettingDescriptor<*>): String = "${key.pluginId.idString}.${key.key}"

  private inline fun <T : Any> operate(
    key: SettingDescriptor<T>,
    internalOperation: (map: InternalStateStorageService, componentName: String?) -> Unit,
  ) {
    var componentName: String? = null
    for (tag in key.tags) {
      when (tag) {
        is CacheTag -> {
          return internalOperation(cacheMap, componentName)
        }
        is NonShareableInternalTag -> {
          return internalOperation(internalMap, componentName)
        }
        is PersistenceStateComponentProperty -> {
          // this tag is expected to be first
          componentName = tag.componentName
        }
      }
    }

    logger<LocalSettingsController>().error("Operation for $key is not supported")
  }
}

@Suppress("unused")
private class CacheStateStorageInvalidator : CachesInvalidator() {
  override fun invalidateCaches() {
    service<LocalSettingsControllerService>().invalidateCaches()
  }
}