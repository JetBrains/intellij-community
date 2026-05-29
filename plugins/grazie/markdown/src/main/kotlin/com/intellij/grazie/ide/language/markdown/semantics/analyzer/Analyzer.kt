package com.intellij.grazie.ide.language.markdown.semantics.analyzer

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.rules.promptAnalysis.AmbiguityAnalyzer
import ai.grazie.rules.promptAnalysis.AmbiguityAnalyzer.Ambiguity
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.LlmAnalyzer.LlmIssue
import ai.grazie.rules.promptAnalysis.SecurityAnalyzer
import ai.grazie.rules.promptAnalysis.SecurityAnalyzer.SecurityVulnerability
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer.CoverageIssue
import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.grazie.cloud.APIQueries
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.UserDataHolderEx
import com.intellij.openapi.util.getOrCreateUserData
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ExceptionUtil
import com.intellij.util.io.computeDetached
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

private typealias AnalyzerCacheKey<T> = Key<CachedValue<AtomicReference<Cached<LlmIssue<T>>>>>
private val log = Logger.getInstance(Analyzer::class.java)

internal object Analyzer {
  private val CONTRADICTION_ANALYZER_KEY: AnalyzerCacheKey<LlmIssue<Contradiction>> =
    Key.create("ml.llm.markdown.semantics.contradictionAnalyzer")
  private val SECURITY_ANALYZER_KEY: AnalyzerCacheKey<LlmIssue<SecurityVulnerability>> =
    Key.create("ml.llm.markdown.semantics.securityAnalyzer")
  private val SEMANTIC_COVERAGE_ANALYZER_KEY: AnalyzerCacheKey<LlmIssue<CoverageIssue>> =
    Key.create("ml.llm.markdown.semantics.semanticCoverageAnalyzer")
  private val AMBIGUITY_ANALYZER_KEY: AnalyzerCacheKey<LlmIssue<Ambiguity>> = Key.create("ml.llm.markdown.semantics.ambiguityAnalyzer")

  private val CONTRADICTION_MUTEX_KEY: Key<Mutex> = Key.create("ml.llm.markdown.semantics.contradictionAnalyzer")
  private val SECURITY_MUTEX_KEY: Key<Mutex> = Key.create("ml.llm.markdown.semantics.securityAnalyzer")
  private val SEMANTIC_COVERAGE_MUTEX_KEY: Key<Mutex> = Key.create("ml.llm.markdown.semantics.semanticCoverageAnalyzer")
  private val AMBIGUITY_MUTEX_KEY: Key<Mutex> = Key.create("ml.llm.markdown.semantics.ambiguityAnalyzer")

  fun <T> analyze(analyzer: LlmAnalyzer<T>, file: PsiFile, client: SuspendableAPIGatewayClient): List<LlmIssue<T>> {
    val ref = CachedValuesManager.getManager(file.project).getCachedValue(file, getAnalyzerKey(analyzer), {
      CachedValueProvider.Result.create(AtomicReference<Cached<LlmIssue<T>>>(), file)
    }, false)

    try {
      val cached = ref.get()
      val text = file.text
      if (cached == null || cached.text != text) {
        return executeRequestWithLock(analyzer, file) {
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

  private fun <T> getAnalyzerKey(analyzer: LlmAnalyzer<T>): AnalyzerCacheKey<T> {
    return when (analyzer) {
      is ContradictionAnalyzer -> CONTRADICTION_ANALYZER_KEY
      is SecurityAnalyzer -> SECURITY_ANALYZER_KEY
      is SemanticCoverageAnalyzer -> SEMANTIC_COVERAGE_ANALYZER_KEY
      is AmbiguityAnalyzer -> AMBIGUITY_ANALYZER_KEY
      else -> error("Unsupported analyzer: ${analyzer.javaClass.name}")
    } as AnalyzerCacheKey<T>
  }

  private fun getMutexKey(analyzer: LlmAnalyzer<*>): Key<Mutex> {
    return when (analyzer) {
      is ContradictionAnalyzer -> CONTRADICTION_MUTEX_KEY
      is SecurityAnalyzer -> SECURITY_MUTEX_KEY
      is SemanticCoverageAnalyzer -> SEMANTIC_COVERAGE_MUTEX_KEY
      is AmbiguityAnalyzer -> AMBIGUITY_MUTEX_KEY
      else -> error("Unsupported analyzer: ${analyzer.javaClass.name}")
    }
  }

  private suspend fun restartInspections(analyzer: LlmAnalyzer<*>, file: PsiFile) {
    withContext(Dispatchers.EDT) {
      val project = file.project
      if (project.isInitialized && project.isOpen) {
        DaemonCodeAnalyzer.getInstance(project).restart(file, "${analyzer.javaClass.simpleName} restart")
        log.info("${analyzer.javaClass.simpleName} restarted inspections")
      }
    }
  }

  /**
   * Runs each analyzer request for a file under an analyzer-specific mutex. This serializes requests for the same file and analyzer,
   * avoiding unnecessary LLM calls and token usage.
   *
   * If the detached job is canceled, the inspection is restarted so highlighting can read the cached result.
   */
  @OptIn(DelicateCoroutinesApi::class)
  private fun <T> executeRequestWithLock(analyzer: LlmAnalyzer<T>, file: PsiFile, action: suspend () -> List<LlmIssue<T>>): List<LlmIssue<T>> {
    val mutex = (file as UserDataHolderEx).getOrCreateUserData(getMutexKey(analyzer)) { Mutex() }
    if (mutex.isLocked) return emptyList()
    return runBlockingCancellable {
      computeDetached {
        APIQueries.handleExceptions(file.project) {
          mutex.withLock {
            try {
              action()
            } finally {
              if (!this@computeDetached.isActive) {
                log.info("${analyzer.javaClass.simpleName} was cancelled")
                withContext(NonCancellable) {
                  log.info("${analyzer.javaClass.simpleName} will schedule inspections restart")
                  restartInspections(analyzer, file)
                }
              }
            }
          }
        }
      }
    } ?: emptyList()
  }
}

private data class Cached<T>(val text: String, val data: List<T>)
