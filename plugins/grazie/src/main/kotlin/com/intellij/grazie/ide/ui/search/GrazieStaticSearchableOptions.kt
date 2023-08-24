package com.intellij.grazie.ide.ui.search

import com.intellij.grazie.GraziePlugin
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
enum class GrazieStaticSearchableOptions(private val path: String) {
  LanguageProofreading("searchableOptions/languageProofreading.txt"),
  Rules("searchableOptions/rules.txt"),
  RuleCategories("searchableOptions/ruleCategories.txt");

  fun process(block: (String) -> Unit) {
    val stream = GraziePlugin.classLoader.getResourceAsStream(path)
    checkNotNull(stream) { "Failed to obtain resource for $this" }
    stream.reader().useLines { lines ->
      for (line in lines) {
        if (line.isNotBlank()) {
          block(line.trim())
        }
      }
    }
  }
}
