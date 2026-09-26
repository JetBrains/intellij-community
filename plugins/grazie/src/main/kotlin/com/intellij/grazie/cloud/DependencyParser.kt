package com.intellij.grazie.cloud

import ai.grazie.nlp.langs.Language
import ai.grazie.rules.de.GermanTreeSupport
import ai.grazie.rules.en.EnglishTreeSupport
import ai.grazie.rules.ru.RussianTreeSupport
import ai.grazie.rules.tree.Tree
import ai.grazie.rules.tree.TreeSupport
import ai.grazie.rules.uk.UkrainianTreeSupport
import ai.grazie.text.exclusions.SentenceWithExclusions
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.grazie.ide.ui.configurable.StyleConfigurable.Companion.ruleEngineLanguages
import com.intellij.grazie.jlanguage.CACHE_SIZE
import com.intellij.grazie.jlanguage.LazyCachingConcurrentDisambiguator
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.HunspellUtil
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.util.runWithCheckCanceled
import com.intellij.util.containers.ContainerUtil
import org.languagetool.language.English
import java.util.SequencedMap

object DependencyParser {
  private val cachedTrees = Caffeine.newBuilder()
    .softValues()
    .maximumSize(CACHE_SIZE)
    .build<SentenceWithLanguage, Tree>()

  fun parse(language: Language, sentences: List<SentenceWithExclusions>): SequencedMap<SentenceWithExclusions, Tree?> {
    val support = obtainSupport(language) ?: return LinkedHashMap()
    val ltLanguage = HighlightingUtil.findInstalledLang(language)?.jLanguage
    (ltLanguage?.disambiguator as? LazyCachingConcurrentDisambiguator)?.ensureInitialized()
    return sentences.associateWithTo(LinkedHashMap()) { swe ->
      cachedTrees.get(SentenceWithLanguage(swe.sentence, language)) { swl ->
        runWithCheckCanceled {
          Tree.createFlatTree(support, swl.sentence) { ProgressManager.checkCanceled() }
        }
      }
    }
  }

  private val supports = ContainerUtil.createConcurrentSoftValueMap<Language, TreeSupport>()

  @JvmStatic
  fun obtainSupport(language: Language): TreeSupport? {
    if (language !in ruleEngineLanguages) {
      return null
    }
    val ltLanguage = HighlightingUtil.findInstalledLang(language)?.jLanguage ?: return null
    return supports.computeIfAbsent(language) {
      when (language) {
        Language.ENGLISH -> EnglishTreeSupport(ltLanguage as English) { HunspellUtil.obtainEnglish() }
        Language.GERMAN -> GermanTreeSupport(ltLanguage) { HunspellUtil.obtainDictionary(it) }
        Language.UKRAINIAN -> UkrainianTreeSupport(ltLanguage) { HunspellUtil.obtainDictionary(it) }
        Language.RUSSIAN -> RussianTreeSupport(ltLanguage) { HunspellUtil.obtainDictionary(it) }
        else -> throw UnsupportedOperationException()
      }
    }
  }

}

private data class SentenceWithLanguage(val sentence: String, val language: Language)
