package com.intellij.grazie.ide.language.markdown.semantics.analyzer

import ai.grazie.api.gateway.client.SuspendableAPIGatewayClient
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer
import ai.grazie.rules.promptAnalysis.ContradictionAnalyzer.Contradiction
import ai.grazie.rules.promptAnalysis.LlmAnalyzer
import ai.grazie.rules.promptAnalysis.SecurityAnalyzer
import ai.grazie.rules.promptAnalysis.SecurityAnalyzer.SecurityVulnerability
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer
import ai.grazie.rules.promptAnalysis.SemanticCoverageAnalyzer.CoverageIssue
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.util.Key
import com.intellij.psi.PsiFile
import com.intellij.psi.util.CachedValue
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.util.ExceptionUtil
import java.util.concurrent.atomic.AtomicReference

private typealias AnalyzerCacheKey<T> = Key<CachedValue<AtomicReference<Cached<T>>>>

internal object Analyzer {

  private val CONTRADICTION_ANALYZER_KEY: AnalyzerCacheKey<Contradiction> =
    Key.create("ml.llm.markdown.semantics.contradictionAnalyzer")
  private val SECURITY_ANALYZER_KEY: AnalyzerCacheKey<SecurityVulnerability> =
    Key.create("ml.llm.markdown.semantics.securityAnalyzer")
  private val SEMANTIC_COVERAGE_ANALYZER_KEY: AnalyzerCacheKey<CoverageIssue> =
    Key.create("ml.llm.markdown.semantics.semanticCoverageAnalyzer")

  fun <T> analyze(analyzer: LlmAnalyzer<T>, file: PsiFile, client: SuspendableAPIGatewayClient): List<T> {
    val text = file.text

    val ref = CachedValuesManager.getManager(file.project).getCachedValue(file, getAnalyzerKey(analyzer), {
      CachedValueProvider.Result.create(AtomicReference<Cached<T>>(), file)
    }, false)

    try {
      var cached = ref.get()
      if (cached == null || cached.text != text) {
        val start = System.currentTimeMillis()
        val analysis = analyzer.analyze(text, client)
        val done = System.currentTimeMillis()
        thisLogger().info("""
          Analyzing text with ${analyzer::class.simpleName} took ${done - start}ms on 
          text with length ${text.length} and used ${analysis.spentCredits} tokens.
        """.trimIndent())
        ref.set(Cached(text, analysis.data).also { cached = it })
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
  private fun <T> getAnalyzerKey(analyzer: LlmAnalyzer<T>): AnalyzerCacheKey<T> {
    return when (analyzer) {
      is ContradictionAnalyzer -> CONTRADICTION_ANALYZER_KEY
      is SecurityAnalyzer -> SECURITY_ANALYZER_KEY
      is SemanticCoverageAnalyzer -> SEMANTIC_COVERAGE_ANALYZER_KEY
      else -> error("Unsupported analyzer: ${analyzer.javaClass.name}")
    } as AnalyzerCacheKey<T>
  }
}

private data class Cached<T>(val text: String, val data: List<T>)
