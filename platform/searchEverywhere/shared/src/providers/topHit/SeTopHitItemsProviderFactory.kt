// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.providers.topHit

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.application.EDT
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeItemsProvider
import com.intellij.platform.searchEverywhere.SeItemsProviderFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

/**
 * Creates [SeTopHitItemsProvider] with the context component of the data context. A session without a project has no top hits.
 */
@ApiStatus.Internal
abstract class SeTopHitItemsProviderFactory : SeItemsProviderFactory {
  protected abstract val isHost: Boolean
  protected abstract val displayName: @Nls String

  override val id: String
    get() = SeTopHitItemsProvider.id(isHost)

  override suspend fun getItemsProvider(project: Project?, dataContext: DataContext): SeItemsProvider? {
    if (project == null) return null

    val contextComponent = withContext(Dispatchers.EDT) { dataContext.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) }
    return SeTopHitItemsProvider(isHost, project, contextComponent, displayName)
  }
}
