// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:OptIn(IntellijInternalApi::class)

package com.intellij.platform.settings.local

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.StoragePathMacros
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.settings.*

private val SETTINGS_CONTROLLER_EP_NAME: ExtensionPointName<ChainedSettingsController> =
  ExtensionPointName("com.intellij.settingsController")

private val delegateToSettingsController = System.getProperty("idea.settings.internal.delegate.to.controller", "true").toBoolean()

internal class SettingsControllerMediator : SettingsController {
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

  fun <T : Any> hasKeyStartsWith(key: SettingDescriptor<T>) = first.hasKeyStartsWith(key, chain)

  fun <T : Any> putIfDiffers(key: SettingDescriptor<T>, value: T?) = first.putIfDiffers(key = key, value = value, chain = chain)

  override fun createStateStorage(collapsedPath: String): Any? {
    if (!delegateToSettingsController && !ApplicationManager.getApplication().isUnitTestMode) {
      return null
    }

    when (collapsedPath) {
      StoragePathMacros.CACHE_FILE -> return StateStorageBackedByController(this, listOf(CacheTag))
      StoragePathMacros.NON_ROAMABLE_FILE -> return StateStorageBackedByController(this, listOf(NonShareableInternalTag))
      else -> return null
    }
  }
}