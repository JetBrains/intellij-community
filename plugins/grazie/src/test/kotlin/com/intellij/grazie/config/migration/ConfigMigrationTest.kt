package com.intellij.grazie.config.migration

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.jlanguage.Lang
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class ConfigMigrationTest : BasePlatformTestCase() {
  fun `test global LT id migration`() {
    val before = GrazieConfig.State(
      enabledLanguages = setOf(Lang.AMERICAN_ENGLISH, Lang.GERMANY_GERMAN),
      userEnabledRules = setOf("PUNCTUATION_PARAGRAPH_END", "ENTSCHEIDEN_ENTSCHEIDEND", "InvalidRuleId"),
      userDisabledRules = setOf("COMMA_WHICH", "Some.Other")
    )
    val after = GrazieConfig.migrateLTRuleIds(before)
    assertSameElements(
      after.userEnabledRules,
      "LanguageTool.EN.PUNCTUATION_PARAGRAPH_END",
      "LanguageTool.DE.PUNCTUATION_PARAGRAPH_END", "LanguageTool.DE.ENTSCHEIDEN_ENTSCHEIDEND"
    )
    assertSameElements(after.userDisabledRules, "LanguageTool.EN.COMMA_WHICH", "Some.Other")
  }
}