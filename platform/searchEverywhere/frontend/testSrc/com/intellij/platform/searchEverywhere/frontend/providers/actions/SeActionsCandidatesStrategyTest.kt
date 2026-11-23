// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.searchEverywhere.frontend.providers.actions

import com.intellij.ide.ui.search.SearchableOptionsRegistrar
import com.intellij.ide.ui.search.SearchableOptionsRegistrarImpl
import com.intellij.openapi.components.serviceAsync
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentLinkedQueue

private const val MAX_CANDIDATES: Int = 8
private const val MATCHED_OPTIONS: Int = 3

/**
 * [SeActionsCandidatesStrategy.POPULARITY] gives a part of the candidates to the settings options that match the query words.
 *
 * The tests take the options of the query words from the word index of the registrar in the test application.
 */
@TestApplication
class SeActionsCandidatesStrategyTest {
  private val actions = (1..MAX_CANDIDATES * 2).map { SeActionsRankerCandidate("test.action.$it", "Test action $it", null) }
  private val otherOptions = listOf(option("option:test|first"), option("option:test|second"))

  @Test
  fun `popularity gives up to a quarter of the candidates to the options that match the query`(): Unit = timeoutRunBlocking {
    val (word, matchedIds) = findWordWithOptions()
    val matchedOptions = matchedIds.map { option(it) }
    val session = RecordingSession()

    SeActionsCandidatesStrategy.POPULARITY.rank(session, word, actions + otherOptions + matchedOptions)

    val sent = session.calls.single()
    val sentOptions = sent.filter { it.isOption }
    assertEquals(MAX_CANDIDATES, sent.size)
    assertEquals(MAX_CANDIDATES / 4, sentOptions.size)
    assertTrue(matchedOptions.containsAll(sentOptions), "Sent options: $sentOptions")
  }

  @Test
  fun `popularity gives all candidates to the actions when no option matches the query`(): Unit = timeoutRunBlocking {
    val session = RecordingSession()

    SeActionsCandidatesStrategy.POPULARITY.rank(session, "qzxwvkj", actions + otherOptions)

    val sent = session.calls.single()
    assertEquals(MAX_CANDIDATES, sent.size)
    assertTrue(sent.none { it.isOption }, "Sent candidates: $sent")
  }

  /**
   * Returns a word of the word index, and the candidate ids of [MATCHED_OPTIONS] options that the word finds.
   */
  private suspend fun findWordWithOptions(): Pair<String, List<String>> {
    val registrar = serviceAsync<SearchableOptionsRegistrar>() as SearchableOptionsRegistrarImpl
    registrar.initialize()
    return registrar.getStorage()
      .filter { (word, _) -> word.length > 3 && word.all { it.isLetter() } }
      .map { (word, descriptions) -> word to descriptions.mapNotNull { optionCandidateId(it) }.distinct().take(MATCHED_OPTIONS).toList() }
      .first { (_, ids) -> ids.size == MATCHED_OPTIONS }
  }

  private fun option(id: String): SeActionsRankerCandidate = SeActionsRankerCandidate(id, id, null, isOption = true)

  private class RecordingSession : SeActionsRankerSession {
    val calls = ConcurrentLinkedQueue<List<SeActionsRankerCandidate>>()

    override val maxCandidates: Int get() = MAX_CANDIDATES

    override suspend fun rank(query: String, candidates: List<SeActionsRankerCandidate>): List<SeActionsRankerMatch> {
      calls.add(candidates)
      return candidates.map { SeActionsRankerMatch(it.id, probability = null) }
    }
  }
}
