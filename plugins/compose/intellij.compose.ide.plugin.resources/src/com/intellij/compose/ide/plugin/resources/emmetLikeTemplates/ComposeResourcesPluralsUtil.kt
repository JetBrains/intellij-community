// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.compose.ide.plugin.resources.emmetLikeTemplates


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
      val language = qualifier.substringBefore('-').substringBefore('_').lowercase()
      val categories = LANGUAGE_PLURAL_CATEGORIES[canonicalLanguage(language)]
      // unknown languages deliberately offer all categories (ICU root fallback would offer only "other")
      if (categories == null) ALL_PLURAL_CATEGORIES
      else ALL_PLURAL_CATEGORIES.filter { it in categories }
    }
  }

/** Resource qualifiers may use legacy language codes; the CLDR table lists them under the modern spellings. */
private fun canonicalLanguage(language: String): String =
  when (language) {
    "iw" -> "he"
    "in" -> "id"
    "ji" -> "yi"
    "jw" -> "jv"
    "tl" -> "fil"
    "mo" -> "ro"
    "sh" -> "sr"
    else -> language
  }

private val LANGUAGE_QUALIFIER_REGEX = Regex("[a-z]{2,3}")

/** Extracts the language qualifier from a values directory name, for example `values-en-rUS` gives `en`. */
internal fun extractLanguageQualifier(directoryName: String): String =
  directoryName.split('-').drop(1).firstOrNull { it.matches(LANGUAGE_QUALIFIER_REGEX) } ?: ""
