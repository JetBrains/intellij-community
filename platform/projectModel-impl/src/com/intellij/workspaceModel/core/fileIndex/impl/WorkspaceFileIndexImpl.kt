// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.ThrottledLogger
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.roots.impl.DirectoryIndexImpl
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.url.VirtualFileUrlManagerImpl
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.PathUtil
import com.intellij.util.Query
import com.intellij.util.ThreeState
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo.NonWorkspace
import java.util.concurrent.TimeUnit.MINUTES
import java.util.concurrent.atomic.AtomicReference

class WorkspaceFileIndexImpl(private val project: Project) : WorkspaceFileIndexEx, Disposable.Default {
  companion object {
    val EP_NAME: ExtensionPointName<WorkspaceFileIndexContributor<*>> = ExtensionPointName("com.intellij.workspaceModel.fileIndexContributor")
  }

  private val indexDataReference = AtomicReference<WorkspaceFileIndexData>(EmptyWorkspaceFileIndexData.NOT_INITIALIZED)
  private val throttledLogger = ThrottledLogger(thisLogger(), MINUTES.toMillis(1))
  
  constructor(project: Project, indexData: WorkspaceFileIndexData) : this(project) {
    indexDataReference.set(indexData)
  }

  override var indexData: WorkspaceFileIndexData
    get() = indexDataReference.get()
    set(newValue) {
      fun WorkspaceFileIndexData.dispose() {
        if (this is Disposable) Disposer.dispose(this)
      }
      val current = indexDataReference.get()
      if (indexDataReference.compareAndSet(current, newValue)) {
        current.dispose()
      }
      else {
        newValue.dispose()
      }
    }

