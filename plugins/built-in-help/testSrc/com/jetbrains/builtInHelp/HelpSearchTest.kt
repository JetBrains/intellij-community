// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.builtInHelp

import com.jetbrains.builtInHelp.indexer.HelpIndexer
import com.jetbrains.builtInHelp.search.HelpSearch
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path

class HelpSearchTest {
  @Test
  fun `generated index supplies English fallback search`(@TempDir temp: Path) {
    val topics = Files.createDirectory(temp.resolve("topics"))
    Files.write(topics.resolve("editor.html"), Utils.getResourceWithFallback("topics", "editor.html", "")!!)
    val index = temp.resolve("search")
    HelpIndexer.main(arrayOf(index.toString(), topics.toString()))
    val requestedLocales = mutableListOf<String>()
    val results = HelpSearch.search("shortcuts", 10, "zh-cn") { _, name, locale ->
      requestedLocales.add(locale)
      if (locale == "zh-cn") null else Files.readAllBytes(index.resolve(name))
    }
    assertTrue(results.contains("editor.html"))
    assertTrue(results.contains("Editor settings"))
    assertEquals("zh-cn", requestedLocales.first())
    assertTrue(requestedLocales.drop(1).all { it.isEmpty() })
    assertFalse(Files.readAllLines(index.resolve("rlist")).contains("rlist"))
  }

  @Test
  fun `empty queries and invalid limits do not load an index`() {
    for ((query, limit) in listOf("" to 10, "topic" to 0, "topic" to 1001)) {
      assertEquals("[]", HelpSearch.search(query, limit, "") { _, _, _ -> error("No index should be loaded") })
    }
  }
}
