// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.toProviderId
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
class SeAdaptedTab private constructor(delegate: SeTabDelegate, override val name: @Nls String, override val id: String): SeDefaultTabBase(delegate) {
  override suspend fun getFilterEditor(): SeFilterEditor? = null

  companion object {
    fun create(legacyContributorId: String, name: @Nls String, scope: CoroutineScope, project: Project?, session: SeSession, initEvent: AnActionEvent): SeAdaptedTab {
      val delegate = SeTabDelegate(project, session, legacyContributorId, listOf(legacyContributorId.toProviderId()), initEvent, scope)

      return SeAdaptedTab(delegate, name, legacyContributorId)
    }
  }
}