  init {
    project.messageBus.simpleConnect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        if (DirectoryIndexImpl.shouldResetOnEvents(events)) {
          indexData.clearPackageDirectoryCache()
          if (events.any(DirectoryIndexImpl::isIgnoredFileCreated)) {
            indexData.resetFileCache()
          }
        }
      }
    })
    LowMemoryWatcher.register({ indexData.onLowMemory() }, project)
    val clearData = Runnable { indexData = EmptyWorkspaceFileIndexData.RESET }
    EP_NAME.addChangeListener(clearData, this)
  }

  override fun isInWorkspace(file: VirtualFile): Boolean {
    return findFileSet(file = file,
                       honorExclusion = true,
                       includeContentSets = true,
                       includeExternalSets = true,
                       includeExternalSourceSets = true,
                       includeCustomKindSets = true) != null
  }

  override fun isInContent(file: VirtualFile): Boolean {
    return findFileSet(file = file,
                       honorExclusion = true,
                       includeContentSets = true,
                       includeExternalSets = false,
                       includeExternalSourceSets = false,
                       includeCustomKindSets = false) != null
  }

  override fun isIndexable(file: VirtualFile): Boolean {
    val type = findFileSet(file = file,
                           honorExclusion = true,
                           includeContentSets = true,
                           includeExternalSets = true,
                           includeExternalSourceSets = false,
                           includeCustomKindSets = false)
    return type?.kind?.isIndexable ?: false
  }

  override fun getContentFileSetRoot(file: VirtualFile, honorExclusion: Boolean): VirtualFile? {
    return findFileSet(file, honorExclusion, true, false, false, false)?.root
  }

  override fun isUrlInContent(url: String): ThreeState {
    var currentUrl = url
    val fileManager = VirtualFileManager.getInstance()
    val urlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    while (currentUrl.isNotEmpty()) {
      val file = fileManager.findFileByUrl(currentUrl)
      if (file != null) {
        return ThreeState.fromBoolean(isInContent(file))
      }
      val virtualFileUrl = urlManager.findByUrl(currentUrl)
      if (virtualFileUrl != null) {
        val kinds = getMainIndexData().getNonExistentFileSetKinds(virtualFileUrl)
        if (NonExistingFileSetKind.EXCLUDED_FROM_CONTENT in kinds) {
          return ThreeState.NO
        }
        if (NonExistingFileSetKind.EXCLUDED_OTHER in kinds) {
          return ThreeState.UNSURE
        }
        if (NonExistingFileSetKind.INCLUDED_CONTENT in kinds) {
          return ThreeState.YES
        }
      }
      currentUrl = PathUtil.getParentPath(currentUrl)
    }
    return ThreeState.NO
  }

  override fun processIndexableFilesRecursively(fileOrDir: VirtualFile,
                                                processor: ContentIteratorEx,
                                                customFilter: VirtualFileFilter?,
                                                fileSetFilter: (WorkspaceFileSetWithCustomData<*>) -> Boolean): Boolean {
    return processIndexableFilesRecursively(fileOrDir, processor, customFilter, fileSetFilter, 0)
  }

  private fun processIndexableFilesRecursively(fileOrDir: VirtualFile,
                                               processor: ContentIteratorEx,
                                               customFilter: VirtualFileFilter?,
                                               fileSetFilter: (WorkspaceFileSetWithCustomData<*>) -> Boolean,
                                               numberOfExcludedParentDirectories: Int): Boolean {
    val visitor = object : VirtualFileVisitor<Void?>() {
      override fun visitFileEx(file: VirtualFile): Result {
        val fileInfo = ApplicationManager.getApplication().runReadAction(Computable {
          getFileInfo(file = file,
                      honorExclusion = true,
                      includeContentSets = true,
                      includeExternalSets = false,
                      includeExternalSourceSets = false,
                      includeCustomKindSets = false)
        })
        if (file.isDirectory && fileInfo is NonWorkspace) {
          return when (fileInfo) {
            NonWorkspace.EXCLUDED, NonWorkspace.NOT_UNDER_ROOTS -> {
              processContentFilesUnderExcludedDirectory(file, processor, customFilter, fileSetFilter, fileOrDir, 
                                                        numberOfExcludedParentDirectories)
            }
            NonWorkspace.IGNORED, NonWorkspace.INVALID -> {
              SKIP_CHILDREN
            }
          }
        }
        val accepted = ApplicationManager.getApplication().runReadAction(Computable {
          fileInfo.findFileSet(fileSetFilter)?.kind?.isIndexable == true && (customFilter == null || customFilter.accept(file))
        })
        val status = if (accepted) processor.processFileEx(file) else TreeNodeProcessingResult.CONTINUE
        return when (status) {
          TreeNodeProcessingResult.CONTINUE -> CONTINUE
          TreeNodeProcessingResult.SKIP_CHILDREN -> SKIP_CHILDREN
          TreeNodeProcessingResult.SKIP_TO_PARENT -> skipTo(file.parent)
          TreeNodeProcessingResult.STOP -> skipTo(fileOrDir)
        }
      }
    }
    val result = VfsUtilCore.visitChildrenRecursively(fileOrDir, visitor)
    return result.skipToParent != fileOrDir
  }

  private fun processContentFilesUnderExcludedDirectory(dir: VirtualFile,
                                                        processor: ContentIteratorEx,
                                                        customFilter: VirtualFileFilter?,
                                                        fileSetFilter: (WorkspaceFileSetWithCustomData<*>) -> Boolean,
                                                        rootDir: VirtualFile,
                                                        numberOfExcludedParentDirectories: Int): VirtualFileVisitor.Result {
    if (numberOfExcludedParentDirectories > 5) {
      /* 
         It seems improbable that there are more than 5 alternations between excluded and non-excluded directories, so it seems that this 
         is an infinite recursion.
         However, check should catch such cases in VirtualFileVisitor.allowVisitChildren, so report the details and skip processing.
      */
      reportInfiniteRecursion(dir, this)
      return VirtualFileVisitor.SKIP_CHILDREN
    }
    
    /* there may be other file sets under this directory; their URLs must be registered in VirtualFileUrlManager,
       so it's enough to process VirtualFileUrls only. */
    val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager() as VirtualFileUrlManagerImpl
    val virtualFileUrl = virtualFileUrlManager.findByUrl(dir.url) ?: return VirtualFileVisitor.SKIP_CHILDREN
    val processed = virtualFileUrlManager.processChildrenRecursively(virtualFileUrl) { childUrl ->
      val childFile = childUrl.virtualFile ?: return@processChildrenRecursively TreeNodeProcessingResult.SKIP_CHILDREN
      return@processChildrenRecursively if (runReadAction { isIndexable (childFile) }) {
        if (processIndexableFilesRecursively(childFile, processor, customFilter, fileSetFilter, numberOfExcludedParentDirectories + 1)) {
          TreeNodeProcessingResult.SKIP_CHILDREN
        }
        else {
          TreeNodeProcessingResult.STOP
        }
      }
      else {
        TreeNodeProcessingResult.CONTINUE
      }
    }
    return if (!processed) {
      VirtualFileVisitor.skipTo(rootDir)
    }
    else VirtualFileVisitor.SKIP_CHILDREN
  }

  override fun findFileSet(file: VirtualFile,
                           honorExclusion: Boolean,
                           includeContentSets: Boolean,
                           includeExternalSets: Boolean,
                           includeExternalSourceSets: Boolean,
                           includeCustomKindSets: Boolean): WorkspaceFileSet? {
    return when (val info = getFileInfo(file, honorExclusion, includeContentSets,
                                        includeExternalSets, includeExternalSourceSets, includeCustomKindSets)) {
      is WorkspaceFileSetImpl -> info
      is MultipleWorkspaceFileSets -> info.find(null)
      else -> null
    }
  }

  override fun findFileSets(file: VirtualFile,
                            honorExclusion: Boolean,
                            includeContentSets: Boolean,
                            includeExternalSets: Boolean,
                            includeExternalSourceSets: Boolean,
                            includeCustomKindSets: Boolean): List<WorkspaceFileSet> {
    val info = getFileInfo(
      file = file,
      honorExclusion = honorExclusion,
      includeContentSets = includeContentSets,
      includeExternalSets = includeExternalSets,
      includeExternalSourceSets = includeExternalSourceSets,
      includeCustomKindSets = includeCustomKindSets
    )
    return when (info) {
      is WorkspaceFileSetImpl -> listOf(info)
      is MultipleWorkspaceFileSets -> info.fileSets
      else -> emptyList()
    }
  }

  override suspend fun initialize() {
    if (indexData is EmptyWorkspaceFileIndexData) {
      val contributors = EP_NAME.extensionList
      indexData = WorkspaceFileIndexDataImpl(contributorList = contributors, project = project, parentDisposable = this)
    }
  }

  override fun initializeBlocking() {
    if (indexData is EmptyWorkspaceFileIndexData) {
      indexData = WorkspaceFileIndexDataImpl(EP_NAME.extensionList, project, this)
    }
  }

  override fun <D : WorkspaceFileSetData> findFileSetWithCustomData(file: VirtualFile,
                                                                    honorExclusion: Boolean,
                                                                    includeContentSets: Boolean,
                                                                    includeExternalSets: Boolean,
                                                                    includeExternalSourceSets: Boolean,
                                                                    includeCustomKindSets: Boolean,
                                                                    customDataClass: Class<out D>): WorkspaceFileSetWithCustomData<D>? {
    val result = when (val info = getFileInfo(file, honorExclusion, includeContentSets,
                                              includeExternalSets, includeExternalSourceSets, includeCustomKindSets)) {
      is WorkspaceFileSetWithCustomData<*> -> info.takeIf { customDataClass.isInstance(it.data) }
      is MultipleWorkspaceFileSets -> info.find(customDataClass)
      else -> null
    }
    @Suppress("UNCHECKED_CAST")
    return result as? WorkspaceFileSetWithCustomData<D>
  }

  override fun <D : WorkspaceFileSetData> findFileSetsWithCustomData(
    file: VirtualFile,
    honorExclusion: Boolean,
    includeContentSets: Boolean,
    includeExternalSets: Boolean,
    includeExternalSourceSets: Boolean,
    includeCustomKindSets: Boolean,
    customDataClass: Class<out D>
  ): List<WorkspaceFileSetWithCustomData<D>> {
    val info = getFileInfo(file, honorExclusion, includeContentSets, includeExternalSets, includeExternalSourceSets, includeCustomKindSets)
    val result = when (info) {
      is WorkspaceFileSetWithCustomData<*> -> listOfNotNull(info.takeIf { customDataClass.isInstance(it.data) })
      is MultipleWorkspaceFileSets -> info.fileSets.filter { customDataClass.isInstance(it.data) }
      else -> emptyList()
    }
    @Suppress("UNCHECKED_CAST")
    return result as List<WorkspaceFileSetWithCustomData<D>>
  }

  override fun getFileInfo(file: VirtualFile,
                           honorExclusion: Boolean,
                           includeContentSets: Boolean,
                           includeExternalSets: Boolean,
                           includeExternalSourceSets: Boolean,
                           includeCustomKindSets: Boolean): WorkspaceFileInternalInfo {
    val unwrappedFile = BackedVirtualFile.getOriginFileIfBacked((file as? VirtualFileWindow)?.delegate ?: file)
    return getMainIndexData().getFileInfo(unwrappedFile, honorExclusion, includeContentSets,
                                          includeExternalSets, includeExternalSourceSets, includeCustomKindSets)
  }

  override fun <E : WorkspaceEntity> findContainingEntities(file: VirtualFile, entityClass: Class<E>, honorExclusion: Boolean, includeContentSets: Boolean, includeExternalSets: Boolean, includeExternalSourceSets: Boolean, includeCustomKindSets: Boolean): Collection<E> {
    val allEntities = findContainingEntities(file, honorExclusion, includeContentSets, includeExternalSets, includeExternalSourceSets, includeCustomKindSets)
    @Suppress("UNCHECKED_CAST")
    return allEntities.filter { entity -> entity.getEntityInterface() == entityClass } as Collection<E>
  }

  override fun findContainingEntities(file: VirtualFile, honorExclusion: Boolean, includeContentSets: Boolean, includeExternalSets: Boolean, includeExternalSourceSets: Boolean, includeCustomKindSets: Boolean): Collection<WorkspaceEntity> {
    return when (val fileInfo = getFileInfo(file, honorExclusion, includeContentSets, includeExternalSets, includeExternalSourceSets, includeCustomKindSets)) {
      is WorkspaceFileSetImpl -> listOfNotNull(resolveEntity(fileInfo))
      is MultipleWorkspaceFileSets -> fileInfo.fileSets.mapNotNull { fileSet ->
        (fileSet as? StoredFileSet?)?.let { resolveEntity(it) }
      }
      is NonWorkspace -> return emptyList()
    }
  }

  private fun resolveEntity(fileSet: StoredFileSet): WorkspaceEntity? {
    if (fileSet.entityStorageKind != EntityStorageKind.MAIN) return null
    return fileSet.entityPointer.resolve(WorkspaceModel.getInstance(project).currentSnapshot)
  }

  @RequiresReadLock
  override fun visitFileSets(visitor: WorkspaceFileSetVisitor) {
    getMainIndexData().visitFileSets(visitor)
  }

  override fun getPackageName(directory: VirtualFile): String? {
    return getMainIndexData().getPackageName(directory)
  }

  override fun getDirectoriesByPackageName(packageName: String, includeLibrarySources: Boolean): Query<VirtualFile> {
    return getMainIndexData().getDirectoriesByPackageName(packageName, includeLibrarySources)
  }

  override fun getDirectoriesByPackageName(packageName: String, scope: GlobalSearchScope): Query<VirtualFile> {
    return getDirectoriesByPackageName(packageName, true).filtering { scope.contains(it) }
  }

  override fun getFilesByPackageName(packageName: String): Query<VirtualFile> {
    return getMainIndexData().getFilesByPackageName(packageName)
  }

  private fun getMainIndexData(): WorkspaceFileIndexData {
    when (indexData) {
      EmptyWorkspaceFileIndexData.NOT_INITIALIZED -> {
        if (project.isDefault) {
          throttledLogger.warn("WorkspaceFileIndex must not be queried for the default project", Throwable())
        }
        else {
          thisLogger().error("WorkspaceFileIndex is not initialized yet, empty data is returned. Activities which use the project configuration must be postponed until the project is fully loaded." +
                             "It is possible to check Project.isInitialized to verify that the project is fully loaded.")
        }
      }
      EmptyWorkspaceFileIndexData.RESET -> {
        indexData = WorkspaceFileIndexDataImpl(EP_NAME.extensionList, project, this)
      }
    }
    return indexData
  }

  override fun reset() {
    indexData = EmptyWorkspaceFileIndexData.RESET
  }
}

