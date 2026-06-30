package com.intellij.grazie.ide.language.markdown.semantics.analyzer

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.Specification
import ai.grazie.utils.mpp.money.Credit
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private typealias AnalyzerCacheKey<T> = Key<CachedValue<AtomicReference<Cached<LlmIssue<T>>>>>
private val log = Logger.getInstance(SpecificationAnalyzer::class.java)

data class LlmResults<T>(val id: UUID, val data: List<T>)

internal object SpecificationAnalyzer {
  private val idKey = Key.create<UUID>("specification id")
  private val mutexKeys = ConcurrentHashMap<String, Key<Mutex>>()
  private val cacheKeys = ConcurrentHashMap<String, AnalyzerCacheKey<LlmIssue<*>>>()

  fun <T> analyze(analyzer: LlmAnalyzer<T>, file: PsiFile, client: SuspendableAPIGatewayClient): LlmResults<LlmIssue<T>> {
    @Suppress("UNCHECKED_CAST") val analyzerKey = cacheKeys
      .computeIfAbsent(analyzer.javaClass.name) { Key.create("cache key for ${analyzer.javaClass.name}") }
      as AnalyzerCacheKey<T>
    val ref = CachedValuesManager.getManager(file.project).getCachedValue(file, analyzerKey, {
      CachedValueProvider.Result.create(AtomicReference<Cached<LlmIssue<T>>>(), file)
    }, false)
    val id = (file as UserDataHolderEx).getOrCreateUserData(idKey) { UUID.randomUUID() }

    try {
      var cached = ref.get()
      val text = file.text
      if (cached == null || cached.text != text) {
        return executeRequestWithLock(analyzer, file) {
          cached = ref.get()
          if (cached != null && cached.text == text) return@executeRequestWithLock LlmResults(id, cached.data)
          val start = System.currentTimeMillis()
          val analyzerName = analyzer::class.simpleName
          log.info("$analyzerName starts executing request with lock")
          val analysis = analyzer.analyze(getSpecification(cached, text), client)
          val timeMs = System.currentTimeMillis() - start
          val credits = analysis.spentCredits / Credit.CREDITS_IN_DOLLAR
          log.info("""
            Analyzing text with $analyzerName took $timeMs ms on
            text with length ${text.length} and used $credits credits.
          """.trimIndent())
          cached = Cached(text, analysis.data)
          ref.set(cached)
          SpecificationFUSCollector.analysisCompleted(id, analyzerName!!, text.length, credits, timeMs, analysis.data.size)
          LlmResults(id, cached.data)
        }
      }
      return LlmResults(id, cached.data)
    }
    catch (e: Throwable) {
      val cause = ExceptionUtil.getRootCause(e)
      if (cause is ProcessCanceledException) {
        throw cause
      }
      throw e
    }
  }

  private fun <T> getSpecification(cache: Cached<LlmIssue<T>>?, text: String): Specification<T> =
    if (cache == null) Specification(text) else Specification(text, cache.text, cache.data)

  private fun <T> executeRequestWithLock(analyzer: LlmAnalyzer<T>, file: PsiFile, action: suspend () -> LlmResults<LlmIssue<T>>): LlmResults<LlmIssue<T>> {
    val mutexKey = mutexKeys.computeIfAbsent(analyzer.javaClass.name) { Key.create("mutex key for ${analyzer.javaClass.name}") }
    val mutex = (file as UserDataHolderEx).getOrCreateUserData(mutexKey) { Mutex() }
    return runWithCheckCanceled {
      APIQueries.handleExceptions(file.project) {
        mutex.withLock {
          action()
        }
      }
    } ?: LlmResults(UUID.fromString("00000000-0000-0000-0000-0000000000"), emptyList())
  }
}

private data class Cached<T>(val text: String, val data: List<T>)
