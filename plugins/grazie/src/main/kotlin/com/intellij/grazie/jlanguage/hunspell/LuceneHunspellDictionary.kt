package com.intellij.grazie.jlanguage.hunspell

import ai.grazie.spell.lists.hunspell.HunspellWordList
import com.intellij.openapi.progress.ProgressManager
import org.languagetool.rules.spelling.hunspell.HunspellDictionary
import java.io.InputStream

class LuceneHunspellDictionary(dictionary: InputStream, affix: InputStream) : HunspellDictionary {
  private val dict = HunspellWordList(affix, dictionary, { ProgressManager.checkCanceled() })

  override fun spell(word: String): Boolean = dict.contains(word, false)
  override fun add(word: String): Nothing = throw UnsupportedOperationException()
  override fun suggest(word: String): List<String> = dict.suggest(word).toList()

  @Volatile
  private var closed = false

  override fun isClosed(): Boolean {
    return closed
  }

  override fun close() {
    closed = true
  }
}
