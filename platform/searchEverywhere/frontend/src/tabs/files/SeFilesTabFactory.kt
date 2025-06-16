// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.files

import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeProviderIdUtils
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabFactory
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import fleet.kernel.DurableRef
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesTabFactory : SeTabFactory {
  override val id: String get() = SeFilesTab.ID

  override suspend fun getTab(scope: CoroutineScope, project: Project?, sessionRef: DurableRef<SeSessionEntity>, initEvent: AnActionEvent): SeTab? {
    project ?: return null

    val delegate = SeTabDelegate(project,
                                 sessionRef,
                                 "Files",
                                 listOf(SeProviderId(SeProviderIdUtils.FILES_ID)),
                                 initEvent,
                                 scope)

    return SeFilesTab(delegate)
  }
}