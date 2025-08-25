package com.intellij.grazie.cloud

import ai.grazie.ner.model.SentenceWithNERAnnotations
import ai.grazie.nlp.langs.Language
import ai.grazie.rules.de.GermanTreeSupport
import ai.grazie.rules.en.EnglishTreeSupport
import ai.grazie.rules.ru.RussianTreeSupport
import ai.grazie.rules.tree.Tree
import ai.grazie.rules.tree.TreeSupport
import ai.grazie.rules.uk.UkrainianTreeSupport
import ai.grazie.text.exclusions.SentenceWithExclusions
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.GrazieConfig
import com.intellij.grazie.rule.CloudOrLocalBatchParser
import com.intellij.grazie.rule.SentenceBatcher
import com.intellij.grazie.rule.SentenceBatcher.AsyncBatchParser
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.HunspellUtil
import com.intellij.grazie.utils.getLanguageIfAvailable
import com.intellij.openapi.Disposable
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.util.application
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.languagetool.language.English

object DependencyParser {
  private val LOG = Logger.getInstance(DependencyParser::class.java)
  private val cachedTrees = ContainerUtil.createSoftKeySoftValueMap<String, Tree>()

  @JvmStatic
  fun getParser(text: TextContent, minimal: Boolean): AsyncBatchParser<Tree>? {
    val stripPrefixLength = HighlightingUtil.stripPrefix(text)
    val language = getLanguageIfAvailable(text.toString().substring(stripPrefixLength)) ?: return null

    if (!GrazieCloudConnector.seemsCloudConnected()) {
      return getLocalParser(language)
    }
    val batcher = getBatcher(language) ?: return null
    val file = text.containingFile
    val cloud = when {
      minimal -> batcher.minimal(file.project)
      else -> batcher.forFile(file.viewProvider)
    }
    return CloudOrLocalBatchParser(
      project = file.project,
      cloud = cloud,
      local = { getLocalParser(language) }
    )
  }

  private fun getLocalParser(language: Language): AsyncBatchParser<Tree> {
    return object : AsyncBatchParser<Tree> {
      override suspend fun parseAsync(sentences: List<SentenceWithExclusions>): LinkedHashMap<SentenceWithExclusions, Tree?> {
        val support = obtainSupport(language)
        if (support != null) {
          @Suppress("UNCHECKED_CAST")
          return sentences.associateWith {
            cachedTrees.getOrPut(it.sentence) {
              Tree.createFlatTree(support, it.sentence)
            }
          } as LinkedHashMap<SentenceWithExclusions, Tree?>
        }

        return LinkedHashMap()
      }
    }
  }

  private fun getBatcher(language: Language): Batcher? {
    return application.service<BatcherHolder>().batchers[language]
  }

  private val lang2SupportClass = mapOf(
    Language.ENGLISH to "ai.grazie.rules.en.EnglishTreeSupport",
    Language.GERMAN to "ai.grazie.rules.de.GermanTreeSupport",
    Language.UKRAINIAN to "ai.grazie.rules.uk.UkrainianTreeSupport",
    Language.RUSSIAN to "ai.grazie.rules.ru.RussianTreeSupport"
  )
  private val supports: MutableMap<Language, TreeSupport> = ContainerUtil.createConcurrentSoftValueMap()

  @JvmStatic
  fun obtainSupport(language: Language): TreeSupport? {
    if (language !in lang2SupportClass) {
      return null
    }
    val ltLanguage = SentenceBatcher.findInstalledLTLanguage(language) ?: return null
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

  @Service
  private class BatcherHolder: Disposable {
    val batchers = mapOf(
      Language.ENGLISH to Batcher(Language.ENGLISH),
      Language.GERMAN to Batcher(Language.GERMAN),
      Language.UKRAINIAN to Batcher(Language.UKRAINIAN),
      Language.RUSSIAN to Batcher(Language.RUSSIAN)
    )

    init {
      GrazieConfig.subscribe(this) { clearCaches() }
      GrazieCloudConnector.EP_NAME.forEachExtensionSafe { it.subscribeToAuthorizationStateEvents(this) { clearCaches() } }
    }

    private fun clearCaches() {
      supports.clear()
      batchers.values.forEach { it.clearCache() }
    }

    override fun dispose() {
      batchers.values.forEach(Disposer::dispose)
    }
  }

  private class Batcher(language: Language): SentenceBatcher<Tree>(language, TreeSupport.CLOUD_BATCH_SIZE, quoteMarkup = true) {
    override suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, Tree> {
      if (GrazieCloudConnector.EP_NAME.extensionList.any { it.isAfterRecentGecError() }) {
        return emptyMap()
      }
      val support = obtainSupport(language) ?: return emptyMap()
      val sentenceStrings = sentences.map { it.sentence }

      return coroutineScope {
        val start = System.currentTimeMillis()
        val asyncLabels: Deferred<List<SentenceWithNERAnnotations>?>? =
          if (support.needsNer()) async {
            GrazieCloudConnector.EP_NAME.extensionList.firstNotNullOfOrNull { it.nerAnnotations(language, sentenceStrings, project) }
          } else null

        val trees = GrazieCloudConnector.EP_NAME.extensionList
          .firstNotNullOfOrNull { it.trees(language, support.cloudTreeModelName, support.cloudParserOptions, sentenceStrings, project) }
        val labels = asyncLabels?.await()?.associateBy { it.text } ?: emptyMap()

        LOG.debug("Parsing servers responded in ${System.currentTimeMillis() - start}ms for ${sentenceStrings.size} sentences")

        if (trees == null) emptyMap()
        else sentences.zip(trees).associate { it.first to support.buildTree(it.second, labels[it.first.sentence]) }
      }
    }

    @Suppress("UnstableApiUsage")
    override fun reportStatus(reporter: RawProgressReporter) {
      super.reportStatus(reporter)
      reporter.text(GrazieBundle.message("progress.text.parsing.natural.language.text"))
    }
  }
}
