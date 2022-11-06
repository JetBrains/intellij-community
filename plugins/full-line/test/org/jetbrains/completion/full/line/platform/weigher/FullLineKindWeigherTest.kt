package org.jetbrains.completion.full.line.platform.weigher

import junit.framework.TestCase
import org.jetbrains.completion.full.line.AnalyzedFullLineProposal
import org.jetbrains.completion.full.line.FullLineProposal
import org.jetbrains.completion.full.line.ReferenceCorrectness
import org.jetbrains.completion.full.line.platform.FullLineLookupElement
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito

internal class FullLineKindWeigherTest : TestCase() {
  private val tabWeigher = FullLineTabWeigher()
  private val syntaxWeigher = FullLineSyntaxCorrectnessWeigher()
  private val refWeigher = FullLineReferenceCorrectnessWeigher()
  private val scoreWeigher = FullLineScoreWeigher()

  fun `test weighFullLineLookup by tabSelected`() {
    val lookups = listOf(
      // ref correct
      getMockedFLLookup(100.0, false, ReferenceCorrectness.CORRECT, true),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.CORRECT, false),
      // ref incorrect
      getMockedFLLookup(80.0, false, ReferenceCorrectness.INCORRECT, true),
      getMockedFLLookup(70.0, false, ReferenceCorrectness.INCORRECT, false),
      // ref undefined
      getMockedFLLookup(60.0, false, ReferenceCorrectness.UNDEFINED, true),
      getMockedFLLookup(50.0, false, ReferenceCorrectness.UNDEFINED, false),
      // lowest score, worst correctness & tab
      getMockedFLLookup(1.0, true, ReferenceCorrectness.UNDEFINED, false),
    ).assertNoMoreThanOneTab()
      .sortedWith(compareByDescending(tabWeigher::weighFullLineLookup))

