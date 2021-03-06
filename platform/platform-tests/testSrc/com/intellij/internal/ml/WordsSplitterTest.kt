// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.ml

import org.junit.Test
import kotlin.test.assertEquals

class WordsSplitterTest {

  @Test
  fun `split identifier on words in different cases`() {
    val wordsSplitter = WordsSplitter.Builder().toLowerCase().build()
    val names2words = mapOf(
      "camelCaseIdentifier" to listOf("camel", "case", "identifier"),
      "PascalCaseIdentifier" to listOf("pascal", "case", "identifier"),
      "snake_case_identifier" to listOf("snake", "case", "identifier"),
      "Some_differentCases_Identifier" to listOf("some", "different", "cases", "identifier"),
      "CAPS_IDENTIFIER" to listOf("caps", "identifier"),
      "identifier123withNumbers__And#@special?symbols" to listOf("identifier", "with", "numbers", "and", "special", "symbols"),
      "MLCompletion" to listOf("ml", "completion"),
      "GetHTMLReport" to listOf("get", "html", "report"),
      "test" to listOf("test"),
      "test5" to listOf("test"),
      "5test!" to listOf("test"),
      "555test555" to listOf("test"),
      "_test_" to listOf("test"),
      "123_?!" to listOf()
    )
    checkResults(wordsSplitter, names2words)
  }

  @Test
  fun `skip stop words`() {
    val wordsSplitter = WordsSplitter.Builder().ignoreStopWords().toLowerCase().build()
    val names2words = mapOf(
      "getSomeInformation" to listOf("some", "information"),
      "get_some_information" to listOf("some", "information"),
      "setValue" to listOf("value"),
      "is_correct" to listOf("correct")
    )
    checkResults(wordsSplitter, names2words)
  }

  @Test
  fun `don't process too many words`() {
    val wordsSplitter = WordsSplitter.Builder().maxWords(5).toLowerCase().build()
    val names2words = mapOf(
      "someVeryLongIdentifierWithManyWords" to listOf("some", "very", "long", "identifier", "with"),
      "shortIdentifier" to listOf("short", "identifier")
    )
    checkResults(wordsSplitter, names2words)
  }

  @Test
  fun `skip short words`() {
    val wordsSplitter = WordsSplitter.Builder().skipShort(3).toLowerCase().build()
    val names2words = mapOf(
      "myIdentifier" to listOf("identifier"),
      "it_is_identifier" to listOf("identifier")
    )
    checkResults(wordsSplitter, names2words)
  }

  @Test
  fun `use stemming`() {
    val wordsSplitter = WordsSplitter.Builder().withStemming().toLowerCase().build()
    val names2words = mapOf(
      "readingNames" to listOf("read", "name"),
      "lists_of_words" to listOf("list", "of", "word")
    )
    checkResults(wordsSplitter, names2words)
  }

  private fun checkResults(wordsSplitter: WordsSplitter, names2words: Map<String, List<String>>) {
    for ((name, words) in names2words.entries) {
      val result = wordsSplitter.split(name)
      assertEquals(words, result)
    }
  }
}