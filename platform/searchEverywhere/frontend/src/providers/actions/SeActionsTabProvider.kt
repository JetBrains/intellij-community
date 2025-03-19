// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.frontend.SeTab
import com.intellij.platform.searchEverywhere.frontend.SeTabProvider
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import fleet.kernel.DurableRef
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeActionsTabProvider : SeTabProvider {
  override suspend fun getTab(project: Project, sessionRef: DurableRef<SeSessionEntity>, dataContext: DataContext): SeTab {
    val delegate = SeTabDelegate.create(project,
                                        sessionRef,
                                        "Actions",
                                        listOf(SeProviderId("com.intellij.ActionsItemsProvider")),
                                        dataContext,
                                        true)
    return SeActionsTab(delegate)
  }
}