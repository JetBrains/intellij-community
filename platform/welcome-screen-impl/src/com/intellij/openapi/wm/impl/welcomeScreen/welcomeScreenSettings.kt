// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.wm.impl.welcomeScreen

import com.intellij.openapi.util.registry.Registry
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
fun isStationWelcomeScreenPromoEnabled(): Boolean {
  return Registry.`is`("station.enable.welcome.screen.promo") && !Registry.`is`("jetbrainsd.new.connection.flow.enabled", false)
}

/**
 * Number of primary buttons on welcome screen (other go to 'more actions')
 */
@Internal
fun getWelcomeScreenPrimaryButtonsNum(): Int {
  if (isStationWelcomeScreenPromoEnabled()) {
    return 4
  }
  return Registry.intValue("welcome.screen.primaryButtonsCount", 3)
}
