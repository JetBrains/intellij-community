// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.text.matching

import org.jetbrains.annotations.ApiStatus

/**
 * Fuzzy search using the Bitap algorithm.
 *
 * A pattern word must be short enough to fit a register (64 chars). To create an instance, use [tryCreate] only.
 * Callers that need to know upfront whether a pattern can be served should ask [isSupported].
 */
@ApiStatus.Internal
class BitapFuzzySearchAlgorithm private constructor(pattern: String) {

  /**
   * Lowercased char by char rather than with [String.lowercase], because for a few chars (e.g. `İ`) the latter changes
   * the length of the string, which would desynchronize the masks and the reported offsets from the pattern.
   */
  private val myPatternLowercase: CharArray = CharArray(pattern.length) { pattern[it].lowercaseChar() }

  /**
   * Masks for the ASCII pattern chars, an optimization to skips boxing.
   */
  private val myAsciiMasks = LongArray(ASCII_TABLE_SIZE) { NO_OCCURRENCES }

  private val myNonAsciiMasks: Map<Char, Long>?

  val patternLength: Int get() = myPatternLowercase.size

  init {
    var nonAsciiMasks: HashMap<Char, Long>? = null
    for ((i, patternChar) in myPatternLowercase.withIndex()) {
      val occurrenceHere = (1L shl i).inv()
      val code = patternChar.code
      if (code < ASCII_TABLE_SIZE) {
        myAsciiMasks[code] = myAsciiMasks[code] and occurrenceHere
      }
      else {
        val masks = nonAsciiMasks ?: HashMap<Char, Long>().also { nonAsciiMasks = it }
        masks[patternChar] = (masks[patternChar] ?: NO_OCCURRENCES) and occurrenceHere
      }
    }
    myNonAsciiMasks = nonAsciiMasks
  }

  /**
   * Bitmask of occurrences of [lowercaseChar] in the pattern
   *
   * @return mask or [NO_OCCURRENCES] when not found.
   * */
  private fun maskOf(lowercaseChar: Char): Long {
    val code = lowercaseChar.code
    if (code < ASCII_TABLE_SIZE) return myAsciiMasks[code]
    return myNonAsciiMasks?.get(lowercaseChar) ?: NO_OCCURRENCES
  }

