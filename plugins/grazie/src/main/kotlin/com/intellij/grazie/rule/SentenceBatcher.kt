package com.intellij.grazie.rule

import ai.grazie.nlp.langs.Language
import ai.grazie.rules.util.BatchParser
import ai.grazie.text.exclusions.SentenceWithExclusions
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.openapi.Disposable
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.Project

abstract class SentenceBatcher<T>(val language: Language) : Disposable {
  protected abstract suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, T>?

  interface AsyncBatchParser<T> : BatchParser<T> {
    override fun parse(sentences: List<String>): LinkedHashMap<String, T?> =
      runBlockingCancellable {
        parseAsync(sentences.map { SentenceWithExclusions(it) })
      }
        .mapKeysTo(LinkedHashMap()) { it.key.sentence }

    suspend fun parseAsync(sentences: List<SentenceWithExclusions>): LinkedHashMap<SentenceWithExclusions, T?>
  }

  companion object {
    @JvmStatic
    fun findInstalledLTLanguage(language: Language): org.languagetool.Language? {
      return HighlightingUtil.findInstalledLang(language)?.jLanguage
    }
  }
}
