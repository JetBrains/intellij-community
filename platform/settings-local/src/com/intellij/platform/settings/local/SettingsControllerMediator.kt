// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.local

import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.platform.settings.ChainedSettingsController
import com.intellij.platform.settings.SettingDescriptor
import com.intellij.platform.settings.SettingsController

private val SETTINGS_CONTROLLER_EP_NAME: ExtensionPointName<ChainedSettingsController> =
  ExtensionPointName("com.intellij.settingsController")

private val delegateToSettingsController = System.getProperty("idea.settings.cache.delegate.to.controller", "false").toBoolean()

private class SettingsControllerMediator : SettingsController {
  private val first: ChainedSettingsController
  private val chain: List<ChainedSettingsController>

  init {
    val extensions = SETTINGS_CONTROLLER_EP_NAME.extensionList
    first = extensions.first()
    chain = extensions.subList(1, extensions.size)
  }

  override fun <T : Any> getItem(key: SettingDescriptor<T>): T? {
    return first.getItem(key, chain)
  }

  override suspend fun <T : Any> setItem(key: SettingDescriptor<T>, value: T?) {
    return first.setItem(key = key, value = value, chain = chain)
  }

  override fun createStateStorage(collapsedPath: String): Any? {
    if (delegateToSettingsController && collapsedPath == StoragePathMacros.CACHE_FILE) {
      return StateStorageBackedByController(first)
    }
    else {
      return null
    }
  }
}