  /**
   * Fuzzy matching using the Bitap algorithm with a tolerance of 1 error if [tolerateErrors] is enabled. Every occurrence
   * goes to [consumer], in the order the occurrences END in [name]. Two occurrences may cover the same chars if they were
   * matched by correcting different typos. An exact occurrence always wins. The search is case-insensitive.
   *
   * What typos it supports:
   * - Missing character, e.g. "foobr" instead of "foob**a**r".
   * - Extra character, e.g. "foob**b**ar" instead of "foobar".
   * - Substitution of a character, e.g. "foob**o**r" instead of "foobar".
   * - Swap of two adjacent characters, e.g. "fo**ob**ar" instead of "foobar".
   *
   * **Implementation details**
   *
   * **Pattern transformation**
   *
   * Patten is transformed to a map of binary masks of all character occurrences, mask is position bits,
   * where 0 is an active bit (character is here) and 1 inactive bit (character isn't here).
   *
   * E.g., the mask of "o" in a pattern "foobar":
   *
   * ```
   * j:          5 4 3 2 1 0
   * pattern:    r a b o o f
   * mask of o:  1 1 1 0 0 1
   * ```
   *
   * **Exact match**
   *
   * The exact match looks for an occurrence of the whole pattern inside [name] with no errors. An active bit `j` means:
   * `pattern[0..j]` matched.
   *
   * So mechanically the "happy path" is active bits walking up towards bit `patternLen - 1`, and checking for a match is
   * as simple as testing that one bit in the state.
   *
   * E.g. the matching pattern "aabac" on name "aabaacaabacab":
   *
   * ```
   *                            name                 masks
   *                 a a b a a c a a b a c a b       a b c
   *             a   0 0 1 0 0 1 0 0 1 0 1 0 1       0 1 1
   *  pattern    a   1 0 1 1 0 1 1 0 1 1 1 1 1       0 1 1
   *     +   --> b   1 1 0 1 1 1 1 1 0 1 1 1 1       1 0 1
   *   state     a   1 1 1 0 1 1 1 1 1 0 1 1 1       0 1 1
   *             c   1 1 1 1 1 1 1 1 1 1 0 1 1       1 1 0
   *                                     ^
   *                                     |
   *                              match ends here
   *
   * 0 is match
   * 1 is not match
   * ```
   *
   * **1-error match**
   *
   * Currently this implementation of Bitap supports only a single error. There are exactly four routes to spend it, and
   * each one consumes a different amount of text:
   *
   * | route         | edit applied to the pattern                  | chars of [name] consumed |
   * |---------------|----------------------------------------------|--------------------------|
   * | substitution  | a char is swapped (`getter` ~ `gutter`)      | [patternLength]          |
   * | transposition | two chars trade places (`getter` ~ `gteter`) | [patternLength]          |
   * | deletion      | a char is dropped (`getter` ~ `gette`)       | [patternLength] - 1      |
   * | insertion     | a char is added (`getter` ~ `gettter`)       | [patternLength] + 1      |
   *
   * A hit always ends at the char in the current iteration, so the end offset is always known. Each route gets its own
   * state because the start offset of a hit depends on which route completed it.
   *
   * @param tolerateErrors whether to report the occurrences with an error.
   * @param consumer receives every occurrence, with an error count. It returns `false` to stop the scan. Consumer is both cheaper
   * on allocations and provides the necessary infrastructure for streaming occurrences.
   * @return `false` when [consumer] stopped the scan, `true` when the whole [name] was read.
   */
  fun processMatches(name: String, tolerateErrors: Boolean, consumer: MatchConsumer): Boolean {
    val patternLen = patternLength
    val fullMatchBit = 1L shl (patternLen - 1)

    if (!tolerateErrors) return processExactMatches(name, fullMatchBit, patternLen, consumer)
    // Too short to match: even the deletion route needs `patternLen - 1` chars.
    if (name.length < patternLen - 1) return true

    var exactMatches = NO_OCCURRENCES
    // the exact match state two chars back, i.e., the state a transposition builds on
    var exactMatchesTwoCharsAgo = NO_OCCURRENCES
    var substitutionMatches = NO_OCCURRENCES
    // the only state seeded with active bit 0: the first pattern char can be deleted before any input is consumed
    var deletionMatches = NO_OCCURRENCES shl 1
    var insertionMatches = NO_OCCURRENCES
    var transpositionMatches = NO_OCCURRENCES
    var previousCharMask = NO_OCCURRENCES

    for (i in name.indices) {
      val mask = maskOf(name[i].lowercaseChar())

      val previousExactMatches = exactMatches
      // match at [j] survives if there was a previous match at [j - 1] and there's an active bit in the mask for [j]
      exactMatches = (previousExactMatches shl 1) or mask

      val exactMatchEndsHere = (exactMatches and fullMatchBit) == 0L
      if (exactMatchEndsHere && !consumer.consume(i + 1 - patternLen, i + 1, 0)) return false

      substitutionMatches = nextSubstitutionMatches(substitutionMatches, mask, previousExactMatches)
      deletionMatches = nextDeletionMatches(deletionMatches, mask, exactMatches)
      insertionMatches = nextInsertionMatches(insertionMatches, mask, previousExactMatches)
      transpositionMatches = nextTranspositionMatches(transpositionMatches, mask, previousCharMask,
                                                      exactMatchesTwoCharsAgo)

      /*
      An error route may spend its error during iteration where the exact match was found.
      Such a report is pointless, so it is skipped.
      */
      if (!exactMatchEndsHere) {
        val consumedChars = when {
          (substitutionMatches and fullMatchBit) == 0L -> patternLen
          (transpositionMatches and fullMatchBit) == 0L -> patternLen
          (deletionMatches and fullMatchBit) == 0L -> patternLen - 1
          (insertionMatches and fullMatchBit) == 0L -> patternLen + 1
          else -> NO_MATCH
        }

        if (consumedChars != NO_MATCH && !consumer.consume(i + 1 - consumedChars, i + 1, 1)) return false
      }

      exactMatchesTwoCharsAgo = previousExactMatches
      previousCharMask = mask
    }

    return true
  }

  /** The exact match on its own, to optimize when typos are not allowed */
  private fun processExactMatches(name: String, fullMatchBit: Long, patternLen: Int, consumer: MatchConsumer): Boolean {
    if (name.length < patternLen) return true

    var exactMatches = NO_OCCURRENCES

    for (i in name.indices) {
      exactMatches = (exactMatches shl 1) or maskOf(name[i].lowercaseChar())
      if ((exactMatches and fullMatchBit) == 0L && !consumer.consume(i + 1 - patternLen, i + 1, 0)) return false
    }

    return true
  }

