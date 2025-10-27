// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.vcs.git.frontend.searchEverywhere

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabFactory
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.vcs.git.SeGitProviderIdUtils
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeGitTabFactory : SeTabFactory {
  override val id: String get() = SeGitTab.ID
  override suspend fun getTab(scope: CoroutineScope, project: Project?, session: SeSession, initEvent: AnActionEvent, registerShortcut: (AnAction) -> Unit): SeTab? {
    if (project == null) return null

    val providerId = SeProviderId(SeGitProviderIdUtils.GIT_OBJECTS_ID)
    if (!SeTabDelegate.shouldShowLegacyContributorInSeparateTab(project, providerId, initEvent, session)) return null

    val delegate = SeTabDelegate(project,
                                 session,
                                 "Git",
                                 listOf(providerId),
                                 initEvent,
                                 scope)
    return SeGitTab(delegate)
  }
}