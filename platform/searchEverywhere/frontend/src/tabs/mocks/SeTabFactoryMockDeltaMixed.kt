// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.mocks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabFactory
import com.intellij.platform.searchEverywhere.providers.mocks.SeItemsProviderFactoryMockAlphaLocal
import com.intellij.platform.searchEverywhere.providers.mocks.SeItemsProviderFactoryMockBetaLocal
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTabFactoryMockDeltaMixed : SeTabFactory {
  override val id: String get() = "DeltaMixed"

  override suspend fun getTab(scope: CoroutineScope, project: Project?, session: SeSession, initEvent: AnActionEvent, registerShortcut: (AnAction) -> Unit): SeTab? =
    SeTabMock.create(project,
                     session,
                     "DeltaMixed",
                     listOf(SeProviderId(SeItemsProviderFactoryMockAlphaLocal.ID),
                            SeProviderId(SeItemsProviderFactoryMockBetaLocal.ID),
                            SeProviderId("SearchEverywhereItemsProviderMock_MockBackend")),
                     initEvent,
                     scope)
}