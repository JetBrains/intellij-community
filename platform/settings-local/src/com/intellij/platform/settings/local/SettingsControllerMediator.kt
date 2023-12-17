// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.settings.ChainedSettingsController
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingsController
import com.intellij.platform.settings.SettingsControllerInternal

private val SETTINGS_CONTROLLER_EP_NAME: ExtensionPointName<ChainedSettingsController> =
  ExtensionPointName("com.intellij.settingsController")

private val delegateToSettingsController = System.getProperty("idea.settings.cache.delegate.to.controller", "true").toBoolean()

internal class SettingsControllerMediator : SettingsController, SettingsControllerInternal {
  private val first: ChainedSettingsController
  private val chain: List<ChainedSettingsController>

  init {
    val extensions = SETTINGS_CONTROLLER_EP_NAME.extensionList
    first = extensions.first()
    chain = extensions.subList(1, extensions.size)
  }

  override fun <T : Any> getItem(key: SettingDescriptor<T>): T? = first.getItem(key, chain)

  override suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?) {
    return first.setItem(key = key, value = value, chain = chain)
  }

  override fun hasKeyStartsWith(key: String) = first.hasKeyStartsWith(key)

  fun <T : Any> putIfDiffers(key: SettingDescriptor<T>, value: T?) = first.putIfDiffers(key = key, value = value, chain = chain)

  override fun createStateStorage(collapsedPath: String): Any? {
    if (collapsedPath == StoragePathMacros.CACHE_FILE &&
        (delegateToSettingsController || ApplicationManager.getApplication().isUnitTestMode)) {
      return StateStorageBackedByController(this)
    }
    else {
      return null
    }
  }
}