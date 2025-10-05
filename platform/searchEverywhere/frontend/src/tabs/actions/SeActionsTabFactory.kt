// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.actions

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeEssentialTabFactory
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeActionsTabFactory : SeEssentialTabFactory {
  override val id: String get() = SeActionsTab.ID
  override val name: String get() = SeActionsTab.NAME

  override suspend fun getTab(scope: CoroutineScope, project: Project?, session: SeSession, initEvent: AnActionEvent, registerShortcut: (AnAction) -> Unit): SeTab? {
    val delegate = SeTabDelegate(project,
                                 session,
                                 "Actions",
                                 listOf(SeProviderId(SeProviderIdUtils.ACTIONS_ID)),
                                 initEvent,
                                 scope)

    return SeActionsTab(delegate)
  }
}