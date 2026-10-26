// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.openapi.Disposable
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.util.registry.RegistryManager
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls

private const val RANKER_REGISTRY_KEY: String = "search.everywhere.actions.ranker"
private const val DEFAULT_MAX_CANDIDATES: Int = 256

/**
 * A remote model that orders IDE actions by how well they match a natural-language query.
 *
 * The `search.everywhere.actions.ranker` registry key selects one ranker by its [id].
 * [SeRankerActionsProvider] opens a [SeActionsRankerSession] of the selected ranker for each Search Everywhere popup.
 */
@Internal
interface SeActionsRanker {
  /**
   * The value of the `search.everywhere.actions.ranker` registry key that selects this ranker.
   */
  val id: String

  /**
   * Opens a session for one Search Everywhere popup, or returns `null` when the ranker cannot rank now.
   * For example, a ranker cannot rank while the user is signed out.
   *
   * [SeRankerActionsProvider] opens the session on the first query and disposes it when the popup closes.
   * While this function returns `null`, the provider calls it again on each query, thus a `null` answer must be cheap.
   *
   * The call must be cancellable. Search Everywhere cancels it when the query changes.
   */
  suspend fun openSession(): SeActionsRankerSession?

  companion object {
    val EP_NAME: ExtensionPointName<SeActionsRanker> = ExtensionPointName("com.intellij.searchEverywhere.actionsRanker")

    /**
     * Returns the ranker that the registry key selects, or `null` when the key is empty or no ranker has this id.
     */
    internal suspend fun findSelected(): SeActionsRanker? {
      val rankerId = RegistryManager.getInstanceAsync().stringValue(RANKER_REGISTRY_KEY)
      if (rankerId.isNullOrEmpty()) return null

      return EP_NAME.extensionList.firstOrNull { it.id == rankerId }
    }
  }
}

/**
 * The ranking of one [SeActionsRanker] for one Search Everywhere popup.
 *
 * The session holds the resources that the ranking needs while the popup is open, such as a lease of a proxy route.
 * [dispose] releases them. [SeRankerActionsProvider] disposes the session when the popup closes.
 */
@Internal
interface SeActionsRankerSession : Disposable {
  /**
   * The maximum number of candidates that one [rank] call accepts. The value must be at least 2.
   *
   * When there are more candidates, [SeActionsCandidatesStrategy] decides which candidates each call gets.
   */
  val maxCandidates: Int get() = DEFAULT_MAX_CANDIDATES

  /**
   * Orders [candidates] by how well they match [query].
   * The list contains no more than [maxCandidates] candidates, except with [SeActionsCandidatesStrategy.UNLIMITED].
   *
   * The call must be cancellable. Search Everywhere cancels it when the query changes.
   *
   * @return the matching candidates, the best match first.
   * The result does not contain the candidates that do not match.
   */
  suspend fun rank(query: String, candidates: List<SeActionsRankerCandidate>): List<SeActionsRankerMatch>

  override fun dispose() {}
}

/**
 * The data that [SeActionsRanker] sends to a remote model for one action.
 *
 * It holds only the action id, and the name and the description of the action template.
 * [SeRankerActionsProvider] takes them only from actions that plugin descriptors declare, thus they are the same in each project and context.
 * It holds no code, paths, project names or user identifiers.
 *
 * @property id the action id in `ActionManager`.
 */
@Internal
data class SeActionsRankerCandidate(val id: String, val name: @Nls String, val description: @Nls String?)

/**
 * One candidate that matches the query of a [SeActionsRankerSession.rank] call.
 *
 * @property id the id of the [SeActionsRankerCandidate].
 * @property probability the probability that the candidate does what the query asks for, from 0 to 1.
 * It is `null` when the ranker gives no probability. Search Everywhere shows it only as diagnostic data.
 */
@Internal
data class SeActionsRankerMatch(val id: String, val probability: Double?)
