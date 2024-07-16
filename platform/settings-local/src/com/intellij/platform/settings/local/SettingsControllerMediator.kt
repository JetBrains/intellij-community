// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.platform.settings.local

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.settings.*
import com.intellij.util.xmlb.SettingsInternalApi
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

@VisibleForTesting
internal val SETTINGS_CONTROLLER_EP_NAME: ExtensionPointName<DelegatedSettingsController> = ExtensionPointName("com.intellij.settingsController")

@VisibleForTesting
@SettingsInternalApi
@Internal
class SettingsControllerMediator(
  private val controllers: List<DelegatedSettingsController> = SETTINGS_CONTROLLER_EP_NAME.extensionList,
  private val isPersistenceStateComponentProxy: Boolean = controllers.size > 1,
  private val useEfficientStorageForCache: Boolean = true,
) : SettingsController {
  @TestOnly
  constructor(isPersistenceStateComponentProxy: Boolean)
    : this(controllers = SETTINGS_CONTROLLER_EP_NAME.extensionList, isPersistenceStateComponentProxy = isPersistenceStateComponentProxy)

  override fun <T : Any> getItem(key: SettingDescriptor<T>): T? {
    return doGetItem(key).get()
  }

  override fun <T : Any> doGetItem(key: SettingDescriptor<T>): GetResult<T?> {
    for (controller in controllers) {
      val result = controller.getItem(key)
      if (result.isResolved) {
        return result
      }
    }

    return GetResult.inapplicable()
  }

  override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?) {
    val result = doSetItem(key, value)
    if (result == SetResult.forbid()) {
      throw ReadOnlySettingException(key)
    }
  }

  override fun <T : Any> doSetItem(key: SettingDescriptor<T>, value: T?): SetResult {
    var totalResult = SetResult.inapplicable()
    for (controller in controllers) {
      val result = controller.setItem(key = key, value = value)
      if (result == SetResult.forbid() || result.value !is Enum<*>) {
        return result
      }
      else if (result == SetResult.done()) {
        totalResult = result
      }
    }
    return totalResult
  }

  override fun createStateStorage(collapsedPath: String, file: Path): Any? {
    if (useEfficientStorageForCache && collapsedPath == StoragePathMacros.CACHE_FILE) {
      return StateStorageBackedByController(controller = this, tags = java.util.List.of(CacheTag))
    }
    else {
      return null
    }
  }

  override fun release() {
    for (controller in controllers) {
      controller.close()
    }
  }

  override fun isPersistenceStateComponentProxy(): Boolean = isPersistenceStateComponentProxy

  override fun createChild(container: ComponentManager): SettingsController? {
    val result = ArrayList<DelegatedSettingsController>()
    for (controller in controllers) {
      controller.createChild(container)?.let {
        result.add(it)
      }
    }

    if (result.isEmpty()) {
      return null
    }
    else {
      return SettingsControllerMediator(controllers = java.util.List.copyOf(result), isPersistenceStateComponentProxy = true, useEfficientStorageForCache = false)
    }
  }
}