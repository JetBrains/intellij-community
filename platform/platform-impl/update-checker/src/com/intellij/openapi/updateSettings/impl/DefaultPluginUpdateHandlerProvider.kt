// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import org.jetbrains.annotations.ApiStatus

/** The provider of every process without a split frontend layer. The split frontend registers its own provider with `order="first"`. */
@ApiStatus.Internal
class DefaultPluginUpdateHandlerProvider : PluginUpdateHandlerProvider {
  private val defaultHandler = DefaultPluginUpdateHandler()

  override fun getPluginUpdateHandler(): PluginUpdateHandler = defaultHandler
}
