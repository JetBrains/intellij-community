// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.components.Service
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@Service(Service.Level.PROJECT)
class SearchEverywhereFrontendService(val project: Project, val coroutineScope: CoroutineScope) {

  //suspend fun createPopup(): SearchEverywherePopupVm {
  //  val popupScope = coroutineScope.childScope("SearchEverywhereFrontendService scope")
  //
  //  val sessionId = SearchEverywhereRemoteApi.getInstance().startSession(project.projectId())
  //  val remoteProviderIds =
  //
  //  val tabs: List<SearchEverywhereTab> = emptyList()
  //
  //  val providers = tabs.associate { tab ->
  //    tab to tab.providers.
  //  }
  //
  //  return SearchEverywherePopupVm(popupScope)
  //}

  companion object {
    @JvmStatic
    fun getInstance(project: Project): SearchEverywhereFrontendService = project.getService(SearchEverywhereFrontendService::class.java)
  }
}