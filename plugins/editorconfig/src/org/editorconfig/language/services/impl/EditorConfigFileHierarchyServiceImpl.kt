// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.editorconfig.language.services.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressIndicatorProvider
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.registry.RegistryValue
import com.intellij.openapi.util.registry.RegistryValueListener
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleSettingsManager
import com.intellij.reference.SoftReference
import com.intellij.util.PathUtil
import com.intellij.util.concurrency.SequentialTaskExecutor
import com.intellij.util.containers.FixedHashMap
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import org.editorconfig.EditorConfigRegistry
import org.editorconfig.language.filetype.EditorConfigFileConstants
import org.editorconfig.language.psi.EditorConfigPsiFile
import org.editorconfig.language.psi.reference.EditorConfigVirtualFileDescriptor
import org.editorconfig.language.services.EditorConfigFileHierarchyService
import org.editorconfig.language.services.EditorConfigServiceLoaded
import org.editorconfig.language.services.EditorConfigServiceLoading
import org.editorconfig.language.services.EditorConfigServiceResult
import org.editorconfig.language.util.EditorConfigPsiTreeUtil
import org.editorconfig.language.util.matches
import java.lang.ref.Reference

class EditorConfigFileHierarchyServiceImpl(private val project: Project) : EditorConfigFileHierarchyService(), BulkFileListener, RegistryValueListener {
  private val taskExecutor = SequentialTaskExecutor.createSequentialApplicationPoolExecutor("EditorConfig.notification.vfs.update.executor")

  private val updateQueue = MergingUpdateQueue("EditorConfigFileHierarchy UpdateQueue", 500, true, null, project)

  @Volatile
  private var cacheDropsCount = 0
  private val cacheLocker = Any()
  private val affectingFilesCache = FixedHashMap<VirtualFile, Reference<List<EditorConfigPsiFile>>>(CacheSize)

  init {
    DumbService.getInstance(project).runWhenSmart {
      ApplicationManager.getApplication().messageBus.connect(project).subscribe(VirtualFileManager.VFS_CHANGES, this)
    }
    Registry.get(EditorConfigRegistry.EDITORCONFIG_STOP_AT_PROJECT_ROOT_KEY).addListener(this, project)
  }

  private fun updateHandlers(project: Project) {
    updateQueue.queue(Update.create("editorconfig hierarchy update") {
      CodeStyleSettingsManager.getInstance(project).notifyCodeStyleSettingsChanged()
    })
  }

  // method of BulkFileListener
  override fun after(events: List<VFileEvent>) {
    val editorConfigs = events
      .asSequence()
      .filter { PathUtil.getFileName(it.path) == EditorConfigFileConstants.FILE_NAME }
      .toList()
    if (editorConfigs.isNotEmpty()) {
      synchronized(cacheLocker) {
        cacheDropsCount += 1
        affectingFilesCache.clear()
      }
      updateHandlers(project)
    }
  }

  override fun beforeValueChanged(value: RegistryValue) {}

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
    ReadAction
      .nonBlocking<List<EditorConfigPsiFile>?> { findApplicableFiles(virtualFile) }
      .expireWith(project)
      .finishOnUiThread(ModalityState.any()) ui@{ affectingFiles ->
      if (affectingFiles == null) return@ui
      synchronized(cacheLocker) {
        if (expectedCacheDropsCount != cacheDropsCount) return@ui
        affectingFilesCache[virtualFile] = SoftReference(affectingFiles)
      }
    }.submit(taskExecutor)
  }

  /**
   * *null* means that operation was aborted due to cache drop
   */
  private fun findApplicableFiles(virtualFile: VirtualFile): List<EditorConfigPsiFile>? {
    val app = ApplicationManager.getApplication()
    Log.assertTrue(!app.isDispatchThread)
    app.assertReadAccessAllowed()
    val expectedCacheDropsCount = cacheDropsCount
    val parentFiles = when {
      !EditorConfigRegistry.shouldStopAtProjectRoot() -> findParentPsiFiles(virtualFile)
      else -> EditorConfigPsiTreeUtil.findAllParentsFiles(PsiManager.getInstance(project).findFile(virtualFile) ?: return null)
    }
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
    val app = ApplicationManager.getApplication()
    Log.assertTrue(!app.isDispatchThread)
    app.assertReadAccessAllowed()
    val expectedCacheDropsCount = cacheDropsCount

    val parents =
      findParentFiles(file)
        ?.asSequence()
        ?.sortedBy(EditorConfigVirtualFileDescriptor(file)::distanceToParent)
        ?.mapNotNull {
          PsiManager.getInstance(project).findFile(it) as? EditorConfigPsiFile
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
    val app = ApplicationManager.getApplication()
    Log.assertTrue(!app.isDispatchThread)
    app.assertReadAccessAllowed()
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
