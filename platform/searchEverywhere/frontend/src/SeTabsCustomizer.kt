// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.application.ApplicationManager
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls

@ApiStatus.Internal
interface SeTabsCustomizer {
  companion object {
    @JvmStatic
    fun getInstance(): SeTabsCustomizer {
      return ApplicationManager.getApplication().getService(SeTabsCustomizer::class.java)
    }
  }

  fun customizeTabInfo(tabId: String, info: SeTabInfo) : SeTabInfo?

  /**
   * Whether the type filter (i.e. the funnel icon) is available for [tabId].
   *
   * Note: when disabling user-facing filter, make sure the filters are normalized to some clean state, because users
   * don't have an escape hatch to clear persisted settings after UI is gone.
   */
  fun isTypeFilterEnabled(tabId: String): Boolean = true
}

@ApiStatus.Internal
class SeTabInfo(val priority: Int, val name: @Nls String)