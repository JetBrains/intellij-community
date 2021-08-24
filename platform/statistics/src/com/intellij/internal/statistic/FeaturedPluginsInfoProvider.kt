// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.statistic

import com.intellij.openapi.extensions.PluginId

interface FeaturedPluginsInfoProvider {
  /**
   * Returns a list of pre-defined featured plugins suggested in IDE customization wizard.
   * All plugins returned from this method has to be available on public Marketplace.
   */
  fun getFeaturedPluginsFromMarketplace(): Set<PluginId>
}