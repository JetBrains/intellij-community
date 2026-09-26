package com.intellij.grazie.detection

import ai.grazie.detector.ChainLanguageDetector.ChainDetectionResult
import ai.grazie.detector.LanguageDetector.Type
import ai.grazie.nlp.langs.Language
import ai.grazie.nlp.tokenizer.word.StandardWordTokenizer.words
import com.intellij.grazie.config.DetectionContext
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.HighlightingUtil.getCheckedFileTexts
import com.intellij.grazie.utils.HighlightingUtil.grazieConfigTracker
import com.intellij.grazie.utils.LanguageDetectorHolder
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager

private typealias DetectionResults = Map<String, ChainDetectionResult>

object BatchLangDetector {
  private val CACHE = Key.create<CachedValue<ResultHolder>>("grazie reliable language detection cache")

  @JvmOverloads
  fun getLanguage(content: TextContent, offset: Int? = null): Language? {
    val text = LangDetector.getCleanText(content, offset ?: HighlightingUtil.stripPrefix(content)) ?: return null
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
      val languages = detectWithDetails(texts)
      CachedValueProvider.Result.create(ResultHolder(file, texts, languages), file, grazieConfigTracker())
    }.get()

  private fun getCleanTexts(file: PsiFile): List<String> =
    getCheckedFileTexts(file.viewProvider).mapNotNull { LangDetector.getCleanText(it) }

  private fun detectWithDetails(inputs: List<String>): List<ChainDetectionResult> {
    val chainResults = inputs.map {
      ProgressManager.checkCanceled()
      LangDetector.detectWithDetails(it)
    }
    val detectionResults = LanguageDetectorHolder.get().contextualize(inputs, chainResults.map { it.result })
    return chainResults.mapIndexed { index, chain ->
      val language = detectionResults[index]
      if (language.preferred == Language.UNKNOWN || language.type != Type.Neighbor) return@mapIndexed chain
      chain.withResult(language)
    }
  }

  private data class ResultHolder(val psiFile: PsiFile, val texts: List<String>, val languages: List<ChainDetectionResult>) {
    override fun toString(): String {
      return "[fileType = ${psiFile.viewProvider.virtualFile.fileType}, " +
             "fileLanguage = ${psiFile.language}, " +
             "viewProviderLanguages = ${psiFile.viewProvider.allFiles.map { it.language }.toSet()}, " +
             "detectedLanguages = ${languages.mapTo(HashSet()) { it.result.preferred }}," +
             "isPhysical = ${psiFile.isPhysical}, " +
             "contentLengths = ${texts.map { it.length }}]"
    }

    fun get(): DetectionResults = texts.zip(languages).toMap()
  }
}
