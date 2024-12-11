// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.platform.searchEverywhere.frontend.SearchEverywhereFrontendService
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class SearchEverywhereActionsInitiatorAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val service = SearchEverywhereFrontendService.getInstance(project)
    service.coroutineScope.launch {
      val popup = service.createPopup()
      popup.setSearchPattern("Zoom")
      popup.searchResults.collectLatest { resultsFlow ->
        resultsFlow.collect { itemData ->
          println("Found action: ${itemData.presentation.text}")
        }
      }
    }
  }
}