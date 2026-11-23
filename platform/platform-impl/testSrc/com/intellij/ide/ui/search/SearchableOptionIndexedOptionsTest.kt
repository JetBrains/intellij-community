// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.ui.search

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/**
 * [MySearchableOptionProcessor.indexedOptions] keeps only the options of the searchable options files.
 */
internal class SearchableOptionIndexedOptionsTest {
  @Test
  fun `the indexed options have no options of the contributors`() {
    val processor = MySearchableOptionProcessor(stopWords = emptySet())

    processor.addOptions("sout", null, "sout live template", "editing.templates", null, false)
    processor.putIndexedOption(words = listOf("separators"), id = "terminal", groupName = "Terminal", hit = "Show separators", path = null)

    val indexed = packSearchableOptions(id = "terminal", hit = "Show separators", path = null, groupName = "Terminal",
                                        identifierTable = processor.identifierTable)
    assertEquals(setOf(indexed), processor.indexedOptions.toSet())
    assertTrue(processor.storage.keys.containsAll(listOf("sout", "separators")))
  }

  @Test
  fun `an indexed option without a word that is not a stop word is not kept`() {
    val processor = MySearchableOptionProcessor(stopWords = setOf("the"))

    processor.putIndexedOption(words = listOf("the"), id = "terminal", groupName = "Terminal", hit = "The", path = null)

    assertTrue(processor.indexedOptions.isEmpty())
    assertTrue(processor.storage.isEmpty())
  }
}
