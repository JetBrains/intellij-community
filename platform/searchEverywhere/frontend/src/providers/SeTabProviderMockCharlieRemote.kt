// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeTab
import com.intellij.platform.searchEverywhere.api.SeTabProvider
import fleet.kernel.DurableRef
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeTabProviderMockCharlieRemote : SeTabProvider {
  override suspend fun getTab(project: Project, sessionRef: DurableRef<SeSessionEntity>): SeTab =
    SeTabMock.create(project,
                     sessionRef,
                     "Charlie-Remote",
                     listOf(SeProviderId("SearchEverywhereItemsProviderMock_MockBackend")))
}