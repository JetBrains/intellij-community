package com.intellij.grazie.jlanguage.hunspell

import ai.grazie.spell.lists.hunspell.HunspellWordList
import com.intellij.openapi.progress.ProgressManager
import org.languagetool.rules.spelling.hunspell.HunspellDictionary
import java.nio.file.Path
import kotlin.io.path.inputStream

class LuceneHunspellDictionary(dictionary: Path, affix: Path) : HunspellDictionary {
  private val dict: HunspellWordList = affix.inputStream().use { affix ->
    dictionary.inputStream().use { dictionary ->
      HunspellWordList(
        affix,
        dictionary,
        checkCanceled = { ProgressManager.checkCanceled() }
      )
    }
  }

  override fun spell(word: String) = dict.contains(word, false)
  override fun add(word: String) = throw UnsupportedOperationException()
  override fun suggest(word: String) = dict.suggest(word).toList()

  override fun close() {
    // do nothing
  }
}
