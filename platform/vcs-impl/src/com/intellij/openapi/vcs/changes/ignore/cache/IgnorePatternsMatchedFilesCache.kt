// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.

package com.intellij.openapi.vcs.changes.ignore.cache

import com.google.common.cache.CacheBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.changes.ignore.util.RegexUtil
import com.intellij.openapi.vfs.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Alarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import gnu.trove.THashSet
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

/**
 * Cache that retrieves matching files using given [Pattern].
 * Cache population happened on demand in the background.
 * The cache eviction happen in the following cases:
 * * by using [VirtualFileListener] to handle filesystem changes and clean cache if needed for the specific pattern parts.
 * * after entries have been expired: entries becomes expired if no read/write operations happened with the corresponding key during some amount of time (10 minutes).
 * * after project dispose
 */
class IgnorePatternsMatchedFilesCache(private val project: Project,
                                      private val projectFileIndex: ProjectFileIndex,
                                      fileManager: VirtualFileManager) : Disposable {

  private val cache =
    CacheBuilder.newBuilder()
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .build<String, Collection<VirtualFile>>()

  private val updateQueue = MergingUpdateQueue("IgnorePatternsMatchedFilesCacheUpdateQueue", 500, true, null, this, null,
                                               Alarm.ThreadToUse.POOLED_THREAD)

  init {
    fileManager.addVirtualFileListener(object : VirtualFileListener {
      override fun fileCreated(event: VirtualFileEvent) = cleanupCache(event)
      override fun fileDeleted(event: VirtualFileEvent) = cleanupCache(event)
      override fun fileMoved(event: VirtualFileMoveEvent) = cleanupCache(event)
      override fun fileCopied(event: VirtualFileCopyEvent) = cleanupCache(event)
      override fun beforeFileMovement(event: VirtualFileMoveEvent) = cleanupCache(event)

      override fun propertyChanged(event: VirtualFilePropertyEvent) {
        if (event.isRename) {
          cleanupCache(event)
        }
      }

      private fun cleanupCache(event: VirtualFileEvent) {
        val cacheMap = cache.asMap()
        val globCache = PatternCache.getInstance(project)
        for (key in cacheMap.keys) {
          val pattern = globCache.getPattern(key) ?: continue
          val parts = RegexUtil.getParts(pattern)
          if (RegexUtil.matchAnyPart(parts, event.file.path)) {
            cacheMap.remove(key)
          }
        }
      }
    }, this)
  }

  override fun dispose() {
    cache.invalidateAll()
    updateQueue.cancelAllUpdates()
  }

  /**
   * Finds [VirtualFile] instances in project for the specific [Pattern] and caches them.
   *
   * @param pattern to match
   * @return matched files list
   */
  fun getFilesForPattern(pattern: Pattern): Collection<VirtualFile> {
    val key = pattern.toString()
    val files = cache.getIfPresent(key) ?: emptyList()

    if (files.isEmpty()) {
      runSearchRequest(key, pattern)
    }
    return files
  }

  private fun runSearchRequest(key: String, pattern: Pattern) =
    updateQueue.queue(object : Update(key) {
      override fun canEat(update: Update) = true
      override fun run() = cache.put(key, doSearch(pattern))
    })

  private fun doSearch(pattern: Pattern): THashSet<VirtualFile> {
    val files = THashSet<VirtualFile>(1000)
    val parts = RegexUtil.getParts(pattern)
    if (parts.isEmpty()) return files

    val projectScope = GlobalSearchScope.projectScope(project)
    projectFileIndex.iterateContent { fileOrDir ->
      val name = fileOrDir.name
      if (RegexUtil.matchAnyPart(parts, name)) {
        for (file in runReadAction { FilenameIndex.getVirtualFilesByName(project, name, projectScope) }) {
          if (file.isValid && RegexUtil.matchAllParts(parts, file.path)) {
            files.add(file)
          }
        }
      }
      return@iterateContent true
    }
    return files
  }

  companion object {
    @JvmStatic
    fun getInstance(project: Project): IgnorePatternsMatchedFilesCache {
      return ServiceManager.getService(project, IgnorePatternsMatchedFilesCache::class.java)
    }
  }
}
