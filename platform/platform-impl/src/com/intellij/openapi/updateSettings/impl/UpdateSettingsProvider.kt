// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("UpdateSettingsProviderHelper")
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.diagnostic.runAndLogException
import com.intellij.openapi.extensions.ExtensionPointName

private val LOG = logger<UpdateSettingsProvider>()
private val UPDATE_SETTINGS_PROVIDER_EP = ExtensionPointName<UpdateSettingsProvider>("com.intellij.updateSettingsProvider")

interface UpdateSettingsProvider {
  fun getPluginRepositories(): List<String>
}

internal fun getPluginRepositories(): List<String> = UPDATE_SETTINGS_PROVIDER_EP.extensionList.flatMap {
  LOG.runAndLogException {
    it.getPluginRepositories()
  } ?: emptyList()
}
