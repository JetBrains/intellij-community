// The expected scores are calculated using the `sentence_bleu` method from the Python package `sacrebleu`
package com.intellij.cce.metric
import com.intellij.cce.metric.util.computeBleuScore
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ComputeBleuScoreTest {

  @Test
  fun `test identical texts`() {
    val text = "The quick brown fox jumps over the lazy dog."
    val score = computeBleuScore(text, text)
    assertEquals(1.0, score, 0.000001)
  }

  @Test
  fun `test completely different texts`() {
    val candidate = "The quick brown fox jumps over the lazy dog."
    val reference = "An unrelated sentence with different words."
    val score = computeBleuScore(candidate, reference)
    assertEquals(0.0, score, 0.000001)
  }

  @Test
  fun `test partially overlapping texts`() {
    val candidate = "The quick brown fox jumps over the lazy dog."
    val reference = "The quick brown fox leaps over the lazy cat."
    val score = computeBleuScore(candidate, reference)
    assertEquals(0.392815, score, 0.000001)
  }

  @Test
  fun `test empty candidate text`() {
    val candidate = ""
    val reference = "The quick brown fox jumps over the lazy dog."
    val score = computeBleuScore(candidate, reference)
    assertEquals(0.0, score, 0.000001)
  }

  @Test
  fun `test empty reference text`() {
    val candidate = "The quick brown fox jumps over the lazy dog."
    val reference = ""
    val score = computeBleuScore(candidate, reference)
    assertEquals(0.0, score, 0.000001)
  }

  @Test
  fun `test candidate longer than reference`() {
    val candidate = "The quick brown fox jumps over the lazy dog multiple times."
    val reference = "The quick brown fox jumps over the lazy dog."
    val score = computeBleuScore(candidate, reference)
    assertEquals(0.729257, score, 0.000001)
  }

  @Test
  fun `test candidate shorter than reference`() {
    val candidate = "The quick brown fox."
    val reference = "The quick brown fox jumps over the lazy dog."
    val score = computeBleuScore(candidate, reference)
    assertEquals(0.260130, score, 0.000001)
  }

  @Test
  fun `test with punctuation differences`() {
    val candidate = "The quick brown fox jumps over the lazy dog"
    val reference = "The quick brown fox jumps over the lazy dog."
    val score = computeBleuScore(candidate, reference)
    assertEquals(0.894839, score, 0.000001)
  }

  @Test
  fun `test case sensitivity`() {
    val candidate = "The Quick Brown Fox Jumps Over The Lazy Dog."
    val reference = "the quick brown fox jumps over the lazy dog."
    val score = computeBleuScore(candidate, reference)
    assertEquals(1.0, score, 0.000001)
  }
}
