// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.updateChecker.backend

import com.intellij.openapi.updateSettings.impl.DefaultPluginUpdateHandler
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandler
import com.intellij.openapi.updateSettings.impl.PluginUpdateHandlerProvider
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class DefaultPluginUpdateHandlerProvider : PluginUpdateHandlerProvider {
  private val defaultHandler = DefaultPluginUpdateHandler()

  override fun getPluginUpdateHandler(): PluginUpdateHandler = defaultHandler
}
