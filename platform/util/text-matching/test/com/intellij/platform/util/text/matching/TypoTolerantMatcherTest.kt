// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.util.text.matching

import com.intellij.psi.codeStyle.NameUtil
import com.intellij.psi.codeStyle.TypoTolerantMatcher
import org.junit.jupiter.api.Test
import kotlin.random.Random
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TypoTolerantMatcherTest {

  @Test
  fun testStability() {
    val data = randomStrings(Random(1)).take(20000)
    val matcher = TypoTolerantMatcher("asd", NameUtil.MatchingCaseSensitivity.FIRST_LETTER, "")
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

  @Test
  fun testEmptyPattern() {
    val matcher = TypoTolerantMatcher("*", NameUtil.MatchingCaseSensitivity.NONE, "")
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
}
