// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.grazie.spellcheck.dictionary

import ai.grazie.nlp.langs.alphabet.Alphabet
import ai.grazie.nlp.similarity.Levenshtein
import ai.grazie.spell.lists.WordList
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.detection.toLanguage
import com.intellij.grazie.spellcheck.GrazieCheckers
import com.intellij.openapi.components.service
import com.intellij.spellchecker.dictionary.Dictionary
import com.intellij.spellchecker.dictionary.Dictionary.LookupStatus.Present

internal class WordListAdapter : WordList, EditableWordListAdapter() {
  fun isAlien(word: String): Boolean {
    if (Alphabet.ENGLISH.matchAny(word)) {
      // Asian English mixed text should never be highlighted. Each of the tokens must be checked separately
      return Alphabet.Group.ASIAN.matchAny(word)
    }
    val matchedLanguages = GrazieConfig.get().availableLanguages
      .filter { it.toLanguage().alphabet.matchEntire(word) }
    return (matchedLanguages.isEmpty() || !service<GrazieCheckers>().hasSpellerTool(matchedLanguages)) && !aggregator.contains(word)
  }

  override fun contains(word: String, caseSensitive: Boolean): Boolean =
    contains(word, caseSensitive, GrazieConfig.get().dictionaries) || aggregator.contains(word, caseSensitive)

  private fun contains(word: String, caseSensitive: Boolean, hunspell: List<Dictionary>): Boolean =
    if (caseSensitive) {
      dictionaries.values.any { it.lookup(word) == Present } || hunspell.any { it.lookup(word) == Present }
    } else {
      val lowered = word.lowercase()
      // NOTE: dictionary may not contain a lowercase form, but may contain any form in a different case
      // current dictionary interface does not support caseSensitive
      val isPresent: (Dictionary) -> Boolean = { it.lookup(lowered) == Present || it.lookup(word) == Present }
      dictionaries.values.any(isPresent) || hunspell.any(isPresent)
    }

  override fun suggest(word: String): LinkedHashSet<String> {
    val result = LinkedHashSet<String>()
    for (dictionary in dictionaries.values) {
      dictionary.consumeSuggestions(word) {
        if (it.isEmpty()) {
          return@consumeSuggestions
        }
        val distance = Levenshtein.distance(word, it, SimpleWordList.MAX_LEVENSHTEIN_DISTANCE + 1)
        if (distance <= SimpleWordList.MAX_LEVENSHTEIN_DISTANCE) {
          result.add(it)
        }
      }
    }

    result.addAll(aggregator.suggest(word))
    result.remove("")
    return result
  }
}
