// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.projectId
import com.intellij.platform.searchEverywhere.SearchEverywhereItemsProvider
import com.intellij.platform.searchEverywhere.SearchEverywhereProviderId
import com.intellij.platform.searchEverywhere.SearchEverywhereTab
import com.intellij.platform.searchEverywhere.SearchEverywhereTabProvider
import com.intellij.platform.searchEverywhere.impl.SearchEverywhereRemoteApi
import com.intellij.platform.util.coroutines.childScope
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SearchEverywhereFrontendService(val project: Project, val coroutineScope: CoroutineScope) {

  private val projectId: ProjectId get() = project.projectId()

  suspend fun createPopup(): SearchEverywherePopupVm {
    val popupScope = coroutineScope.childScope("SearchEverywhereFrontendService scope")
    val remoteApi = SearchEverywhereRemoteApi.getInstance()
    val sessionId = remoteApi.startSession(projectId)

    val remoteProviderIds = remoteApi.getProviderIds(projectId, sessionId).toSet()
    val frontendProviders = remoteProviderIds.associateWith {
      providerId -> SearchEverywhereItemDataFrontendProvider(providerId, projectId, sessionId)
    }
    val localProviders = SearchEverywhereItemsProvider.EP_NAME.extensionList.filter {
      !remoteProviderIds.contains(SearchEverywhereProviderId(it.id))
    }.associate { provider ->
      SearchEverywhereProviderId(provider.id) to SearchEverywhereItemDataLocalProvider(provider)
    }
    val allProviders = frontendProviders + localProviders

    val tabs: List<SearchEverywhereTab> = SearchEverywhereTabProvider.EP_NAME.extensionList.map {
      it.getTab()
    }

    return SearchEverywherePopupVm(popupScope, tabs, allProviders)
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SearchEverywhereFrontendService = project.getService(SearchEverywhereFrontendService::class.java)
  }
}