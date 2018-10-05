// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services.impl

import com.intellij.openapi.application.Application
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.reference.SoftReference
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.containers.FixedHashMap
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.reference.EditorConfigVirtualFileDescriptor
import org.editorconfig.language.services.*
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.matches
import java.lang.ref.Reference

class EditorConfigFileHierarchyServiceImpl(
  private val manager: PsiManager,
  private val application: Application,
  project: Project
) : EditorConfigFileHierarchyService, BulkFileListener, RegistryValueListener.Adapter() {
  private val taskExecutor = SequentialTaskExecutor
    .createSequentialApplicationPoolExecutor("editorconfig.notification.vfs.update.executor")

  @Volatile
  private var cacheDropsCount = 0
  private val cacheLocker = Any()
  private val affectingFilesCache = FixedHashMap<VirtualFile, Reference<List<EditorConfigPsiFile>>>(CacheSize)

  init {
    application.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, this)
    Registry.get(EditorConfigRegistry.EDITORCONFIG_STOP_AT_PROJECT_ROOT_KEY).addListener(this, project)
  }

  private fun updateHandlers() {
    application.assertIsDispatchThread()
    application.messageBus.syncPublisher(EditorConfigNotificationTopic).editorConfigChanged()
  }

  // method of BulkFileListener
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

  // method of RegistryValueListener
  override fun afterValueChanged(value: RegistryValue) {
    synchronized(cacheLocker) {
      cacheDropsCount += 1
      affectingFilesCache.clear()
    }
  }

  override fun getParentEditorConfigFiles(virtualFile: VirtualFile): EditorConfigServiceResult {
    val cachedResult = SoftReference.dereference(synchronized(cacheLocker) {
      affectingFilesCache[virtualFile]
    })

    if (cachedResult != null) return EditorConfigServiceLoaded(cachedResult)
    startBackgroundTask(virtualFile)
    return EditorConfigServiceLoading
  }

  private fun startBackgroundTask(virtualFile: VirtualFile) {
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
    val parentFiles = if (!EditorConfigRegistry.shouldStopAtProjectRoot()) findParentPsiFiles(virtualFile)
    else EditorConfigPsiTreeUtil.findAllParentsFiles(manager.findFile(virtualFile) ?: return null)
    return parentFiles?.filter { parent ->
      parent.sections.any { section ->
        if (expectedCacheDropsCount != cacheDropsCount) return null
        ProgressIndicatorProvider.checkCanceled()
        val header = section.header
        if (header.isValidGlob) header matches virtualFile
        else false
      }
    }
  }

  /**
   * Searches for files manually, i.e. without using indices.
   * *null* means that operation was aborted due to cache drop.
   * Current file *is* included.
   * Honors root declarations.
   */
  private fun findParentPsiFiles(file: VirtualFile): List<EditorConfigPsiFile>? {
    Log.assertTrue(!application.isDispatchThread)
    application.assertReadAccessAllowed()
    val expectedCacheDropsCount = cacheDropsCount

    val parents =
      findParentFiles(file)
        ?.asSequence()
        ?.sortedBy(EditorConfigVirtualFileDescriptor(file)::distanceToParent)
        ?.mapNotNull {
          manager.findFile(it) as? EditorConfigPsiFile
        } ?: return null

    val firstRoot = parents.indexOfFirst(EditorConfigPsiFile::hasValidRootDeclaration)

    if (cacheDropsCount != expectedCacheDropsCount) return null
    ProgressManager.checkCanceled()

    return if (firstRoot < 0) parents.toList()
    else parents.take(firstRoot + 1).toList()
  }

  /**
   * *null* means that operation was aborted due to cache drop.
   * Current file *is* included
   */
  private fun findParentFiles(file: VirtualFile): List<VirtualFile>? {
    Log.assertTrue(!application.isDispatchThread)
    application.assertReadAccessAllowed()
    val fileName = EditorConfigFileConstants.FILE_NAME
    val expectedCacheDropsCount = cacheDropsCount
    val result = mutableListOf<VirtualFile>()

    var currentFolder: VirtualFile? = file.parent
    while (currentFolder != null) {
      if (cacheDropsCount != expectedCacheDropsCount) return null
      ProgressManager.checkCanceled()
      val child = currentFolder.findChild(fileName)
      if (child != null && !child.isDirectory) {
        result.add(child)
      }

      currentFolder = currentFolder.parent
    }

    return result
  }

  private companion object {
    private val Log = logger<EditorConfigFileHierarchyService>()
    private const val CacheSize = 10
  }
}
