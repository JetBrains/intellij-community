// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates

import com.ibm.icu.text.PluralRules
import com.ibm.icu.util.ULocale


private val ALL_PLURAL_CATEGORIES = listOf("zero", "one", "two", "few", "many", "other")

/**
 * Returns a list of plural quantity tags (one, few, other, etc.) based on the file's language qualifier.
 *
 * @param tag The shortcut tag used ("a" for all quantities, or anything else for current locale-based quantities).
 * @param qualifier Language qualifier (e.g., "en" for English).
 * The mapping follows the Unicode CLDR (Common Locale Data Repository) plural rules.
 *
 * @see <a href="https://www.unicode.org/cldr/charts/43/supplemental/language_plural_rules.html#rules">CLDR Plural Rules</a>
 */
internal fun getComposeResourcesPluralQuantityTags(tag: String, qualifier: String): List<String> =
  when {
    tag == "a" || tag == "all" -> ALL_PLURAL_CATEGORIES
    qualifier.isEmpty() -> ALL_PLURAL_CATEGORIES
    else -> {
      val localeKeywords = PluralRules.forLocale(ULocale(qualifier.ifEmpty { "en" })).keywords
      ALL_PLURAL_CATEGORIES.filter { it in localeKeywords }
    }
  }
