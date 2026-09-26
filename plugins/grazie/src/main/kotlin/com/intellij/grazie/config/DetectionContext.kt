package com.intellij.grazie.config

import ai.grazie.detector.ChainLanguageDetector
import ai.grazie.detector.LanguageDetector
import ai.grazie.detector.heuristics.list.ListDetector
import ai.grazie.detector.ngram.NgramDetector
import ai.grazie.nlp.langs.Language
import com.intellij.grazie.detection.hasWhitespaces
import com.intellij.util.xmlb.annotations.Property
import java.util.concurrent.ConcurrentHashMap

object DetectionContext {
  data class State(@Property val disabled: Set<Language> = HashSet()) {
    /** Disable from detection */
    fun disable(langs: Iterable<Language>) = State(disabled + langs)

    /** Enable for detection */
    fun enable(langs: Iterable<Language>) = State(disabled - langs)
  }

  data class Local(val counter: ConcurrentHashMap<Language, Int> = ConcurrentHashMap()) {
    companion object {
      //Each text gives SIZE / SCORE_SIZE + 1 scores to own language.
      //If LANGUAGE_SCORES / TOTAL_SCORES > NOTIFICATION_PROPORTION_THRESHOLD we suggest language
      const val SCORE_SIZE = 100

      const val NOTIFICATION_PROPORTION_THRESHOLD = 0.20
      const val NOTIFICATION_TOTAL_THRESHOLD = 3

      const val NGRAM_CONFIDENCE_THRESHOLD = 0.98

      //More than half of all seen words are clearly from one language
      const val LIST_CONFIDENCE_THRESHOLD = 0.51

      //Require not less than 40 chars
      const val TEXT_SIZE_THRESHOLD = 40

      //Require not less than 4 words for non-hieroglyphic languages
      const val WORDS_SIZE_THRESHOLD = 4
    }

    fun getToNotify(disabled: Set<Language>): Set<Language> {
      val total = counter.values.sum()

      val filtered = counter.filter { (_, myTotal) ->
        myTotal > NOTIFICATION_TOTAL_THRESHOLD && (myTotal.toDouble() / total) > NOTIFICATION_PROPORTION_THRESHOLD
      }.map { it.key }

      val langs = filtered.filter { it != Language.UNKNOWN && it !in disabled }

      return langs.toSet()
    }

    fun update(size: Int, wordsTotal: Int, details: ChainLanguageDetector.ChainDetectionResult) {
      val result = details.result
      val language = result.preferred

      //Check if not unknown
      if (language == Language.UNKNOWN) return

      //Check threshold by text size is not met
      if (size < TEXT_SIZE_THRESHOLD) return
      //Check threshold by number of words is not met (if language has words at all)
      if (language.hasWhitespaces && wordsTotal < WORDS_SIZE_THRESHOLD) return

      if (language in ListDetector.supported) {
        //Check if threshold by list detector is not met
        val listResult = details[LanguageDetector.Type.List]?.detected ?: return
        val maxList = listResult.maxByOrNull { it.probability } ?: return
        if (maxList.probability < LIST_CONFIDENCE_THRESHOLD || maxList.lang != result.preferred) return
      }

      if (language in NgramDetector.supported) {
        //Check if threshold by ngram detector is not met
        val ngramResult = details[LanguageDetector.Type.Ngram]?.detected
        //Check ngram only it was used. Otherwise, we believe to list detector
        if (ngramResult != null) {
          val maxNgram = ngramResult.maxByOrNull { it.probability } ?: return
          if (maxNgram.probability < NGRAM_CONFIDENCE_THRESHOLD || maxNgram.lang != result.preferred) return
        }
      }

      count(size, language)
    }

    private fun count(size: Int, lang: Language) {
      counter[lang] = counter.getOrDefault(lang, 0) + (size / SCORE_SIZE + 1)
    }

    fun clear() {
      counter.clear()
    }
  }
}
