package com.intellij.grazie.pro

import ai.grazie.nlp.langs.locale
import com.intellij.grazie.mlec.MlecChecker
import com.intellij.testFramework.UsefulTestCase.assertNotEmpty
import org.junit.jupiter.api.Test

class MlecCheckerRulesSanityTest {
  @Test
  fun `test that can actually provide rules for each language`() {
    val checker = MlecChecker()
    val locales = MlecChecker.Constants.mlecRules.keys.map { it.locale }
    for (locale in locales) {
      val rules = checker.getRules(locale)
      assertNotEmpty(rules)
    }
  }
}
