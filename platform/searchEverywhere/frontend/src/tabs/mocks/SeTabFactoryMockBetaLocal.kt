// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.mocks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabFactory
import com.intellij.platform.searchEverywhere.providers.mocks.SeItemsProviderFactoryMockBetaLocal
import fleet.kernel.DurableRef
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTabFactoryMockBetaLocal : SeTabFactory {
  override val id: String get() = "BetaLocal"

  override suspend fun getTab(scope: CoroutineScope, project: Project?, sessionRef: DurableRef<SeSessionEntity>, initEvent: AnActionEvent, registerShortcut: (AnAction) -> Unit): SeTab? =
    SeTabMock.create(project,
                     sessionRef,
                     "BetaLocal",
                     listOf(SeProviderId(SeItemsProviderFactoryMockBetaLocal.ID)),
                     initEvent,
                     scope)
}