    assertAll(
      // Top lookup must eq 100
      { assertTrue(lookups.first().proposal.score == 1.0) },
      // Only one lookup must eq 100
      { assertTrue(lookups.count { it.proposal.score == 1.0 } == 1) }
    )
  }

  fun `test weighFullLineLookup by ref correctness`() {
    val lookups = testKindWeigher(refWeigher::weighFullLineLookup)
    // Expected ref correctness order
    val expected = listOf(
      ReferenceCorrectness.CORRECT,
      ReferenceCorrectness.CORRECT,
      ReferenceCorrectness.CORRECT,
      ReferenceCorrectness.CORRECT,
      ReferenceCorrectness.INCORRECT,
      ReferenceCorrectness.INCORRECT,
      ReferenceCorrectness.INCORRECT,
      ReferenceCorrectness.INCORRECT,
      ReferenceCorrectness.UNDEFINED,
      ReferenceCorrectness.UNDEFINED,
      ReferenceCorrectness.UNDEFINED,
      ReferenceCorrectness.UNDEFINED,
    )

    // Check that was sorted as expected
    assertEquals(expected, lookups.map { it.proposal.refCorrectness })
  }

  fun `test weighFullLineLookup by syntax correctness`() {
    val lookups = testKindWeigher(syntaxWeigher::weighFullLineLookup)
    assertAll(
      // Check that the first half is syntax-correct
      {
        assertTrue(lookups.subList(0, lookups.size / 2).map { it.proposal.isSyntaxCorrect }
                     .all { it == FullLineProposal.BasicSyntaxCorrectness.CORRECT })
      },

      // Check that the second half is not syntax-correct
      {
        assertFalse(lookups.subList(lookups.size / 2, lookups.size).map { it.proposal.isSyntaxCorrect }
                      .all { it == FullLineProposal.BasicSyntaxCorrectness.CORRECT })
      },
    )
  }

  fun `test weighFullLineLookup by score`() {
    val initLookups = listOf(
      getMockedFLLookup(100.0, false, ReferenceCorrectness.CORRECT, true),
      getMockedFLLookup(50.0, false, ReferenceCorrectness.INCORRECT, true),
      getMockedFLLookup(60.0, false, ReferenceCorrectness.UNDEFINED, true),
      getMockedFLLookup(70.0, false, ReferenceCorrectness.CORRECT, false),
      getMockedFLLookup(50.0, false, ReferenceCorrectness.INCORRECT, false),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.UNDEFINED, false),
      getMockedFLLookup(80.0, false, ReferenceCorrectness.CORRECT, true),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.INCORRECT, true),
      getMockedFLLookup(50.0, false, ReferenceCorrectness.UNDEFINED, true),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.CORRECT, false),
    ).assertNoMoreThanOneTab()
    val lookups = initLookups
      .sortedWith(compareByDescending(scoreWeigher::weighFullLineLookup))
      .map { it.proposal.score }
    assertAll(
      // top 3 must eq 100
      { assertEquals(lookups.subList(0, 3), listOf(100.0, 100.0, 100.0)) },
      // bottom 3 must eq 50
      { assertEquals(lookups.subList(lookups.size - 3, lookups.size), listOf(50.0, 50.0, 50.0)) },
      // lookups must be sorted only by score
      { assertEquals(lookups, initLookups.map { it.proposal.score }.sortedByDescending { it }) },
    )
  }

  fun `test weighFullLineLookup by kind and score`() {
    val lookups = listOf(
      // with score 100
      getMockedFLLookup(100.0, false, ReferenceCorrectness.CORRECT, true),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.INCORRECT, true),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.UNDEFINED, true),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.CORRECT, false),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.INCORRECT, false),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.UNDEFINED, false),
      // with score 90
      getMockedFLLookup(90.0, false, ReferenceCorrectness.CORRECT, true),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.INCORRECT, true),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.UNDEFINED, true),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.CORRECT, false),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.INCORRECT, false),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.UNDEFINED, false),
      // lowest score, worst correctness & tab
      getMockedFLLookup(1.0, true, ReferenceCorrectness.UNDEFINED, false),
    ).assertNoMoreThanOneTab()
      .sortedWith(
        compareBy(
          tabWeigher::weighFullLineLookup,
          syntaxWeigher::weighFullLineLookup,
          refWeigher::weighFullLineLookup,
          scoreWeigher::weighFullLineLookup,
        )
      ).asReversed()

    // Tab always go first and then:
    // - sorting by syntax
    // - sorting by ref
    // - sorting by score
    val expected = listOf(
      getMockedFLLookup(1.0, true, ReferenceCorrectness.UNDEFINED, false),

      getMockedFLLookup(100.0, false, ReferenceCorrectness.CORRECT, true),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.CORRECT, true),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.INCORRECT, true),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.INCORRECT, true),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.UNDEFINED, true),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.UNDEFINED, true),

      getMockedFLLookup(100.0, false, ReferenceCorrectness.CORRECT, false),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.CORRECT, false),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.INCORRECT, false),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.INCORRECT, false),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.UNDEFINED, false),
      getMockedFLLookup(90.0, false, ReferenceCorrectness.UNDEFINED, false),
    )

    assertAll(
      // Check all fields by which you sorted
      { assertEquals(expected.map { it.proposal.score }, lookups.map { it.proposal.score }) },
      { assertEquals(expected.map { it.selectedByTab }, lookups.map { it.selectedByTab }) },
      {
        assertEquals(
          expected.map { it.proposal.refCorrectness },
          lookups.map { it.proposal.refCorrectness })
      },
      {
        assertEquals(
          expected.map { it.proposal.isSyntaxCorrect },
          lookups.map { it.proposal.isSyntaxCorrect })
      },
    )
  }

  private inline fun testKindWeigher(crossinline selector: (FullLineLookupElement) -> Comparable<*>?): List<FullLineLookupElement> {
    return listOf(
      // same score
      getMockedFLLookup(100.0, false, ReferenceCorrectness.CORRECT, true),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.INCORRECT, true),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.UNDEFINED, true),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.CORRECT, false),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.INCORRECT, false),
      getMockedFLLookup(100.0, false, ReferenceCorrectness.UNDEFINED, false),
      // different score
      getMockedFLLookup(90.0, false, ReferenceCorrectness.CORRECT, true),
      getMockedFLLookup(80.0, false, ReferenceCorrectness.INCORRECT, true),
      getMockedFLLookup(70.0, false, ReferenceCorrectness.UNDEFINED, true),
      getMockedFLLookup(60.0, false, ReferenceCorrectness.CORRECT, false),
      getMockedFLLookup(50.0, false, ReferenceCorrectness.INCORRECT, false),
      getMockedFLLookup(40.0, false, ReferenceCorrectness.UNDEFINED, false),
    ).assertNoMoreThanOneTab()
      .sortedWith(compareByDescending(selector))
  }

  private fun getMockedFLLookup(
    score: Double,
    tab: Boolean,
    ref: ReferenceCorrectness,
    syntax: Boolean,
  ): FullLineLookupElement {
    assertTrue(score > 0, "FullLineLookup's score must be more than zero: $score")
    return Mockito.mock(FullLineLookupElement::class.java).apply {
      Mockito.`when`(selectedByTab).thenReturn(tab)
      val a = Mockito.mock(AnalyzedFullLineProposal::class.java).apply {
        Mockito.`when`(this.score).thenReturn(score)
        Mockito.`when`(refCorrectness).thenReturn(ref)
        Mockito.`when`(isSyntaxCorrect).thenReturn(FullLineProposal.BasicSyntaxCorrectness.fromBoolean(syntax))
      }
      Mockito.`when`(proposal).thenReturn(a)
    }
  }

  private fun List<FullLineLookupElement>.assertNoMoreThanOneTab(): List<FullLineLookupElement> {
    assertTrue(count { it.selectedByTab } <= 1, "In same session only 1 FullLineLookup can be tab-selected")
    return this
  }
}
