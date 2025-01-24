// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.api

import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.Internal
interface SeItemsProviderFactory {
  fun getItemsProvider(project: Project): SeItemsProvider

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SeItemsProviderFactory> = ExtensionPointName("com.intellij.searchEverywhere.itemsProviderFactory")
  }
}