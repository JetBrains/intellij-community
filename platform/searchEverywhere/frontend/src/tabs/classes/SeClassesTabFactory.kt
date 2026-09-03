// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.classes

import com.intellij.ide.actions.searcheverywhere.GotoContributorsAvailabilityService
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ex.WelcomeScreenProjectProvider
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeEssentialTabFactory
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class SeClassesTabFactory : SeEssentialTabFactory {
  override val id: String get() = SeClassesTab.ID
  override val name: String get() = SeClassesTab.NAME
  override val priority: Int get() = SeClassesTab.PRIORITY

  override fun isAvailable(project: Project?): Boolean =
    project != null && !WelcomeScreenProjectProvider.isWelcomeScreenProject(project)

  override suspend fun getTab(scope: CoroutineScope, project: Project?, session: SeSession, initEvent: AnActionEvent, registerShortcut: (AnAction) -> Unit): SeTab? {
    project ?: return null
    if (!isAvailable(project)) return null
    if (!GotoContributorsAvailabilityService.getInstance(project).awaitHasClassContributors()) return null

    val delegate = SeTabDelegate.create(project,
                                        session,
                                        "Classes",
                                        listOf(SeProviderId(SeProviderIdUtils.CLASSES_ID)),
                                        initEvent,
                                        scope)

    return SeClassesTab(delegate)
  }
}
