package com.intellij.grazie.config.migration

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ConfigMigrationTest : BasePlatformTestCase() {

  override fun setUp() {
    super.setUp()
    GrazieTestBase.maskSaxParserFactory(testRootDisposable)
  }

  fun `test global LT id migration`() {
    val before = GrazieConfig.State(
      enabledLanguages = setOf(Lang.AMERICAN_ENGLISH, Lang.GERMANY_GERMAN),
      userEnabledRules = setOf("PUNCTUATION_PARAGRAPH_END", "ENTSCHEIDEN_ENTSCHEIDEND_SPELLING_RULE", "InvalidRuleId"),
      userDisabledRules = setOf("COMMA_WHICH", "Some.Other")
    )
    val after = GrazieConfig.migrateLTRuleIds(before)
    assertSameElements(
      after.userEnabledRules,
      "LanguageTool.EN.PUNCTUATION_PARAGRAPH_END",
      "LanguageTool.DE.PUNCTUATION_PARAGRAPH_END", "LanguageTool.DE.ENTSCHEIDEN_ENTSCHEIDEND_SPELLING_RULE"
    )
    assertSameElements(after.userDisabledRules, "LanguageTool.EN.COMMA_WHICH", "Some.Other")
  }
}