  /**
   * ```
   * pattern:  g e t t e r
   * name:     g u t t e r
   *             ^ name[1] mismatch pattern[1]
   * ```
   *
   * `previousExactMatches shl 1` just skips the exact matching of the char at `name[i]`, so the current state can
   * advance even if `name[i]` and `pattern[j]` mismatch, as long as all of `pattern[0..j - 1]` already matched up to
   * `name[i - 1]`.
   *
   * | i | char | `mask` | `exactMatches` | `previousExactMatches shl 1` | `state before` | `(before shl 1) or mask` | `state after` |
   * |---|------|--------|----------------|------------------------------|----------------|--------------------------|---------------|
   * | 0 | g    | 111110 | 111110         | 111110                       | 111111         | 111110                   | 111110        |
   * | 1 | u    | 111111 | 111111         | 111100                       | 111110         | 111111                   | 111100        |
   * | 2 | t    | 110011 | 111111         | 111110                       | 111100         | 111011                   | 111010        |
   * | 3 | t    | 110011 | 111111         | 111110                       | 111010         | 110111                   | 110110        |
   * | 4 | e    | 101101 | 111111         | 111110                       | 110110         | 101101                   | 101100        |
   * | 5 | r    | 011111 | 111111         | 111110                       | 101100         | 011111                   | 011110        |
   */
  private fun nextSubstitutionMatches(currentState: Long, currentCharMask: Long, previousExactMatches: Long): Long {
    return ((currentState shl 1) or currentCharMask) and (previousExactMatches shl 1)
  }

  /**
   * ```
   * pattern:  g e t t e r
   * name:     g   t t e r
   *             ^ pattern[1] missing counterpart at name
   * ```
   *
   * `currentExactMatches shl 1` just skips `pattern[j]`, so the current state can advance with no char of the name
   * spent on it, as long as all of `pattern[0..j - 1]` already matched up to `name[i]`. Included means that
   * `name[i]` may be the very char that matched `pattern[j - 1]`. Then `exactMatches` turns bit `j - 1` active, and
   * this state turns bit `j` active, both inside one iteration. That is why it reads the exact match state updated in
   * the current iteration, and not the state before that.
   *
   * | i | char | `mask` | `currentExactMatches shl 1` | `state before` | `(before shl 1) or mask` | `state after` |
   * |---|------|--------|-----------------------------|----------------|--------------------------|---------------|
   * | 0 | g    | 111110 | 111100                      | 111110         | 111110                   | 111100        |
   * | 1 | t    | 110011 | 111110                      | 111100         | 111011                   | 111010        |
   * | 2 | t    | 110011 | 111110                      | 111010         | 110111                   | 110110        |
   * | 3 | e    | 101101 | 111110                      | 110110         | 101101                   | 101100        |
   * | 4 | r    | 011111 | 111110                      | 101100         | 011111                   | 011110        |
   */
  private fun nextDeletionMatches(currentState: Long, currentCharMask: Long, currentExactMatches: Long): Long {
    return ((currentState shl 1) or currentCharMask) and (currentExactMatches shl 1)
  }

  /**
   * ```
   * pattern:  g e t   t e r
   * name:     g e t X t e r
   *                 ^ name[3] missing counterpart at pattern
   * ```
   *
   * `previousExactMatches` just skips the char at `name[i]` as if exact match didn't happen, so the current state
   * can hold bit `j` as active while the name moves on, as long as all of `pattern[0..j]` already matched up to `name[i - 1]`.
   * There is no shift, because no char of the pattern is spent on `name[i]`.
   *
   * | i | char | `mask` | `exactMatches` | `previousExactMatches` | `state before` | `(before shl 1) or mask` | `state after` |
   * |---|------|--------|----------------|------------------------|----------------|--------------------------|---------------|
   * | 0 | g    | 111110 | 111110         | 111111                 | 111111         | 111110                   | 111110        |
   * | 1 | e    | 101101 | 111101         | 111110                 | 111110         | 111101                   | 111100        |
   * | 2 | t    | 110011 | 111011         | 111101                 | 111100         | 111011                   | 111001        |
   * | 3 | X    | 111111 | 111111         | 111011                 | 111001         | 111111                   | 111011        |
   * | 4 | t    | 110011 | 111111         | 111111                 | 111011         | 110111                   | 110111        |
   * | 5 | e    | 101101 | 111111         | 111111                 | 110111         | 101111                   | 101111        |
   * | 6 | r    | 011111 | 111111         | 111111                 | 101111         | 011111                   | 011111        |
   */
  private fun nextInsertionMatches(currentState: Long, currentCharMask: Long, previousExactMatches: Long): Long {
    return ((currentState shl 1) or currentCharMask) and previousExactMatches
  }

