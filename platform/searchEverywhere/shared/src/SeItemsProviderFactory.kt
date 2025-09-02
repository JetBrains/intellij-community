// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Contract

@ApiStatus.Experimental
@ApiStatus.Internal
interface SeItemsProviderFactory {
  val id: String

  @Contract("_, _ -> new", pure = true)
  suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider?

  companion object {
    @ApiStatus.Internal
    val EP_NAME: ExtensionPointName<SeItemsProviderFactory> = ExtensionPointName("com.intellij.searchEverywhere.itemsProviderFactory")
  }
}