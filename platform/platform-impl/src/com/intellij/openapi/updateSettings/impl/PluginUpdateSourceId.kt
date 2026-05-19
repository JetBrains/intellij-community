// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.updateSettings.impl

import com.intellij.openapi.util.NlsSafe
import org.jetbrains.annotations.ApiStatus

/**
 * Describes the source from where a plugin was downloaded.
 * This source is considered safe to install updates for the plugin from.
 * [PluginUpdateSourceId] with `isMarketplace == true` may have various hosts and are considered interchangeable in terms of safety.
 * The use case is the Marketplace proxy provided by IDE Services.
 */
@ApiStatus.Internal
sealed interface PluginUpdateSourceId {
  val host: @NlsSafe String
  val isMarketplace: Boolean
}