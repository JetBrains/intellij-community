// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.vcs.changes.ignore.cache

import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vcs.changes.ignore.util.RegexUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileListener
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.Alarm
import com.intellij.util.ui.update.DisposableUpdate
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
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
@Service(Service.Level.PROJECT)
internal class IgnorePatternsMatchedFilesCache(private val project: Project) : Disposable {
  private val projectFileIndex = ProjectFileIndex.getInstance(project)

  private val cache =
    Caffeine.newBuilder()
      .expireAfterAccess(10, TimeUnit.MINUTES)
      .build<String, Collection<VirtualFile>>()

  private val updateQueue = MergingUpdateQueue("IgnorePatternsMatchedFilesCacheUpdateQueue", 500, true, null, this, null,
                                               Alarm.ThreadToUse.POOLED_THREAD)

  init {
    ApplicationManager.getApplication().messageBus.connect(this)
      .subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
        override fun after(events: List<VFileEvent>) {
          if (cache.estimatedSize() == 0L) {
            return
          }

          for (event in events) {
            if (event is VFileCreateEvent ||
                event is VFileDeleteEvent ||
                event is VFileCopyEvent) {
              cleanupCache(event.path)
            }
            else if (event is VFilePropertyChangeEvent && event.isRename) {
              cleanupCache(event.oldPath)
              cleanupCache(event.path)
            }
            else if (event is VFileMoveEvent) {
              cleanupCache(event.oldPath)
              cleanupCache(event.path)
            }
          }
        }

        private fun cleanupCache(path: String) {
          val cacheMap = cache.asMap()
          val globCache = PatternCache.getInstance(project)
          for (key in cacheMap.keys) {
            val pattern = globCache.getPattern(key) ?: continue
            val parts = RegexUtil.getParts(pattern)
            if (RegexUtil.matchAnyPart(parts, path)) {
              cacheMap.remove(key)
            }
          }
        }
      })
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
    updateQueue.queue(object : DisposableUpdate(project, key) {
      override fun canEat(update: Update) = true
      override fun doRun() = cache.put(key, doSearch(pattern))
    })

  private fun doSearch(pattern: Pattern): Set<VirtualFile> {
    val files = HashSet<VirtualFile>(1000)
    val parts = RegexUtil.getParts(pattern)
    if (parts.isEmpty()) {
      return files
    }

    val projectScope = GlobalSearchScope.projectScope(project)
    projectFileIndex.iterateContent { fileOrDir ->
      ProgressManager.checkCanceled()
      val name = fileOrDir.name
      if (RegexUtil.matchAnyPart(parts, name)) {
        for (file in runReadAction { FilenameIndex.getVirtualFilesByName(name, projectScope) }) {
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
      return project.getService(IgnorePatternsMatchedFilesCache::class.java)
    }
  }
}
