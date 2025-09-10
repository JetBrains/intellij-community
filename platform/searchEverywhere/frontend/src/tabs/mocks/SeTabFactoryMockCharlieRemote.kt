// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs.mocks

import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTabFactoryMockCharlieRemote : SeTabFactory {
  override val id: String get() = "Charlie-Remote"

  override suspend fun getTab(scope: CoroutineScope, project: Project?, session: SeSession, initEvent: AnActionEvent, registerShortcut: (AnAction) -> Unit): SeTab? {
    delay(1000)
    return SeTabMock.create(project,
                            session,
                            "Charlie-Remote",
                            listOf(SeProviderId("SearchEverywhereItemsProviderMock_MockBackend")),
                            initEvent,
                            scope)
  }
}