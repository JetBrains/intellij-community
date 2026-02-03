// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.ui.dsl.builder.Panel
import org.jetbrains.annotations.ApiStatus

/**
 * This EP makes it possible for plugins to add any UI to Settings | Appearance & Behavior | System Settings | Updates.
 */
interface UpdateSettingsUIProvider {
  companion object {
    @ApiStatus.Internal
    val EP_NAME = ExtensionPointName<UpdateSettingsUIProvider>("com.intellij.updateSettingsUIProvider")
  }

  fun init(panel: Panel)
}