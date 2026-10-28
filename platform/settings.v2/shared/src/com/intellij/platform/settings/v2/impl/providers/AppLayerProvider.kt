// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.settings.v2.impl.providers

// @spec ../../../../../../../../../spec/file-layers.spec.md

import com.intellij.platform.settings.v2.LayerData
import com.intellij.platform.settings.v2.SettingsContext
import com.intellij.platform.settings.v2.SettingsContextKind
import com.intellij.platform.settings.v2.SettingsContextKindRange

/** Provides the writable application layer. [AppLayerService] holds its state. */
internal class AppLayerProvider : BaseLayerProvider() {
  override val contextKindRange: SettingsContextKindRange =
    SettingsContextKindRange(SettingsContextKind.APPLICATION, SettingsContextKind.APPLICATION)

  override fun service(context: SettingsContext): BaseLayerService = AppLayerService.getInstance()

  override suspend fun requestData(scopeContext: SettingsContext): List<LayerData> = AppLayerService.getInstance().requestData(scopeContext)
}
