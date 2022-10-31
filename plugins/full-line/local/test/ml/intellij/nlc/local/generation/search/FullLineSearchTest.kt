package ml.intellij.nlc.local.generation.search

import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class FullLineSearchTest {
  private lateinit var search: FullLineBeamSearch

  private fun makeOneStep() {
    search.step(arrayOf(doubleArrayOf(0.13, 0.4, 0.17, 0.1, 0.2)), IntArray(0))
  }

  private fun makeFiveSteps() {
    val fakeLogProbs = doubleArrayOf(0.13, 0.4, 0.17, 0.1, 0.2)
    var fakeLogProbsRepeat = 1

    for (i in 0..4) {
      val stepResult = search.step(Array(fakeLogProbsRepeat) { fakeLogProbs }, IntArray(0))
      fakeLogProbsRepeat = stepResult.sortMask.size
    }
  }

  @BeforeEach
  fun initSearch() {
    search = FullLineBeamSearch(
      vocabSize = 5,
      searchSize = 3,
    )
  }

  @Test
  fun `test step - scores`() {
    makeOneStep()
    val targetScores = listOf(-1.4152, -1.6152, -1.6452)
    search.hypothesesScores.zip(targetScores).forEach { (actual, expected) ->
      Assertions.assertEquals(
        expected, actual, 1e-4, "$targetScores != ${search.hypothesesScores}"
      )
    }
  }

  @Test
  fun `test step - hypotheses`() {
    makeOneStep()
    val targetScores = listOf(0.2429, 0.1988, 0.1930)
    val hypothesesScores = search.hypotheses.map { it.score }
    hypothesesScores.zip(targetScores).forEach { (actual, expected) ->
      Assertions.assertEquals(expected, actual, 1e-4, "$targetScores != $hypothesesScores")
    }
  }

  @Test
  fun `test five iterations - scores`() {
    makeFiveSteps()
    val targetScores = listOf(-7.0762, -7.2762, -7.2762)
    search.hypothesesScores.zip(targetScores).forEach { (actual, expected) ->
      Assertions.assertEquals(
        expected, actual, 1e-4, "$targetScores != ${search.hypothesesScores}"
      )
    }
  }

  @Test
  fun `test five iterations - hypotheses`() {
    makeFiveSteps()
    val targetScores = listOf(8.4499E-4, 6.9182E-4, 6.9182E-4)
    val hypothesesScores = search.hypotheses.map { it.score }
    hypothesesScores.zip(targetScores).forEach { (actual, expected) ->
      Assertions.assertEquals(expected, actual, 1e-7, "$targetScores != $hypothesesScores")
    }
  }
}
