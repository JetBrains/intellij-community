// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.ui.search.OptionDescription
import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.ide.util.gotoByName.createFindActionOptionsFilter
import com.intellij.ide.util.gotoByName.getGroupName
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.progress.checkCanceled

private const val OPTION_ID_PREFIX: String = "option:"

/**
 * A settings option that [SeRankerActionsProvider] can send to the ranker, with the [description] that the result list shows.
 */
internal class SeOptionCandidate(val rankerCandidate: SeActionsRankerCandidate, val description: OptionDescription)

/**
 * Returns the settings options that can be ranker candidates, sorted by their ids.
 *
 * They are the options of the searchable options files that the action search shows.
 * See [SearchableOptionsRegistrarImpl.getIndexedOptionDescriptions] and [createFindActionOptionsFilter].
 * The ranker gets the hit of each option as the name, and the settings page as the description.
 * When one settings page has more options with one hit, the function keeps the first option.
 */
internal suspend fun collectOptionCandidates(): List<SeOptionCandidate> {
  val registrar = serviceAsync<SearchableOptionsRegistrar>() as SearchableOptionsRegistrarImpl
  registrar.initialize()

  val isShown = createFindActionOptionsFilter()
  val candidates = HashMap<String, SeOptionCandidate>()
  for (description in registrar.getIndexedOptionDescriptions()) {
    val hit = description.hit?.takeIf { it.isNotBlank() } ?: continue
    val id = optionCandidateId(description) ?: continue
    if (id in candidates || !isShown(description)) continue

    candidates[id] = SeOptionCandidate(SeActionsRankerCandidate(id, hit, getGroupName(description), isOption = true), description)
  }
  return candidates.values.sortedBy { it.rankerCandidate.id }
}

/**
 * Returns the id of the ranker candidate of [description], or `null` when the option has no settings page or no hit.
 * The id holds the settings page and the hit, thus the options of one page with one hit get one id.
 */
internal fun optionCandidateId(description: OptionDescription): String? {
  val configurableId = description.configurableId ?: return null
  val hit = description.hit ?: return null
  return "$OPTION_ID_PREFIX$configurableId|$hit"
}

/**
 * Selects up to [limit] options of [candidates] that match the most words of [query].
 *
 * The word index of the searchable options registrar matches the words, as the action search does.
 * It matches a word by its prefix and by its stem.
 */
internal suspend fun selectOptionsByQueryWords(
  query: String,
  candidates: List<SeActionsRankerCandidate>,
  limit: Int,
): List<SeActionsRankerCandidate> {
  if (limit <= 0 || candidates.isEmpty()) return emptyList()

  val registrar = serviceAsync<SearchableOptionsRegistrar>() as SearchableOptionsRegistrarImpl
  registrar.initialize()

  val matchedWords = HashMap<String, Int>()
  for (word in registrar.getProcessedWords(query)) {
    checkCanceled()
    val ids = registrar.findAcceptableDescriptions(word)?.mapNotNullTo(HashSet()) { optionCandidateId(it) } ?: continue
    for (id in ids) {
      matchedWords.merge(id, 1, Int::plus)
    }
  }
  return selectByMatchedWords(candidates, matchedWords, limit)
}

/**
 * Returns up to [limit] of [candidates] with the most matched words. [matchedWords] gives the number of the words of each candidate id.
 *
 * The result has no candidate without matched words.
 * The order of [candidates] decides between candidates with an equal number of words.
 */
internal fun selectByMatchedWords(
  candidates: List<SeActionsRankerCandidate>,
  matchedWords: Map<String, Int>,
  limit: Int,
): List<SeActionsRankerCandidate> {
  return candidates
    .filter { it.id in matchedWords }
    .sortedByDescending { matchedWords.getValue(it.id) }
    .take(limit)
}
