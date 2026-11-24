// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import kotlinx.coroutines.CoroutineScope
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

  /**
   * The filter editor that the popup of [project] shows for the tab [tabId], in place of [editor], the editor of the tab.
   *
   * A product that searches several projects in one popup puts its own scope control here, see
   * `SeSearchProjectsProvider`. Such a tab builds [editor] without a scope control. The result takes the filter state
   * of [editor] and adds its own keys, so the other controls of [editor] keep their effect.
   * [coroutineScope] is the scope of the tab in the popup.
   */
  fun customizeFilterEditor(tabId: String, project: Project?, editor: SeFilterEditor, coroutineScope: CoroutineScope): SeFilterEditor = editor
}

@ApiStatus.Internal
class SeTabInfo(val priority: Int, val name: @Nls String)