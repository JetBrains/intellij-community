// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.platform.settings.local

import com.intellij.openapi.components.ComponentManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.settings.*
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

@VisibleForTesting
internal val SETTINGS_CONTROLLER_EP_NAME: ExtensionPointName<DelegatedSettingsController> =
  ExtensionPointName("com.intellij.settingsController")

@VisibleForTesting
@IntellijInternalApi
@Internal
class SettingsControllerMediator(
  private val controllers: List<DelegatedSettingsController> = SETTINGS_CONTROLLER_EP_NAME.extensionList,
  private val isPersistenceStateComponentProxy: Boolean = controllers.size > 1,
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
    doSetItem(key, value)
  }

  @IntellijInternalApi
  override fun <T : Any> doSetItem(key: SettingDescriptor<T>, value: T?): SetResult {
    var totalResult = SetResult.INAPPLICABLE
    for (controller in controllers) {
      val result = controller.setItem(key = key, value = value)
      if (result == SetResult.FORBID) {
        return result
      }
      else if (result == SetResult.DONE) {
        totalResult = result
      }
    }
    return totalResult
  }

  override fun createStateStorage(collapsedPath: String, file: Path): Any? {
    return when (collapsedPath) {
      StoragePathMacros.CACHE_FILE -> {
        StateStorageBackedByController(controller = this, tags = java.util.List.of(CacheTag))
      }
      else -> null
    }
  }

  @IntellijInternalApi
  override fun release() {
    for (controller in controllers) {
      controller.close()
    }
  }

  @IntellijInternalApi
  override fun isPersistenceStateComponentProxy(): Boolean = isPersistenceStateComponentProxy

  @IntellijInternalApi
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
      return SettingsControllerMediator(controllers = java.util.List.copyOf(result), isPersistenceStateComponentProxy = true)
    }
  }
}