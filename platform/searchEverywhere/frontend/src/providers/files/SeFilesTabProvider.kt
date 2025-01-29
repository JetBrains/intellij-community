// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.files

import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeProviderId
import com.intellij.platform.searchEverywhere.SeSessionEntity
import com.intellij.platform.searchEverywhere.api.SeTab
import com.intellij.platform.searchEverywhere.api.SeTabProvider
import com.intellij.platform.searchEverywhere.frontend.SeTabHelper
import fleet.kernel.DurableRef
import org.jetbrains.annotations.ApiStatus.Internal

@Internal
class SeFilesTabProvider : SeTabProvider {
  override suspend fun getTab(project: Project, sessionRef: DurableRef<SeSessionEntity>): SeTab {
    val helper = SeTabHelper.create(project, sessionRef,
                                    listOf(SeProviderId("com.intellij.FileSearchEverywhereItemProvider")), true)
    return SeFilesTab(helper)
  }
}