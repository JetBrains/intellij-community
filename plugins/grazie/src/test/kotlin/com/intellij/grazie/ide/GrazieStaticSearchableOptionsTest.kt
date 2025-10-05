package com.intellij.grazie.ide

import com.intellij.grazie.ide.ui.grammar.tabs.rules.component.allRules
import com.intellij.grazie.ide.ui.search.GrazieStaticSearchableOptions
import com.intellij.grazie.jlanguage.Lang
import com.intellij.testFramework.junit5.TestApplication
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@TestApplication
class GrazieStaticSearchableOptionsTest {
  @Test
  fun `verify language proofreading options are correct`() {
    val options = GrazieStaticSearchableOptions.LanguageProofreading.collectStaticOptions()
    val expected = collectLanguageProofreadingOptions().sorted()
    Assertions.assertEquals(
      expected.joinToString(separator = "\n"),
      options.joinToString(separator = "\n")
    )
  }

  @Test
  fun `verify rule options are correct`() {
    val options = GrazieStaticSearchableOptions.Rules.collectStaticOptions()
    val expected = collectRuleOptions().sorted()
    Assertions.assertEquals(
      expected.joinToString(separator = "\n"),
      options.joinToString(separator = "\n")
    )
  }

  @Test
  fun `verify rule category options are correct`() {
    val options = GrazieStaticSearchableOptions.RuleCategories.collectStaticOptions()
    val expected = collectRuleCategoryOptions().sorted()
    Assertions.assertEquals(
      expected.joinToString(separator = "\n"),
      options.joinToString(separator = "\n")
    )
  }

  private fun GrazieStaticSearchableOptions.collectStaticOptions(): List<String> {
    return buildList {
      process(this::add)
    }
  }

  private fun collectLanguageProofreadingOptions(): List<String> {
    return Lang.entries.map { "${it.displayName} ${it.nativeName}".trim() }
  }

  private fun collectRuleOptions(): List<String> {
    val rules = allRules().values.flatten()
    return rules.map { it.presentableName.trim() }
  }

  private fun collectRuleCategoryOptions(): Set<String> {
    return buildSet {
      val rules = allRules().values.flatten()
      for (rule in rules) {
        for (cat in rule.categories) {
          add(cat.trim())
        }
      }
    }
  }
}
