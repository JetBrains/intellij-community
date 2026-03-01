// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.ml

import com.intellij.internal.statistic.utils.getPluginInfo
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IntellijInternalApi
import com.intellij.platform.searchEverywhere.SeItemData
import com.intellij.platform.searchEverywhere.SeParams
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
@IntellijInternalApi
interface SeMlService {
  val isEnabled: Boolean

  /**
   * Informs the ML service that a search session has started
   * (Search Everywhere window was opened)
   *
   * @param project the project in which the search session was started
   * @param tabId the id of the tab in which the search session was started
   */
  fun onSessionStarted(project: Project?, tabId: String)

  /**
   * Applies machine learning-based weight adjustments to the provided search item data.
   *
   * @param seItemData the search item data to which the machine learning weight adjustment will be applied
   * @return a new instance of [SeItemData] containing the updated weight information
   */
  fun applyMlWeight(seItemData: SeItemData): SeItemData

  /**
   * Notifies the service that the UI list has received updated search results.
   */
  fun notifySearchResultsUpdated()

  /**
   * Notifies the ML service that the search state has changed.
   * The reason for the change may be:
   *   - Tab change
   *   - Query change
   *   - Filter change
   *
   * @param tabId ID of the current tab
   * @param searchParams Current search parameters
   */
  fun onStateStarted(tabId: String, searchParams: SeParams)

  /**
   * Notifies that the processing of a specific state has been completed, along with
   * the results corresponding to that state.
   *
   * Note that on fast typing, even when the search had no time to produce results,
   * we still expect this method to be called. In this case - an empty list of results
   * may be passed.
   *
   * @param stateId An identifier representing the specific state that has finished processing.
   * @param results A list of results, represented as [SeItemData], that were processed during the state.
   */
  fun onStateFinished(results: List<SeItemData>)

  /**
   * Notifies the service when specific search results have been selected by the user.
   *
   * @param selectedResults A list of selected results, where each result is a pair.
   *                        The first element in the pair is an integer representing the index
   *                        of the result, and the second element is an instance of [SeItemData]
   *                        containing the details of the selected search item.
   */
  fun onResultsSelected(selectedResults: List<Pair<Int, SeItemData>>)

  /**
   * Notifies the service that a search session has finished.
   */
  fun onSessionFinished()

  companion object {
    private val EP_NAME = ExtensionPointName.create<SeMlService>("com.intellij.searchEverywhere.mlService")

    fun getInstanceIfEnabled(): SeMlService? {
        return EP_NAME.extensionList
          .filter { getPluginInfo(it.javaClass).isDevelopedByJetBrains() }
          .firstOrNull { it.isEnabled }
    }
  }
}
