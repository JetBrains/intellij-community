// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import org.jetbrains.annotations.ApiStatus

/**
 * Makes one [SeItemsProvider] for the All tab and one for the separate tab of the provider.
 *
 * Use it when the provider changes its behavior with the tab, for example its commands.
 * [SeProvidersHolder] asks for both providers in each session.
 */
@ApiStatus.Internal
interface SeTabAwareItemsProviderFactory : SeItemsProviderFactory {
  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider? =
    getItemsProvider(project, dataContext, isAllTab = true)

  suspend fun getItemsProvider(project: Project?, dataContext: DataContext, isAllTab: Boolean): SeItemsProvider?
}
