// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.mocks

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.SeTab
import com.intellij.platform.searchEverywhere.SeTabProvider
import com.intellij.platform.searchEverywhere.mocks.SeItemsProviderFactoryMockAlphaLocal
import com.intellij.platform.searchEverywhere.mocks.SeItemsProviderFactoryMockBetaLocal
import fleet.kernel.DurableRef

class SeTabProviderMockDeltaMixed : SeTabProvider {
  override suspend fun getTab(project: Project, sessionRef: DurableRef<SeSessionEntity>): SeTab =
    SeTabMock.create(project,
                     sessionRef,
                     "DeltaMixed",
                     listOf(SeProviderId(SeItemsProviderFactoryMockAlphaLocal.ID),
                            SeProviderId(SeItemsProviderFactoryMockBetaLocal.ID),
                            SeProviderId("SearchEverywhereItemsProviderMock_MockBackend")))
}