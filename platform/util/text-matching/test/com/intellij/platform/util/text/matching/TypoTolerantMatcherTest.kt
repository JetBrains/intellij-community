// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.codeStyle.TypoTolerantMatcher
import com.intellij.util.text.matching.MatchingMode
import kotlin.test.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypoTolerantMatcherTest {
  @Test
  fun testStability() {
    val data = randomStrings(Random(1)).take(20000)
    val matcher = TypoTolerantMatcher("asd", MatchingMode.FIRST_LETTER, "")
    val matched = data.filter(matcher::matches).toList()
    val expected = listOf("oaebOeSnflvteoDsqhFo", "avsdCzdi", "axFskogmnmdufynMoewjDzsU", "JazjkxFvzhdogdvSBDwq",
                          "amEckovsuvscwqhgoSTdznk", "aAdmRJtQggyGcbzx", "asfZPktnVioCzeWx", "ajEhAd", "aaDuhgmkapirfpEufpNYbl",
                          "ammimq3zszBNwCmDc", "adZOzpdakGDmhqc", "aAdbjfpbbksFvkoxzJncV", "asxQnpskleYdaUnydjy", "lAsdckUaxay",
                          "aafjkqrMnkmwymDhl", "RaaFSqrfuDivxNksXibx", "aDPginwvboivkJjODzTuntQ", "jaazllSrlD", "zaEgqSZroDymriOjj",
                          "aefqJsdfjacxfak_eoVe", "adDTvpUZAuxpX", "aRAdblxighsznpspe", "adCpMjbbtUDlkvfyakCje", "aSfygmyodrokNuwAvugkmc",
                          "ashzDlibpOm", "aKSDdbtlphcbInJafkP", "ajmfuYsqPYFjD", "aFyjwqdwpWDygluewSmtD", "dazpjiSdf", "ademUttrQDu",
                          "agupnvSxdgnukGlpwxhyLxvp", "avuvzzgdvtSdqEgFqz", "asfKryfdl", "hasfiwopqfaoghaDcGokaoq", "ssbzBsmkDv",
                          "aqSSykiocfzWhajmtYHkshnm", "aiyADtwtlnkdYKqhdlj", "asfcqZgtHAqeOpcpFxp", "ssmw\$dsFzenxpyx1Ff")
    assertEquals(expected, matched, matched.joinToString(", ") { "\"$it\"" })
  }

  private fun randomStrings(random: Random): Sequence<String> = sequence {
    while (true) {
      val length = random.nextInt(5, 25)
      yield(buildString(capacity = length) {
        repeat(length) {
          val ch = random.nextInt('a'.code, 'z'.code + 1)
          val c = when {
            random.nextInt(100) == 0 -> "0123456789\$_@%вгдежзиклмно"[ch - 'a'.code].code
            random.nextInt(6) == 0 -> Character.toUpperCase(ch)
            else -> ch
          }
          appendCodePoint(c)
        }
      })
    }
  }

  @Test
  fun testEmptyPattern() {
    val matcher = TypoTolerantMatcher("*", MatchingMode.IGNORE_CASE, "")
    val data = listOf("foo", "bar", "buzz")
    val matched = data.filter(matcher::matches)
    val expected = listOf("foo", "bar", "buzz")
    assertEquals(expected, matched)
  }

  @Test
  fun testLongPattern() {
    val matcher = NameUtil.buildMatcher("MyLongTestClassName").typoTolerant().build()
    assertFalse(matcher is TypoTolerantMatcher)
    assertTrue(matcher.matches("MyLongTestClassName"))
  }

  @Test
  fun testCaseInsensitive() {
    val matcher = TypoTolerantMatcher("foo", MatchingMode.IGNORE_CASE, "")
    assertTrue(matcher.matches("foo"))
    assertTrue(matcher.matches("Foo"))
    assertTrue(matcher.matches("FOO"))
    assertTrue(matcher.matches("fOo"))
    assertTrue(matcher.matches("FooBar"))
    assertTrue(matcher.matches("foobar"))
  }

  @Test
  fun testCaseSensitive() {
    val matcher = TypoTolerantMatcher("Foo", MatchingMode.MATCH_CASE, "")
    assertTrue(matcher.matches("FooBar"))
    assertTrue(matcher.matches("Foobar"))
    assertFalse(matcher.matches("foo"))
    assertFalse(matcher.matches("FOO"))
    assertFalse(matcher.matches("fooBar"))
  }

  @Test
  fun testFirstLetterSensitive() {
    val lowerMatcher = TypoTolerantMatcher("foo", MatchingMode.FIRST_LETTER, "")
    assertTrue(lowerMatcher.matches("fooBar"))
    assertTrue(lowerMatcher.matches("foobar"))
    assertFalse(lowerMatcher.matches("FooBar"), "Should not match: first letter case mismatch")
    assertFalse(lowerMatcher.matches("Foobar"), "Should not match: first letter case mismatch")

    val upperMatcher = TypoTolerantMatcher("Foo", MatchingMode.FIRST_LETTER, "")
    assertTrue(upperMatcher.matches("FooBar"))
    assertTrue(upperMatcher.matches("Foobar"))
    assertFalse(upperMatcher.matches("fooBar"), "Should not match: first letter case mismatch")
    assertFalse(upperMatcher.matches("foobar"), "Should not match: first letter case mismatch")
  }

  @Test
  fun testFirstLetterSensitiveLeadingSpace() {
    val lowerMatcher = TypoTolerantMatcher(" foo", MatchingMode.FIRST_LETTER, "")
    assertTrue(lowerMatcher.matches("fooBar"), "Should match: both start with lowercase 'f'")
    assertTrue(lowerMatcher.matches("foo bar"), "Should match: both start with lowercase 'f'")
    assertFalse(lowerMatcher.matches("FooBar"), "Should NOT match: name starts with 'F', pattern starts with 'f'")
    assertFalse(lowerMatcher.matches("Foo bar"), "Should NOT match: name starts with 'F', pattern starts with 'f'")

    val upperMatcher = TypoTolerantMatcher(" Foo", MatchingMode.FIRST_LETTER, "")
    assertTrue(upperMatcher.matches("FooBar"), "Should match: both start with uppercase 'F'")
    assertTrue(upperMatcher.matches("Foo bar"), "Should match: both start with uppercase 'F'")
    assertFalse(upperMatcher.matches("fooBar"), "Should NOT match: name starts with 'f', pattern starts with 'F'")
    assertFalse(upperMatcher.matches("foo Bar"), "Should NOT match: name starts with 'f', pattern starts with 'F'")
  }

  @Test
  fun testFirstLetterSensitiveLeadingWildcard() {
    val lowerMatcher = TypoTolerantMatcher("*foo", MatchingMode.FIRST_LETTER, "")
    assertTrue(lowerMatcher.matches("fooBar"), "Should match: both start with lowercase 'f'")
    assertTrue(lowerMatcher.matches("foo"), "Should match: both start with lowercase 'f'")
    assertFalse(lowerMatcher.matches("FooBar"), "Should NOT match: name starts with 'F', pattern starts with 'f'")

    val upperMatcher = TypoTolerantMatcher("*Foo", MatchingMode.FIRST_LETTER, "")
    assertTrue(upperMatcher.matches("FooBar"), "Should match: both start with uppercase 'F'")
    assertTrue(upperMatcher.matches("Foo"), "Should match: both start with uppercase 'F'")
    assertFalse(upperMatcher.matches("fooBar"), "Should NOT match: name starts with 'f', pattern starts with 'F'")
  }

  @Test
  fun testCaseSensitivityWithTypos() {
    val insensitiveMatcher = TypoTolerantMatcher("fooBar", MatchingMode.IGNORE_CASE, "")
    assertTrue(insensitiveMatcher.matches("FooBar"))
    assertTrue(insensitiveMatcher.matches("fooBra"))

    val sensitiveMatcher = TypoTolerantMatcher("FooBar", MatchingMode.MATCH_CASE, "")
    assertTrue(sensitiveMatcher.matches("FooBar"))
    assertFalse(sensitiveMatcher.matches("fooBar"), "Case mismatch should prevent match")

    val firstLetterMatcher = TypoTolerantMatcher("FooBar", MatchingMode.FIRST_LETTER, "")
    assertTrue(firstLetterMatcher.matches("FooBar"))
    assertTrue(firstLetterMatcher.matches("Foobar"))
    assertFalse(firstLetterMatcher.matches("fooBar"), "First letter case mismatch should prevent match")
  }

  @Test
  fun camelHumps() {
    assertFalse(TypoTolerantMatcher("*NNINa", MatchingMode.IGNORE_CASE, "").matches("PlanningFailureException"))
    assertTrue(TypoTolerantMatcher("*Git", MatchingMode.IGNORE_CASE, "").matches("BlagitBla"))
    assertTrue(TypoTolerantMatcher("Wlo", MatchingMode.IGNORE_CASE, "").matches("WebLogic"))
    assertTrue(TypoTolerantMatcher("*Git", MatchingMode.FIRST_LETTER, "").matches("BlagitBla"))
    assertTrue(TypoTolerantMatcher("*Bcomp", MatchingMode.FIRST_LETTER, "").matches("BaseComponent"))
    assertTrue(TypoTolerantMatcher("Cl", MatchingMode.FIRST_LETTER, "").matches("CoreLoader"))
  }
}
