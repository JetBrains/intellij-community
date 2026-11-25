// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.backend.providers.runConfigurations

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import org.jetbrains.annotations.ApiStatus

/**
 * Creates [SeRunConfigurationsProvider] with the data context of the session. A session without a project has no run configurations.
 */
@ApiStatus.Internal
class SeRunConfigurationsProviderFactory : SeItemsProviderFactory {
  override val id: String
    get() = SeProviderIdUtils.RUN_CONFIGURATIONS_ID

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider? {
    if (project == null) return null
    return SeRunConfigurationsProvider(project, dataContext)
  }
}
