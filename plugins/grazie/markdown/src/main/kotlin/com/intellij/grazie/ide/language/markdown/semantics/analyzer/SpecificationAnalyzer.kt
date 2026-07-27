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
import com.intellij.openapi.util.updateUserData
import com.intellij.psi.PsiFile
import com.intellij.util.ExceptionUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import kotlinx.coroutines.sync.Mutex
import org.jetbrains.annotations.ApiStatus
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentHashMap

private typealias AnalyzerCacheKey<T> = Key<Cache<LlmIssue<T>>>

private val log = Logger.getInstance(SpecificationAnalyzer::class.java)

data class Costs(val credits: Double, val since: LocalDateTime = LocalDateTime.now()) {
  operator fun plus(other: Double): Costs = Costs(credits + other, since)
}

@ApiStatus.Experimental
internal object SpecificationAnalyzer {
  private val mutexKeys = ConcurrentHashMap<String, Key<Mutex>>()
  private val cacheKeys = ConcurrentHashMap<String, AnalyzerCacheKey<LlmIssue<*>>>()
  private val costKey = Key.create<Costs>("cost key for analyzers")

  fun getCosts(file: PsiFile): Costs? = file.viewProvider.virtualFile.getUserData(costKey)

  @RequiresBackgroundThread
  fun <T> analyze(analyzer: LlmAnalyzer<T>, file: PsiFile, files: Set<PsiFile>, client: SuspendableAPIGatewayClient): List<LlmIssue<T>> {
    @Suppress("UNCHECKED_CAST") val analyzerKey = cacheKeys
      .computeIfAbsent(analyzer.javaClass.name) { Key.create("cache key for ${analyzer.javaClass.name}") }
      as AnalyzerCacheKey<T>
    val storages = files.map { Storage(it, analyzerKey) }
    val dependencies = storages.mapTo(HashSet()) { it.name }

    try {
      val textLength = file.text.length
      if (storages.any { it.isOutdated(dependencies) })
        return executeRequestWithLock(analyzer, files) {
          val newStorages = storages.map { it.copy(cache = it.file.getUserData(analyzerKey)) }
          if (newStorages.none { it.isOutdated(dependencies) }) {
            return@executeRequestWithLock newStorages.first { it.file == file }.cache!!.data
          }

          val specifications = newStorages.mapTo(HashSet()) { getSpecification(it) }
          val start = System.currentTimeMillis()
          val analyzerName = analyzer::class.simpleName
          log.info("$analyzerName starts executing request with lock")
          val analysis = analyzer.analyze(specifications, client)
          val timeMs = System.currentTimeMillis() - start
          val credits = analysis.spentCredits() / Credit.CREDITS_IN_DOLLAR
          log.info("""
            Analyzing text with $analyzerName took $timeMs ms on
            text with length ${textLength} and used $credits credits.
          """.trimIndent())
          SpecificationFUSCollector.analysisCompleted(analyzerName!!, specifications, analysis, timeMs)

          newStorages.forEach { storage ->
            storage.file.putUserData(
              analyzerKey,
              Cache(storage.text, dependencies, storage.stamp, analysis.data[storage.name].orEmpty())
            )
            (storage.file.viewProvider.virtualFile as UserDataHolderEx).updateUserData(costKey) {
              // Split costs equally across files
              (it ?: Costs(0.0)) + credits / newStorages.size
            }
          }
          analysis.data[file.viewProvider.virtualFile.path].orEmpty()
        }
      return storages.first { it.file == file }.cache!!.data
    }
    catch (e: Throwable) {
      val cause = ExceptionUtil.getRootCause(e)
      if (cause is ProcessCanceledException) {
        throw cause
      }
      throw e
    }
  }

  private fun <T> getSpecification(storage: Storage<T>): Specification<T> {
    val name = storage.name
    return if (storage.cache == null) Specification(name, storage.text)
    else Specification(name, storage.text, storage.cache.text, storage.cache.data)
  }

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

private data class Storage<T>(val file: PsiFile, val name: String, val text: String, val stamp: Long, val cache: Cache<LlmIssue<T>>?) {
  constructor(file: PsiFile, analyzerKey: AnalyzerCacheKey<T>) :
    this(file, file.viewProvider.virtualFile.path, file.text, file.modificationStamp, file.getUserData(analyzerKey))

  fun isOutdated(dependencies: Set<String>): Boolean =
    cache == null || cache.stamp != this.stamp || cache.dependencies != dependencies
}

private data class Cache<T>(val text: String, val dependencies: Set<String>, val stamp: Long, val data: List<T>)
