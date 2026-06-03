package com.intellij.grazie.ide.language.markdown.semantics.analyzer

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import com.intellij.grazie.cloud.APIQueries
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
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

private typealias AnalyzerCacheKey<T> = Key<CachedValue<AtomicReference<Cached<LlmIssue<T>>>>>
private val log = Logger.getInstance(Analyzer::class.java)

internal object Analyzer {
  private val mutexKeys = ConcurrentHashMap<String, Key<Mutex>>()
  private val cacheKeys = ConcurrentHashMap<String, AnalyzerCacheKey<LlmIssue<*>>>()

  fun <T> analyze(analyzer: LlmAnalyzer<T>, file: PsiFile, client: SuspendableAPIGatewayClient): List<LlmIssue<T>> {
    @Suppress("UNCHECKED_CAST") val analyzerKey = cacheKeys
      .computeIfAbsent(analyzer.javaClass.name) { Key.create("cache key for ${analyzer.javaClass.name}") }
      as AnalyzerCacheKey<T>
    val ref = CachedValuesManager.getManager(file.project).getCachedValue(file, analyzerKey, {
      CachedValueProvider.Result.create(AtomicReference<Cached<LlmIssue<T>>>(), file)
    }, false)

    try {
      var cached = ref.get()
      val text = file.text
      if (cached == null || cached.text != text) {
        return executeRequestWithLock(analyzer, file) {
          cached = ref.get()
          if (cached != null && cached.text == text) return@executeRequestWithLock cached.data
          val start = System.currentTimeMillis()
          log.info("${analyzer::class.simpleName} starts executing request with lock")
          val analysis = analyzer.analyze(text, client)
          val done = System.currentTimeMillis()
          log.info("""
            Analyzing text with ${analyzer::class.simpleName} took ${done - start}ms on
            text with length ${text.length} and used ${analysis.spentCredits} tokens.
          """.trimIndent())
          ref.set(Cached(text, analysis.data))
          analysis.data
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

  private fun <T> executeRequestWithLock(analyzer: LlmAnalyzer<T>, file: PsiFile, action: suspend () -> List<LlmIssue<T>>): List<LlmIssue<T>> {
    val mutexKey = mutexKeys.computeIfAbsent(analyzer.javaClass.name) { Key.create("mutex key for ${analyzer.javaClass.name}") }
    val mutex = (file as UserDataHolderEx).getOrCreateUserData(mutexKey) { Mutex() }
    return runWithCheckCanceled {
      APIQueries.handleExceptions(file.project) {
        mutex.withLock {
          action()
        }
      }
    } ?: emptyList()
  }
}

private data class Cached<T>(val text: String, val data: List<T>)
