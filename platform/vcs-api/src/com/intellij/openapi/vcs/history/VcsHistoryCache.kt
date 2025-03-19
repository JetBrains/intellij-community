// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.vcs.history

import com.github.benmanes.caffeine.cache.Cache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.options.advanced.AdvancedSettings.Companion.getBoolean
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.VcsKey
import org.jetbrains.annotations.ApiStatus
import java.io.Serializable

class VcsHistoryCache {
  private val historyCache: Cache<HistoryCacheBaseKey, CachedHistory>
  private val annotationCache: Cache<HistoryCacheWithRevisionKey, Any>
  private val lastRevisionCache: Cache<HistoryCacheWithRevisionKey, VcsRevisionNumber>

  init {
    // increase cache size when preload enabled
    val preloadEnabled = getBoolean("vcs.annotations.preload") || Registry.`is`("vcs.code.author.inlay.hints")
    historyCache = Caffeine.newBuilder().maximumSize((if (preloadEnabled) 50 else 10).toLong()).build()
    annotationCache = Caffeine.newBuilder().maximumSize((if (preloadEnabled) 50 else 10).toLong()).build()
    lastRevisionCache = Caffeine.newBuilder().maximumSize(50).build()
  }

  fun <C : Serializable, T : VcsAbstractHistorySession> putSession(filePath: FilePath, correctedPath: FilePath?, vcsKey: VcsKey, session: T,
                                                                   factory: VcsCacheableHistorySessionFactory<C, T>, isFull: Boolean) {
    val cachedHistory = CachedHistory(correctedPath ?: filePath, session.revisionList, session.currentRevisionNumber,
                                      factory.getAdditionallyCachedData(session), isFull)
    historyCache.put(HistoryCacheBaseKey(filePath, vcsKey), cachedHistory)
  }

  @ApiStatus.ScheduledForRemoval
  @Deprecated(message = "Use putSession instead",
              replaceWith = ReplaceWith("putSession(filePath, correctedPath, vcsKey, session, factory, isFull)"))
  fun <C : Serializable, T : VcsAbstractHistorySession> put(filePath: FilePath, correctedPath: FilePath?, vcsKey: VcsKey, session: T,
                                                            factory: VcsCacheableHistorySessionFactory<C, T>, isFull: Boolean) {
    putSession(filePath, correctedPath, vcsKey, session, factory, isFull)
  }

  fun <C : Serializable, T : VcsAbstractHistorySession> getSession(filePath: FilePath, vcsKey: VcsKey,
                                                                   factory: VcsCacheableHistorySessionFactory<C, T>,
                                                                   allowPartial: Boolean): T? {
    val cachedHistory = historyCache.getIfPresent(HistoryCacheBaseKey(filePath, vcsKey))?.takeIf { it.isFull || allowPartial }
                        ?: return null
    val customData = cachedHistory.customData as C?
    return factory.createFromCachedData(customData, cachedHistory.revisions, cachedHistory.path, cachedHistory.currentRevision)
  }

  @Deprecated(message = "Use getSession instead", replaceWith = ReplaceWith("getSession(filePath, vcsKey, factory, false)"))
  fun <C : Serializable, T : VcsAbstractHistorySession> getFull(filePath: FilePath, vcsKey: VcsKey,
                                                                factory: VcsCacheableHistorySessionFactory<C, T>): T? {
    return getSession(filePath, vcsKey, factory, false)
  }

  fun getRevisions(filePath: FilePath, vcsKey: VcsKey): List<VcsFileRevision> {
    val cachedHistory = historyCache.getIfPresent(HistoryCacheBaseKey(filePath, vcsKey))
    if (cachedHistory == null) return emptyList()
    return cachedHistory.revisions
  }

  fun clearHistory() {
    historyCache.asMap().keys.removeIf { !it.filePath.isNonLocal }
  }

  fun putAnnotation(filePath: FilePath, vcsKey: VcsKey, number: VcsRevisionNumber, vcsAnnotation: Any) {
    annotationCache.put(HistoryCacheWithRevisionKey(filePath, vcsKey, number), vcsAnnotation)
  }

  fun getAnnotation(filePath: FilePath, vcsKey: VcsKey, number: VcsRevisionNumber): Any? {
    return annotationCache.getIfPresent(HistoryCacheWithRevisionKey(filePath, vcsKey, number))
  }

  fun clearAnnotations() {
    annotationCache.invalidateAll()
  }

  fun putLastRevision(filePath: FilePath, vcsKey: VcsKey, currentRevision: VcsRevisionNumber, lastRevision: VcsRevisionNumber) {
    lastRevisionCache.put(HistoryCacheWithRevisionKey(filePath, vcsKey, currentRevision), lastRevision)
  }

  fun getLastRevision(filePath: FilePath, vcsKey: VcsKey, currentRevision: VcsRevisionNumber): VcsRevisionNumber? {
    return lastRevisionCache.getIfPresent(HistoryCacheWithRevisionKey(filePath, vcsKey, currentRevision))
  }

  fun clearLastRevisions() {
    lastRevisionCache.invalidateAll()
  }

  fun clearAll() {
    clearHistory()
    clearAnnotations()
    clearLastRevisions()
  }

  private data class CachedHistory(val path: FilePath, val revisions: List<VcsFileRevision>, val currentRevision: VcsRevisionNumber?,
                                   val customData: Any?, val isFull: Boolean)
}
