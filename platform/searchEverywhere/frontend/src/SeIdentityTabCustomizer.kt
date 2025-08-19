// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeIdentityTabCustomizer: SeTabCustomizer {
  override fun customizeTabs(tabFactories: List<SeTabFactory>): List<SeTabFactory> {
    return tabFactories
  }
}