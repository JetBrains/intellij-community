// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
interface SearchEverywhereTabProvider {
  fun getTab(): SearchEverywhereTab

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SearchEverywhereTabProvider> = ExtensionPointName("com.intellij.searchEverywhere.tabProvider")
  }
}