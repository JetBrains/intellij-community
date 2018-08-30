// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services.impl

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.reference.SoftReference
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.containers.FixedHashMap
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.services.*
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.matches
import java.lang.ref.Reference

class EditorConfigNotificationServiceImpl(
  private val manager: PsiManager,
  private val application: Application
) : EditorConfigNotificationService, BulkFileListener {
  private val taskExecutor = SequentialTaskExecutor
    .createSequentialApplicationPoolExecutor("editorconfig.notification.vfs.update.executor")

  @Volatile
  private var cacheDropsCount = 0
  private val cacheLocker = Any()
  private val affectingFilesCache = FixedHashMap<VirtualFile, Reference<List<EditorConfigPsiFile>>>(CacheSize)

  init {
    application.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this)
  }

  private fun updateHandlers() {
    application.assertIsDispatchThread()
    application.messageBus.syncPublisher(EditorConfigNotificationTopic).editorConfigChanged()
  }

  override fun after(events: List<VFileEvent>) {
    val editorConfigs = events
      .asSequence()
      .filter { it.path.endsWith(EditorConfigFileConstants.FILE_NAME) }
      .mapNotNull(VFileEvent::getFile)
      .filter { it.name == EditorConfigFileConstants.FILE_NAME }
      .toList()
    if (editorConfigs.isNotEmpty()) {
      synchronized(cacheLocker) {
        cacheDropsCount += 1
        affectingFilesCache.clear()
      }

      updateHandlers()
    }
  }

  override fun getApplicableFiles(virtualFile: VirtualFile): EditorConfigServiceResult {
    application.assertIsDispatchThread()
    val cachedResult = SoftReference.dereference(synchronized(cacheLocker) {
      affectingFilesCache[virtualFile]
    })

    if (cachedResult != null) return EditorConfigServiceLoaded(cachedResult)
    startBackgroundTask(virtualFile)
    return EditorConfigServiceLoading
  }

  private fun startBackgroundTask(virtualFile: VirtualFile) {
    application.assertIsDispatchThread()
    val expectedCacheDropsCount = cacheDropsCount
    ReadAction.nonBlocking<List<EditorConfigPsiFile>?> {
      findApplicableFiles(virtualFile)
    }.finishOnUiThread(ModalityState.any()) ui@{ affectingFiles ->
      affectingFiles ?: return@ui
      synchronized(cacheLocker) {
        if (expectedCacheDropsCount != cacheDropsCount) return@ui
        affectingFilesCache[virtualFile] = SoftReference(affectingFiles)
      }

      updateHandlers()
    }.submit(taskExecutor)
  }

  /**
   * *null* means that operation was aborted due to cache drop
   */
  private fun findApplicableFiles(virtualFile: VirtualFile): List<EditorConfigPsiFile>? {
    Log.assertTrue(!application.isDispatchThread)
    application.assertReadAccessAllowed()
    val expectedCacheDropsCount = cacheDropsCount
    val psiFile = manager.findFile(virtualFile) ?: return emptyList()
    return EditorConfigPsiTreeUtil.findAllParentsFiles(psiFile).filter { parent ->
      parent.sections.any { section ->
        if (expectedCacheDropsCount != cacheDropsCount) return null
        ProgressIndicatorProvider.checkCanceled()
        section.header matches virtualFile
      }
    }
  }

  private companion object {
    private val Log = logger<EditorConfigNotificationService>()
    private const val CacheSize = 10
  }
}
