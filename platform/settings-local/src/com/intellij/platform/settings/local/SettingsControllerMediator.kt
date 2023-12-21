// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)
@file:Suppress("ReplaceJavaStaticMethodWithKotlinAnalog")

package com.intellij.platform.settings.local

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.settings.*
import org.jetbrains.annotations.VisibleForTesting
import java.nio.file.Path

@VisibleForTesting
internal val SETTINGS_CONTROLLER_EP_NAME: ExtensionPointName<DelegatedSettingsController> =
  ExtensionPointName("com.intellij.settingsController")

private val delegateToSettingsController = System.getProperty("idea.settings.internal.delegate.to.controller", "true").toBoolean()

internal class SettingsControllerMediator : SettingsController {
  private val controllers = SETTINGS_CONTROLLER_EP_NAME.extensionList

  override fun <T : Any> getItem(key: SettingDescriptor<T>): T? {
    for (controller in controllers) {
      val result = controller.getItem(key)
      if (result.isResolved) {
        return result.get()
      }
    }

    return null
  }

  override fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?) {
    for (controller in controllers) {
      if (controller.setItem(key = key, value = value)) {
        return
      }
    }
  }

  override fun createStateStorage(collapsedPath: String, file: Path): Any? {
    if (!delegateToSettingsController && !ApplicationManager.getApplication().isUnitTestMode) {
      return null
    }

    return when (collapsedPath) {
      StoragePathMacros.CACHE_FILE -> {
        StateStorageBackedByController(controller = this, tags = java.util.List.of(CacheTag), oldStorage = null)
      }
      StoragePathMacros.NON_ROAMABLE_FILE -> {
        val oldStorage = XmlFileStorage(file)
        StateStorageBackedByController(this, java.util.List.of(NonShareableInternalTag), oldStorage)
      }
      else -> null
    }
  }
}