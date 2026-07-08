package com.intellij.grazie.ide.language.markdown.semantics.analyzer

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.LlmContradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.Specification
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.WithSpending
import ai.grazie.utils.mpp.money.Credit
import com.intellij.grazie.GrazieBundle
import com.intellij.grazie.cloud.APIQueries
import com.intellij.grazie.ide.language.markdown.semantics.fus.SpecificationFUSCollector
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.util.runWithCheckCanceled
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private typealias IssuesWithSpending<T> = WithSpending<Map<String, List<LlmIssue<T>>>>
private typealias AnalyzerCacheKey<T> = Key<CachedValue<AtomicReference<Cached<LlmIssue<T>>>>>
private val log = Logger.getInstance(SpecificationAnalyzer::class.java)

@ApiStatus.Experimental
internal object SpecificationAnalyzer {
  private val mutexKeys = ConcurrentHashMap<String, Key<Mutex>>()
  private val cacheKeys = ConcurrentHashMap<String, AnalyzerCacheKey<LlmIssue<*>>>()

  @RequiresBackgroundThread
  fun <T> analyze(analyzer: LlmAnalyzer<T>, file: PsiFile, files: Set<PsiFile>, client: SuspendableAPIGatewayClient): List<LlmIssue<T>> {
    @Suppress("UNCHECKED_CAST") val analyzerKey = cacheKeys
      .computeIfAbsent(analyzer.javaClass.name) { Key.create("cache key for ${analyzer.javaClass.name}") }
      as AnalyzerCacheKey<T>
    val cachedData = getCached(files, analyzerKey)
    val ref = cachedData.first { it.first == file }.second

    try {
      var cached = ref.get()
      val text = file.text
      val other = cachedData.filterTo(HashSet()) { it.first != file }
      if (isOutdated(file, ref) || isOutdated(other)) {
        return executeRequestWithLock(analyzer, files) {
          cached = ref.get()
          if (!isOutdated(file, ref) && !isOutdated(other)) return@executeRequestWithLock cached.data
          val specifications = getSpecifications(cached, file, other)
          val start = System.currentTimeMillis()
          val analyzerName = analyzer::class.simpleName
          log.info("$analyzerName starts executing request with lock")
          val analysis = analyze(analyzer, specifications, client)
          val timeMs = System.currentTimeMillis() - start
          val credits = analysis.spentCredits / Credit.CREDITS_IN_DOLLAR
          log.info("""
            Analyzing text with $analyzerName took $timeMs ms on
            text with length ${text.length} and used $credits credits.
          """.trimIndent())
          cached = Cached(text, analysis.data[file.virtualFile.path].orEmpty())
          ref.set(cached)
          other.forEach { (otherFile, otherRef) ->
            otherRef.set(Cached(otherFile.text, analysis.data[otherFile.virtualFile.path].orEmpty()))
          }
          SpecificationFUSCollector.analysisCompleted(analyzerName!!, specifications, analysis, timeMs)
          cached.data
        }
      }
      return cached.data
    }
    catch (e: Throwable) {
      val cause = ExceptionUtil.getRootCause(e)
      if (cause is ProcessCanceledException) {
        throw cause
      }
      throw e
    }
  }

  @Suppress("UNCHECKED_CAST")
  private fun <T> analyze(
    analyzer: LlmAnalyzer<T>, specifications: Set<Specification<T>>, client: SuspendableAPIGatewayClient
  ): IssuesWithSpending<T> {
    val analysis = analyzer.analyze(specifications, client)
    if (analysis.data.values.asSequence().flatMap { it }.any { it.issue is Contradiction }) {
      return analyzeContradictions(analysis as IssuesWithSpending<Contradiction>) as IssuesWithSpending<T>
    }
    return analysis
  }

  private fun analyzeContradictions(analysis: IssuesWithSpending<Contradiction>): IssuesWithSpending<Contradiction> {
    val newAnalysis = HashMap<String, MutableList<LlmIssue<Contradiction>>>()
    analysis.data.forEach { (path, issues) ->
      issues.forEach { issue ->
        newAnalysis.computeIfAbsent(path) { ArrayList() }.add(issue)
        val contradiction = Contradiction(
          "", "", GrazieBundle.message("specification.contradiction.message"),
          issue.issue.contradictsStartOffset, issue.issue.contradictsEndOffset,
          issue.issue.startOffset, issue.issue.endOffset, emptyList<String>(),
          issue.issue.contradictsPath, issue.issue.path
        )
        newAnalysis.computeIfAbsent(issue.issue.contradictsPath) { ArrayList() }
          .add(LlmContradiction(contradiction, issue.issue.contradictsStartOffset, issue.issue.contradictsEndOffset))
      }
    }

    return WithSpending(newAnalysis, analysis.spentCredits)
  }

  private fun <T> getCached(file: PsiFile, files: Set<PsiFile>, analyzerKey: AnalyzerCacheKey<T>) =
    CachedValuesManager.getManager(file.project).getCachedValue(file, analyzerKey, {
      CachedValueProvider.Result.create(AtomicReference<Cached<LlmIssue<T>>>(), files)
    }, false)

  private fun <T> getCached(files: Set<PsiFile>, analyzerKey: AnalyzerCacheKey<T>) =
    files.map { it to getCached(it, files, analyzerKey) }

  private fun <T> getSpecifications(
    cached: Cached<LlmIssue<T>>?, file: PsiFile,
    other: Set<Pair<PsiFile, AtomicReference<Cached<LlmIssue<T>>>>>
  ): Set<Specification<T>> {
    val result = HashSet<Specification<T>>()
    result.add(getSpecification(cached, file))
    other.forEach { result.add(getSpecification(it.second.get(), it.first)) }
    return result
  }

  private fun <T> getSpecification(cache: Cached<LlmIssue<T>>?, file: PsiFile): Specification<T> {
    val name = file.virtualFile.path
    return if (cache == null) Specification(name, file.text)
    else Specification(name, file.text, cache.text, cache.data)
  }

  private fun <T> isOutdated(cachedData: Set<Pair<PsiFile, AtomicReference<Cached<LlmIssue<T>>>>>): Boolean =
    cachedData.any { (file, ref) -> isOutdated(file, ref) }

  private fun <T> isOutdated(file: PsiFile, holder: AtomicReference<Cached<LlmIssue<T>>>): Boolean {
    val cached = holder.get()
    return cached == null || cached.text != file.text
  }

  private fun <T> executeRequestWithLock(analyzer: LlmAnalyzer<T>, files: Set<PsiFile>, action: suspend () -> List<LlmIssue<T>>): List<LlmIssue<T>> {
    val mutexKey = mutexKeys.computeIfAbsent(analyzer.javaClass.name) { Key.create("mutex key for ${analyzer.javaClass.name}") }
    val mutexes = getMutexes(files, mutexKey)
    return runWithCheckCanceled {
      APIQueries.handleExceptions(files.first().project) {
        mutexes.withLock {
          action()
        }
      }
    } ?: emptyList()
  }

  private fun getMutexes(files: Set<PsiFile>, mutexKey: Key<Mutex>): List<Mutex> =
    files.sortedBy { it.viewProvider.virtualFile.path }.map { (it as UserDataHolderEx).getOrCreateUserData(mutexKey) { Mutex() } }

  private suspend inline fun <T> List<Mutex>.withLock(action: () -> T): T {
    val locked = ArrayList<Mutex>(size)
    return try {
      this.forEach {
        it.lock()
        locked.add(it)
      }
      action()
    } finally {
      locked.forEach { it.unlock() }
    }
  }
}

private data class Cached<T>(val text: String, val data: List<T>)
