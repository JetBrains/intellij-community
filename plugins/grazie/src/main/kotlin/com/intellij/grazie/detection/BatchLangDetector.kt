package com.intellij.grazie.detection

import ai.grazie.detector.ChainLanguageDetector
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.tokenizer.word.StandardWordTokenizer.words
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.HighlightingUtil.getCheckedFileTexts
import com.intellij.grazie.utils.HighlightingUtil.grazieConfigTracker
import com.intellij.grazie.utils.LanguageDetectorHolder
import com.intellij.grazie.utils.NaturalTextDetector
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

private typealias DetectionResults = Map<String, ChainLanguageDetector.ChainDetectionResult>

object BatchLangDetector {
  private val CACHE = Key.create<CachedValue<DetectionResults>>("grazie reliable language detection cache")

  fun getLanguage(content: TextContent, offset: Int): Language? {
    val text = content.substring(offset).take(LanguageDetectorHolder.LIMIT)
    if (!NaturalTextDetector.seemsNatural(text)) return null
    val language = detectForFile(content.containingFile)[text]?.result?.preferred
    return if (language == Language.UNKNOWN) null else language
  }

  fun updateContext(file: PsiFile, context: DetectionContext.Local) {
    detectForFile(file).forEach { (text, details) ->
      val wordsCount = text.words().count()
      context.update(text.length, wordsCount, details)
    }
  }

  private fun detectForFile(file: PsiFile): DetectionResults =
    CachedValuesManager.getCachedValue(file, CACHE) {
      val texts = getCleanTexts(file)
      val languages = LanguageDetectorHolder.get().detectWithDetails(texts,true) { ProgressManager.checkCanceled() }
      CachedValueProvider.Result.create(texts.zip(languages).toMap(), file, grazieConfigTracker())
    }

  private fun getCleanTexts(file: PsiFile): List<String> =
    getCheckedFileTexts(file.viewProvider)
      .map { it.substring(HighlightingUtil.stripPrefix(it)) }
      .map { it.take(LanguageDetectorHolder.LIMIT) }
      .filter { NaturalTextDetector.seemsNatural(it) }
}
