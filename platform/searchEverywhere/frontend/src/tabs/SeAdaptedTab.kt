// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.tabs

import com.intellij.ide.actions.bigPopup.ShowFilterAction
import com.intellij.ide.actions.searcheverywhere.PreviewAction
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereContributor
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereToggleAction
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.Project
import com.intellij.platform.searchEverywhere.SeFilter
import com.intellij.platform.searchEverywhere.SeFilterState
import com.intellij.platform.searchEverywhere.SeSession
import com.intellij.platform.searchEverywhere.frontend.AutoToggleAction
import com.intellij.platform.searchEverywhere.frontend.SeFilterEditor
import com.intellij.platform.searchEverywhere.frontend.resultsProcessing.SeTabDelegate
import com.intellij.platform.searchEverywhere.toProviderId
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.Nls
import java.util.UUID

/**
 * Adapted tab for unsupported legacy SearchEverywhereContributors
 */
@ApiStatus.Internal
class SeAdaptedTab private constructor(delegate: SeTabDelegate,
                                       override val name: @Nls String,
                                       override val id: String,
                                       override val priority: Int,
                                       private val filterEditor: SeAdaptedTabFilterEditor?): SeDefaultTabBase(delegate) {
  val autoToggleAction: AutoToggleAction? get() = filterEditor as? AutoToggleAction

  override suspend fun getFilterEditor(): SeFilterEditor? = filterEditor

  companion object {
    suspend fun create(legacyContributorId: String,
                       name: @Nls String,
                       priority: Int,
                       filterEditor: SeAdaptedTabFilterEditor?,
                       scope: CoroutineScope,
                       project: Project?,
                       session: SeSession,
                       initEvent: AnActionEvent): SeAdaptedTab {
      val delegate = SeTabDelegate.create(project,
                                          session,
                                          legacyContributorId,
                                          listOf(legacyContributorId.toProviderId()),
                                          initEvent,
                                          scope)

      return SeAdaptedTab(delegate, name, legacyContributorId, priority, filterEditor)
    }
  }
}

@ApiStatus.Internal
class SeAdaptedTabFilterEditor(val contributor: SearchEverywhereContributor<Any>) : SeFilterEditor, AutoToggleAction {
  override val resultFlow: StateFlow<SeFilterState> get() = _resultFlow.asStateFlow()
  private val _resultFlow = MutableStateFlow(SeAdaptedTabFilter().toState())

  private var isAutoToggleEnabled: Boolean = true
  private var lastAutoToggleValue: Boolean = true

  private val actions: List<AnAction> = contributor.getActions {
    if (toggleAction?.isEverywhere?.let { it == lastAutoToggleValue } == false) {
      // If the autoToggled value differs from the applied value, we disable autoToggling.
      isAutoToggleEnabled = false
    }

    // Generate the new value to restart the search
    _resultFlow.value = SeAdaptedTabFilter().toState()
  }.filter {
    // Intentionally avoid preview action because it's not supported at this moment for adapted tabs
    it !is PreviewAction
  }

  private val toggleAction = actions.filterIsInstance<SearchEverywhereToggleAction>().firstOrNull()?.also {
    lastAutoToggleValue = it.isEverywhere
  }

  override fun getHeaderActions(): List<AnAction> = actions

  override fun closeFilterPopup() {
    actions.filterIsInstance<ShowFilterAction>().forEach { it.closeFilterPopup() }
  }

  override fun autoToggle(everywhere: Boolean): Boolean {
    toggleAction ?: return false

    if (!toggleAction.canToggleEverywhere() || !isAutoToggleEnabled || toggleAction.isEverywhere == everywhere) return false
    lastAutoToggleValue = everywhere
    toggleAction.setEverywhere(everywhere)
    return true
  }
}

/**
 * Filter for adapted tabs that doesn't bring any value, but just notifies that the filter was changed
 */
@ApiStatus.Internal
class SeAdaptedTabFilter: SeFilter {
  override fun toState(): SeFilterState =
    SeFilterState.Data(mapOf(IS_ADAPTED_FILTER to listOf("true"),
                             // Generate a random value so that the filter change would restart the search
                             RANDOM_VALUE to listOf(UUID.randomUUID().toString())))

  companion object {
    private const val IS_ADAPTED_FILTER = "IS_ADAPTED_FILTER"
    private const val RANDOM_VALUE = "RANDOM_VALUE"

    fun from(state: SeFilterState): SeAdaptedTabFilter? {
      return when (state) {
        is SeFilterState.Data -> {
          if (state.getBoolean(IS_ADAPTED_FILTER) ?: false)
            SeAdaptedTabFilter()
          else null
        }
        SeFilterState.Empty -> null
      }
    }
  }
}
