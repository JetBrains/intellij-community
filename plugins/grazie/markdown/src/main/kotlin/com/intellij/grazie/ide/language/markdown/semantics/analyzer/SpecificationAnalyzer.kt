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
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.ConcurrentHashMap

private typealias IssuesWithSpending<T> = WithSpending<Map<String, List<LlmIssue<T>>>>
private typealias AnalyzerCacheKey<T> = Key<Cached<LlmIssue<T>>>

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
    val other = files.filter { it != file }
    val dependencies = files.mapTo(HashSet()) { it.viewProvider.virtualFile.path }

    try {
      val text = file.text
      var otherCached = other.map { it to it.getUserData(analyzerKey) }
      var cached = file.getUserData(analyzerKey)
      if (isOutdated(file, cached, dependencies) || otherCached.any { (file, cached) -> isOutdated(file, cached, dependencies) }) {
        return executeRequestWithLock(analyzer, files) {
          cached = file.getUserData(analyzerKey)
          otherCached = other.map { it to it.getUserData(analyzerKey) }
          if (!isOutdated(file, cached, dependencies) && otherCached.none { (file, cached) -> isOutdated(file, cached, dependencies) }) return@executeRequestWithLock cached!!.data
          val specifications = getSpecifications(cached, file, otherCached)
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
          cached = Cached(text, dependencies, analysis.data[file.virtualFile.path].orEmpty())
          file.putUserData(analyzerKey, cached)
          otherCached.forEach { (otherFile, _) ->
            otherFile.putUserData(analyzerKey, Cached(otherFile.text, dependencies, analysis.data[otherFile.virtualFile.path].orEmpty()))
          }
          SpecificationFUSCollector.analysisCompleted(analyzerName!!, specifications, analysis, timeMs)
          cached!!.data
        }
      }
      return cached!!.data
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

  private fun <T> getSpecifications(
    cached: Cached<LlmIssue<T>>?, file: PsiFile,
    other: Collection<Pair<PsiFile, Cached<LlmIssue<T>>?>>,
  ): Set<Specification<T>> {
    val result = HashSet<Specification<T>>()
    result.add(getSpecification(cached, file))
    other.forEach { result.add(getSpecification(it.second, it.first)) }
    return result
  }

  private fun <T> getSpecification(cache: Cached<LlmIssue<T>>?, file: PsiFile): Specification<T> {
    val name = file.virtualFile.path
    return if (cache == null) Specification(name, file.text)
    else Specification(name, file.text, cache.text, cache.data)
  }

  private fun <T> isOutdated(file: PsiFile, cached: Cached<LlmIssue<T>>?, dependencies: Set<String>): Boolean =
    cached == null || cached.text != file.text || cached.dependencies != dependencies

  private fun <T> executeRequestWithLock(
    analyzer: LlmAnalyzer<T>, files: Collection<PsiFile>, action: suspend () -> List<LlmIssue<T>>,
  ): List<LlmIssue<T>> {
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

  private fun getMutexes(files: Collection<PsiFile>, mutexKey: Key<Mutex>): List<Mutex> =
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

private data class Cached<T>(val text: String, val dependencies: Set<String>, val data: List<T>)
