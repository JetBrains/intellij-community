package com.intellij.grazie.jlanguage.hunspell

import ai.grazie.spell.lists.hunspell.HunspellWordList
import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.io.inputStream
import org.apache.lucene.analysis.hunspell.TimeoutPolicy
import org.languagetool.rules.spelling.hunspell.HunspellDictionary
import java.nio.file.Path

class LuceneHunspellDictionary(dictionary: Path, affix: Path) : HunspellDictionary {
  private val dict: HunspellWordList

  init {
    dictionary.inputStream().use { dic ->
      affix.inputStream().use { aff ->
        this.dict = HunspellWordList(aff, dic, TimeoutPolicy.NO_TIMEOUT) { ProgressManager.checkCanceled() }
      }
    }
  }

  override fun spell(word: String) = dict.contains(word, false)
  override fun add(word: String) = throw UnsupportedOperationException()
  override fun suggest(word: String) = dict.suggest(word).toList()

  override fun close() {
    // do nothing
  }
}
