package com.intellij.grazie.config.migration

import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.GrazieConfig.Companion.ltOxfordRules
import com.intellij.grazie.GrazieTestBase
import com.intellij.grazie.jlanguage.Lang
import com.intellij.grazie.utils.TextStyleDomain
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

  fun `test disabled oxford rules after migration`() {
    val before = TextStyleDomain.entries.fold(GrazieConfig.State()) { acc, domain ->
      if (domain == TextStyleDomain.Other) {
        acc.copy(userDisabledRules = ltOxfordRules)
      } else {
        acc.withDomainDisabledRules(domain, ltOxfordRules)
      }
    }
    val after = GrazieConfig.migrateOxfordRuleIds(before)

    TextStyleDomain.entries.forEach { domain ->
      if (domain == TextStyleDomain.Other) {
        assertEmpty(after.userDisabledRules)
        assertEmpty(after.userEnabledRules)
      } else {
        assertEmpty(after.domainDisabledRules[domain]!!)
        assertNullOrEmpty(after.domainEnabledRules[domain])
      }
    }
  }

  fun `test non-disabled oxford rules are disabled after migration`() {
    val before = GrazieConfig.State()
    val after = GrazieConfig.migrateOxfordRuleIds(before)

    TextStyleDomain.entries.forEach { domain ->
      if (domain == TextStyleDomain.Other) {
        assertEmpty(after.userDisabledRules)
        assertEmpty(after.userEnabledRules)
      } else {
        assertEmpty(after.domainDisabledRules[domain]!!)
        assertNullOrEmpty(after.domainEnabledRules[domain])
      }
    }
  }

  fun `test non-disabled oxford rules are migrated if useOxfordSpelling is true`() {
    val before = TextStyleDomain.entries.fold(GrazieConfig.State(useOxfordSpelling = true)) { acc, domain ->
      if (domain == TextStyleDomain.Other) {
        acc.copy(userDisabledRules = setOf("LanguageTool.EN.OXFORD_SPELLING_Z_NOT_S"))
      } else {
        acc.withDomainDisabledRules(domain, setOf("LanguageTool.EN.OXFORD_SPELLING_Z_NOT_S"))
      }
    }
    val after = GrazieConfig.migrateOxfordRuleIds(before)

    TextStyleDomain.entries.forEach { domain ->
      if (domain == TextStyleDomain.Other) {
        assertEmpty(after.userDisabledRules)
        assertContainsElements(after.userEnabledRules, setOf("LanguageTool.EN.OXFORD_SPELLING_GRAM"))
      } else {
        assertEmpty(after.domainDisabledRules[domain]!!)
        assertContainsElements(after.domainEnabledRules[domain]!!, setOf("LanguageTool.EN.OXFORD_SPELLING_GRAM"))
      }
    }
  }
}