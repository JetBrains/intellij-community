// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@ApiStatus.Experimental
interface SeLegacyItemPresentationProvider {
  val id: String
  suspend fun getPresentation(item: Any): SeItemPresentation?

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SeLegacyItemPresentationProvider> =
      ExtensionPointName("com.intellij.searchEverywhere.legacyItemPresentationProvider")
  }
}