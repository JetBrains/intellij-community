package com.intellij.cce.metric

import com.intellij.cce.core.Lookup
import com.intellij.cce.metric.util.CloudSemanticSimilarityCalculator
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.spy
import org.mockito.kotlin.whenever

class SemanticSimilarityMetricTest : BasePlatformTestCase() {

  fun `test lookup empty`() {
    val additionalInfo = emptyMap<String, List<*>>()
    val mockProposals = emptyList<String>()
    val mockSimilarityScores = emptyList<Double>()
    val expectedScore = null

    val semanticSimilarityScore = simulateSimilarityMetricCalculation(additionalInfo, mockProposals, mockSimilarityScores)

    Assertions.assertEquals(expectedScore, semanticSimilarityScore)
  }

  fun `test lookup with both result_proposals and raw_proposals`() {
    val additionalInfo = mapOf(
      "raw_proposals" to listOf(
        mapOf(
          "first" to "the quick brown",
        ),
        mapOf(
          "first" to "fox jumps",
        ),
      ),
      "result_proposals" to listOf(
        mapOf(
          "first" to "over the lazy dog",
        ),
      )
    )
    val mockProposals = listOf("the quick brown", "fox jumps", "over the lazy dog")
    val mockSimilarityScores = listOf(0.15, 0.65, 1.0)
    val expectedScore = 0.4

    val semanticSimilarityScore = simulateSimilarityMetricCalculation(additionalInfo, mockProposals, mockSimilarityScores)

    Assertions.assertEquals(expectedScore, semanticSimilarityScore)
  }

  fun `test lookup with only raw_proposals`() {
    val additionalInfo = mapOf(
      "raw_proposals" to listOf(
        mapOf(
          "first" to "hello world",
        ),
        mapOf(
          "first" to "quick brown",
        ),
        mapOf(
          "first" to "the quick brown fox",
        ),
      ),
    )
    val mockProposals = listOf("hello world", "quick brown", "the quick brown fox")
    val mockSimilarityScores = listOf(0.15, 0.65, 1.0)
    val expectedScore = 0.6

    val semanticSimilarityScore = simulateSimilarityMetricCalculation(additionalInfo, mockProposals, mockSimilarityScores)

    Assertions.assertEquals(expectedScore, semanticSimilarityScore)
  }

  fun `test lookup with only result_proposals`() {
    val additionalInfo = mapOf(
      "raw_proposals" to listOf(),
      "result_proposals" to listOf(
        mapOf(
          "first" to "over the lazy dog",
        ),
      )
    )
    val mockProposals = listOf("the quick brown", "fox jumps", "over the lazy dog")
    val mockSimilarityScores = listOf(0.15, 0.65, 1.0)
    val expectedScore = 1.0

    val semanticSimilarityScore = simulateSimilarityMetricCalculation(additionalInfo, mockProposals, mockSimilarityScores)

    Assertions.assertEquals(expectedScore, semanticSimilarityScore)
  }

  private fun simulateSimilarityMetricCalculation(additionalInfo: Map<String, Any>, mockProposals: List<String>, mockSimilarityScores: List<Double>): Double? = runBlocking {
    val mockedEmbeddingsProvider = Mockito.mock<CloudSemanticSimilarityCalculator>()

    whenever(mockedEmbeddingsProvider.calculateCosineSimilarity(
      project = any(),
      proposal = any(),
      expectedText = any()
    )).thenThrow(IllegalArgumentException("No mock score provided for this proposal, failed to calculate semantic similarity."))

    mockProposals.zip(mockSimilarityScores).forEach { (proposal, score) ->
      whenever(mockedEmbeddingsProvider.calculateCosineSimilarity(
        project = any(),
        proposal = eq(proposal),
        expectedText = any()
      )).thenReturn(score)
    }

    val semanticSimilarityScoreCalculator = spy(SemanticSimilarityScore(true, mockedEmbeddingsProvider))

    semanticSimilarityScoreCalculator.computeSimilarity(createLookup(additionalInfo), "")
  }

  private fun createLookup(additionalInfo: Map<String, Any>): Lookup =
    Lookup.fromExpectedText("", "", listOf(), 0,
                            comparator = { generated, expected_ -> !(generated.isEmpty() || !expected_.startsWith(generated)) },
                            additionalInfo = additionalInfo)
}