  /**
   * ```
   * pattern:  g e t t e r
   * name:     g t e t e r
   *             ^ ^ name[1] and name[2] traded places
   * ```
   *
   * For swap to complete, all 4 conditions should match:
   * - All of `pattern[0..j - 2]` already matched up to `name[i - 2]` (or swap is in the beginning of patrern).
   * - `name[i - 1]` matches `pattern[j]`.
   * - `name[i]` matches `pattern[j - 1]`.
   * - `j >= 1` as 2 chars are required for swap.
   *
   * | i | char | `exactMatchesTwoCharsAgo shl 2` | `currentCharMask shl 1` | `previousCharMask` | `swapCompletesHere` |
   * |---|------|---------------------------------|-------------------------|--------------------|---------------------|
   * | 0 | g    | 111100                          | 111100                  | 111111             | 111111              |
   * | 1 | t    | 111100                          | 100110                  | 111110             | 111111              |
   * | 2 | e    | 111000                          | 011010                  | 110011             | 111011              |
   * | 3 | t    | 111100                          | 100110                  | 101101             | 111111              |
   * | 4 | e    | 111100                          | 011010                  | 110011             | 111111              |
   * | 5 | r    | 111100                          | 111110                  | 101101             | 111111              |
   *
   * After `i = 2` swap at `j = 2` is recorded and survives in state:
   *
   * | i | char | `mask` | `exactMatches` | `swapCompletesHere` | `state before` | `(before shl 1) or mask` | `state after` |
   * |---|------|--------|----------------|---------------------|----------------|--------------------------|---------------|
   * | 0 | g    | 111110 | 111110         | 111111              | 111111         | 111110                   | 111110        |
   * | 1 | t    | 110011 | 111111         | 111111              | 111110         | 111111                   | 111111        |
   * | 2 | e    | 101101 | 111111         | 111011              | 111111         | 111111                   | 111011        |
   * | 3 | t    | 110011 | 111111         | 111111              | 111011         | 110111                   | 110111        |
   * | 4 | e    | 101101 | 111111         | 111111              | 110111         | 101111                   | 101111        |
   * | 5 | r    | 011111 | 111111         | 111111              | 101111         | 011111                   | 011111        |
   *
   * @param transpositionMatches the state as of the previous char.
   * @param currentCharMask occurrences of this char in the pattern.
   * @param previousCharMask occurrences of the char before it in the pattern.
   * @param exactMatchesTwoCharsAgo the exact match state from the char before the two swapped chars.
   */
  private fun nextTranspositionMatches(transpositionMatches: Long,
                                       currentCharMask: Long,
                                       previousCharMask: Long,
                                       exactMatchesTwoCharsAgo: Long): Long {
    /*
    pattern[0..j - 2] matched two chars ago, and the shift by two moves that bit up to j, because a swap covers
    pattern[j - 1] and pattern[j] at once. The shift also turns bits 0 and 1 active. Bit 1 has to be active, because
    a swap of pattern[0] and pattern[1] needs nothing matched before it.
    */
    val patternMatchedBeforeTheSwap = exactMatchesTwoCharsAgo shl 2
    /*
    A swap completes at bit j, and a mask carries a pattern char at the bit of its own index. The current char must
    equal pattern[j - 1], which the mask carries at bit j - 1, so the shift by one moves it up to j.
    */
    val currentCharIsTheEarlierPatternChar = currentCharMask shl 1

    val swapCompletesHere = patternMatchedBeforeTheSwap or
                            currentCharIsTheEarlierPatternChar or
                            // previousCharMask needs no shift, as it must equal pattern[j], which it already carries at bit j
                            previousCharMask or
                            // bit 0 is deactivated because a swap needs TWO pattern chars to complete, i.e., it's a guard
                            DEACTIVATE_FIRST_BIT


    return ((transpositionMatches shl 1) or currentCharMask) and swapCompletesHere
  }

  /** Receives the occurrences [BitapFuzzySearchAlgorithm.processMatches] finds. */
  fun interface MatchConsumer {
    /**
     * @return `false` to stop the scan.
     */
    fun consume(startOffset: Int, endOffsetExclusive: Int, errorCount: Int): Boolean
  }

  companion object {
    /** All bits inactive, i.e. the char does not occur in the pattern at all. */
    private const val NO_OCCURRENCES = -1L

    private const val ASCII_TABLE_SIZE = 128

    private const val DEACTIVATE_FIRST_BIT = 1L

    private const val NO_MATCH = -1

    /** A state holds one bit per pattern char, so a word cannot be longer than there are bits in a [Long]. */
    private const val MAX_PATTERN_WORD_LENGTH = Long.SIZE_BITS

    /** Whether [BitapFuzzySearchAlgorithm] is able to serve [pattern]. */
    fun isSupported(pattern: CharSequence): Boolean = pattern.length in 1..MAX_PATTERN_WORD_LENGTH

    /**
     * Factory method of [BitapFuzzySearchAlgorithm].
     *
     * @return an instance of [BitapFuzzySearchAlgorithm] for [pattern], or `null` when [pattern] cannot be served.
     * */
    fun tryCreate(pattern: String): BitapFuzzySearchAlgorithm? =
      if (isSupported(pattern)) BitapFuzzySearchAlgorithm(pattern) else null
  }
}
