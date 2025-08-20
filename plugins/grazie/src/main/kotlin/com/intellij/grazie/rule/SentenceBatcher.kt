package com.intellij.grazie.rule

import ai.grazie.nlp.langs.Language
import ai.grazie.rules.util.BatchParser
import ai.grazie.text.exclusions.SentenceWithExclusions
import com.intellij.grazie.text.TextContent
import com.intellij.grazie.text.TextExtractor.findAllTextContents
import com.intellij.grazie.utils.HighlightingUtil
import com.intellij.grazie.utils.NaturalTextDetector
import com.intellij.grazie.utils.getLanguageIfAvailable
import com.intellij.grazie.utils.runBlockingCancellable
import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.platform.util.progress.RawProgressReporter
import com.intellij.platform.util.progress.reportRawProgress
import com.intellij.psi.FileViewProvider
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import com.intellij.util.containers.ContainerUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class SentenceBatcher<T>(val language: Language, private val batchSize: Int, private val quoteMarkup: Boolean = false) : Disposable {
  @Volatile
  private var globalCache: MutableMap<SentenceWithExclusions, T?> = ContainerUtil.createConcurrentSoftValueMap()
  private val key: Key<CachedValue<AsyncBatchParser<T>>> = Key.create(javaClass.name + " " + language)
  private val executorScope = CoroutineScope(SupervisorJob() + CoroutineName(name = "SentenceBatcherScope-${this::class.java.name}"))
  private val taskMutex = Mutex()

  protected abstract suspend fun parse(sentences: List<SentenceWithExclusions>, project: Project): Map<SentenceWithExclusions, T>

  override fun dispose() {
    executorScope.cancel()
  }

  fun clearCache() {
    globalCache = ContainerUtil.createConcurrentSoftValueMap()
  }

  fun minimal(project: Project): AsyncBatchParser<T> = forSentences(project, emptyList())

  fun forFile(vp: FileViewProvider): AsyncBatchParser<T> {
    val cvManager = CachedValuesManager.getManager(vp.manager.project)
    return cvManager.getCachedValue(vp, key, {
      val textContents: List<TextContent> =
        findAllTextContents(vp, HighlightingUtil.checkedDomains())
          .filterNot { HighlightingUtil.isTooLargeText(listOf(it)) }
          .filter { hasOurLanguage(it) }
      val parser = forSentences(vp.manager.project, allSentences(textContents))
      CachedValueProvider.Result.create(parser, PsiModificationTracker.MODIFICATION_COUNT, HighlightingUtil.grazieConfigTracker())
    }, false)
  }

  private fun allSentences(textContents: Collection<TextContent>): List<SentenceWithExclusions> {
    val language = findInstalledLTLanguage(language)
    val allSentences = if (language == null) emptyList() else textContents.asSequence()
      .sortedBy { it.textOffsetToFile(0) }
      .flatMap { text ->
        ProgressManager.checkCanceled()
        SentenceTokenizer.tokenize(text).flatMap {
          listOf(it.swe()) + (if (quoteMarkup) listOfNotNull(it.stubbedSwe()) else emptyList())
        }
      }
      .filterNot { isNonSentence(it.sentence) }
      .distinct()
      .toList()
    return allSentences
  }

  private fun forSentences(project: Project, allSentences: List<SentenceWithExclusions>): AsyncBatchParser<T> {
    return object : AsyncBatchParser<T> {
      override suspend fun parseAsync(sentences: List<SentenceWithExclusions>): LinkedHashMap<SentenceWithExclusions, T?> {
        val map = HashMap<SentenceWithExclusions, T?>()
        val toQuery = LinkedHashSet<SentenceWithExclusions>()
        for (sentence in sentences) {
          checkCache(sentence, map, toQuery)
        }
        if (toQuery.isNotEmpty()) {
          @Suppress("UnstableApiUsage")
          reportRawProgress { reporter ->
            reportStatus(reporter)
            while (toQuery.isNotEmpty()) {
              val start = System.currentTimeMillis()
              val deferred: Deferred<Map<SentenceWithExclusions, T?>> = executorScope.async(context = Dispatchers.Default) {
                taskMutex.withLock {
                  LOG.debug("Waiting took " + (System.currentTimeMillis() - start) + "ms in " + this@SentenceBatcher)
                  val batch = nextBatch(map, toQuery)
                  return@withLock when {
                    batch.isEmpty() -> emptyMap()
                    else -> toLinkedMap(batch, parseAndCache(batch))
                  }
                }
              }
              val parsed: Map<SentenceWithExclusions, T?> = deferred.await()
              map.putAll(parsed)
              toQuery.removeAll(map.keys)
            }
          }
        }
        return sentences.associateWith { sentence -> map[sentence] } as LinkedHashMap<SentenceWithExclusions, T?>
      }

      private fun toLinkedMap(sentences: Iterable<SentenceWithExclusions>, map: Map<SentenceWithExclusions, T?>): LinkedHashMap<SentenceWithExclusions, T?> {
        val result = LinkedHashMap<SentenceWithExclusions, T?>()
        for (sentence in sentences) {
          result[sentence] = map[sentence]
        }
        return result
      }

      private fun nextBatch(resultMap: MutableMap<SentenceWithExclusions, T?>, pool: Set<SentenceWithExclusions>): LinkedHashSet<SentenceWithExclusions> {
        val batch = LinkedHashSet<SentenceWithExclusions>()
        populateBatch(resultMap, pool, batch)
        if (batch.isEmpty() || batch.size == batchSize) return batch
        val anchor = allSentences.indexOfFirst { batch.contains(it) }
        if (anchor >= 0) {
          if (anchor < allSentences.size - 1) {
            populateBatch(resultMap, allSentences.subList(anchor + 1, allSentences.size), batch)
          }
          if (batch.size < batchSize && anchor > 0) {
            populateBatch(resultMap, allSentences.subList(0, anchor).reversed(), batch)
          }
        }
        return batch
      }

      private suspend fun parseAndCache(batch: LinkedHashSet<SentenceWithExclusions>): Map<SentenceWithExclusions, T?> {
        val parsed: Map<SentenceWithExclusions, T?> = parse(ArrayList(batch), project)
        for (entry in parsed.entries) {
          if (entry.value != null) {
            globalCache[entry.key] = entry.value
          }
        }
        return parsed
      }

      private fun populateBatch(resultMap: MutableMap<SentenceWithExclusions, T?>, pool: Collection<SentenceWithExclusions>,
                                batch: MutableCollection<SentenceWithExclusions>) {
        for (sentence in pool) {
          checkCache(sentence, resultMap, batch)
          if (batch.size == batchSize) {
            break
          }
        }
      }

      private fun checkCache(sentence: SentenceWithExclusions, resultMap: MutableMap<SentenceWithExclusions, T?>,
                             nonCached: MutableCollection<SentenceWithExclusions>) {
        if (isNonSentence(sentence.sentence)) {
          resultMap[sentence] = null
          return
        }

        val cached = globalCache[sentence]
        if (cached != null) {
          resultMap[sentence] = cached
        } else {
          nonCached.add(sentence)
        }
      }
    }
  }

  private fun isNonSentence(text: String) = text.isEmpty() || text.length >= 2000 || text.none { it.isLetter() }

  interface AsyncBatchParser<T> : BatchParser<T> {
    override fun parse(sentences: List<String>): LinkedHashMap<String, T?> =
      runBlockingCancellable {
        parseAsync(sentences.map { SentenceWithExclusions(it) })
      }
        .mapKeysTo(LinkedHashMap()) { it.key.sentence }

    suspend fun parseAsync(sentences: List<SentenceWithExclusions>): LinkedHashMap<SentenceWithExclusions, T?>
  }

  @Suppress("UnstableApiUsage")
  protected open fun reportStatus(reporter: RawProgressReporter) {}

  private fun hasOurLanguage(tc: TextContent): Boolean {
    if (!NaturalTextDetector.seemsNatural(tc.toString())) {
      return false
    }
    return getLanguageIfAvailable(tc.toString().substring(HighlightingUtil.stripPrefix(tc))) == language
  }

  companion object {
    private val LOG = Logger.getInstance(SentenceBatcher::class.java)

    @JvmStatic
    fun findInstalledLTLanguage(language: Language): org.languagetool.Language? {
      return HighlightingUtil.findInstalledLang(language)?.jLanguage
    }
  }

}