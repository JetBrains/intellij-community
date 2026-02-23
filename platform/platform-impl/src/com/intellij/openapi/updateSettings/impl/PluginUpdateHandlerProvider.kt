// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
interface PluginUpdateHandlerProvider {
  fun getPluginUpdateHandler(): PluginUpdateHandler

  companion object {
    fun getInstance(): PluginUpdateHandlerProvider = service()
  }
}
