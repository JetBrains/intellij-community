// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.text

import com.intellij.find.impl.TextSearchContributor
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
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeTextTabFactory : SeEssentialTabFactory {
  override val id: String get() = SeTextTab.ID
  override val name: String get() = SeTextTab.NAME

  override suspend fun getTab(scope: CoroutineScope, project: Project?, session: SeSession, initEvent: AnActionEvent, registerShortcut: (AnAction) -> Unit): SeTab? {
    if (project == null || !TextSearchContributor.enabled()) return null

    val delegate = SeTabDelegate(project,
                                 session,
                                 "Text",
                                 listOf(SeProviderId(SeProviderIdUtils.TEXT_ID)),
                                 initEvent,
                                 scope)
    return SeTextTab(delegate, registerShortcut)
  }
}