// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.model.ModelBranch
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ContentIteratorEx
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider
import com.intellij.openapi.roots.impl.DirectoryIndexImpl
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.CollectionQuery
import com.intellij.util.PathUtil
import com.intellij.util.Query
import com.intellij.util.ThreeState
import com.intellij.util.containers.TreeNodeProcessingResult
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndexContributor
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetWithCustomData
import com.intellij.workspaceModel.core.fileIndex.impl.WorkspaceFileInternalInfo.NonWorkspace
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager

class WorkspaceFileIndexImpl(private val project: Project) : WorkspaceFileIndexEx, Disposable.Default {
  companion object {
    val EP_NAME: ExtensionPointName<WorkspaceFileIndexContributor<*>> = ExtensionPointName("com.intellij.workspaceModel.fileIndexContributor")
    private val BRANCH_INDEX_DATA_KEY = Key.create<Pair<Long, WorkspaceFileIndexData>>("BRANCH_WORKSPACE_FILE_INDEX")
  }

  @Volatile
  override var indexData: WorkspaceFileIndexData = EmptyWorkspaceFileIndexData.NOT_INITIALIZED 

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
    CustomEntityProjectModelInfoProvider.EP.addChangeListener(clearData, this)
  }

  override fun isInWorkspace(file: VirtualFile): Boolean {
    return findFileSet(file = file,
                       honorExclusion = true,
                       includeContentSets = true,
                       includeExternalSets = true,
                       includeExternalSourceSets = true) != null
  }

  override fun isInContent(file: VirtualFile): Boolean {
    return findFileSet(file = file,
                       honorExclusion = true,
                       includeContentSets = true,
                       includeExternalSets = false,
                       includeExternalSourceSets = false) != null
  }

  override fun getContentFileSetRoot(file: VirtualFile, honorExclusion: Boolean): VirtualFile? {
    return findFileSet(file, honorExclusion, true, false, false)?.root
  }

  override fun isUrlInContent(url: String): ThreeState {
    var currentUrl = url
    val fileManager = VirtualFileManager.getInstance()
    val urlManager = VirtualFileUrlManager.getInstance(project)
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

  override fun processContentFilesRecursively(fileOrDir: VirtualFile,
                                              processor: ContentIteratorEx,
                                              customFilter: VirtualFileFilter?,
                                              fileSetFilter: (WorkspaceFileSetWithCustomData<*>) -> Boolean): Boolean {
    val visitor = object : VirtualFileVisitor<Void?>() {
      override fun visitFileEx(file: VirtualFile): Result {
        val fileInfo = runReadAction {
            getFileInfo(file, true, true, false, false)
        }
        if (file.isDirectory && fileInfo is NonWorkspace) {
          return when (fileInfo) {
            NonWorkspace.EXCLUDED, NonWorkspace.NOT_UNDER_ROOTS -> {
              processContentFilesUnderExcludedDirectory(file, processor, customFilter, fileSetFilter, fileOrDir)
            }
            NonWorkspace.IGNORED, NonWorkspace.INVALID -> {
              SKIP_CHILDREN
            }
          }
        }
        val accepted = runReadAction {
          fileInfo.findFileSet(fileSetFilter) != null && (customFilter == null || customFilter.accept(file))
        }
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
                                                        rootDir: VirtualFile): VirtualFileVisitor.Result {
    /* there may be other file sets under this directory; their URLs must be registered in VirtualFileUrlManager,
       so it's enough to process VirtualFileUrls only. */
    val virtualFileUrlManager = VirtualFileUrlManager.getInstance(project)
    val virtualFileUrl = virtualFileUrlManager.findByUrl(dir.url) ?: return VirtualFileVisitor.SKIP_CHILDREN
    val processed = virtualFileUrlManager.processChildrenRecursively(virtualFileUrl) { childUrl ->
      val childFile = childUrl.virtualFile ?: return@processChildrenRecursively TreeNodeProcessingResult.SKIP_CHILDREN
      return@processChildrenRecursively if (runReadAction { isInContent (childFile) }) {
        if (processContentFilesRecursively(childFile, processor, customFilter, fileSetFilter)) {
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
                           includeExternalSourceSets: Boolean): WorkspaceFileSet? {
    return when (val info = getFileInfo(file, honorExclusion, includeContentSets, includeExternalSets, includeExternalSourceSets)) {
      is WorkspaceFileSetImpl -> info
      is MultipleWorkspaceFileSets -> info.find(null)
      else -> null
    }
  }

  override suspend fun initialize() {
    readAction {
      initializeBlocking()
    }
  }

  override fun initializeBlocking() {
    if (indexData is EmptyWorkspaceFileIndexData) {
      indexData = WorkspaceFileIndexDataImpl(contributors, project, RootFileSupplier.INSTANCE)
    }
  }

  override fun <D : WorkspaceFileSetData> findFileSetWithCustomData(file: VirtualFile,
                                                                    honorExclusion: Boolean,
                                                                    includeContentSets: Boolean,
                                                                    includeExternalSets: Boolean,
                                                                    includeExternalSourceSets: Boolean,
                                                                    customDataClass: Class<out D>): WorkspaceFileSetWithCustomData<D>? {
    val result = when (val info = getFileInfo(file, honorExclusion, includeContentSets, includeExternalSets, includeExternalSourceSets)) {
      is WorkspaceFileSetWithCustomData<*> -> info.takeIf { customDataClass.isInstance(it.data) }
      is MultipleWorkspaceFileSets -> info.find(customDataClass)
      else -> null
    }
    @Suppress("UNCHECKED_CAST")
    return result as? WorkspaceFileSetWithCustomData<D>
  }

  override fun getFileInfo(file: VirtualFile,
                           honorExclusion: Boolean,
                           includeContentSets: Boolean,
                           includeExternalSets: Boolean,
                           includeExternalSourceSets: Boolean): WorkspaceFileInternalInfo {
    val unwrappedFile = BackedVirtualFile.getOriginFileIfBacked((file as? VirtualFileWindow)?.delegate ?: file)
    return getOrCreateIndexData(unwrappedFile).getFileInfo(unwrappedFile, honorExclusion, includeContentSets, includeExternalSets, includeExternalSourceSets)
  }

  override fun visitFileSets(visitor: WorkspaceFileSetVisitor) {
    getMainIndexData().visitFileSets(visitor)
  }

  override fun getPackageName(directory: VirtualFile): String? {
    return getOrCreateIndexData(directory).getPackageName(directory)
  }

  override fun getDirectoriesByPackageName(packageName: String, includeLibrarySources: Boolean): Query<VirtualFile> {
    return getMainIndexData().getDirectoriesByPackageName(packageName, includeLibrarySources)
  }

  override fun getDirectoriesByPackageName(packageName: String, scope: GlobalSearchScope): Query<VirtualFile> {
    val branches = scope.modelBranchesAffectingScope
    if (branches.isEmpty()) {
      return getDirectoriesByPackageName(packageName, true).filtering { scope.contains(it) }
    }
    val indexDataList = mutableListOf(getMainIndexData()).also {
      branches.mapTo(it, ::obtainBranchIndexData)
    }
    return CollectionQuery(indexDataList)
             .flatMapping { it.getDirectoriesByPackageName(packageName, true) }
             .filtering { scope.contains(it) }
  }

  private fun getOrCreateIndexData(file: VirtualFile): WorkspaceFileIndexData {
    val branch = ModelBranch.getFileBranch(file)
    if (branch != null) {
      return obtainBranchIndexData(branch)
    }
    return getMainIndexData()
  }

  private fun getMainIndexData(): WorkspaceFileIndexData {
    var data = indexData
    when (data) {
      EmptyWorkspaceFileIndexData.NOT_INITIALIZED -> {
        if (!project.isDefault) {
          thisLogger().error("WorkspaceFileIndex is not initialized yet, empty data is returned. Activities which use the project configuration must be postponed until the project is fully loaded.")
        }
        else {
          thisLogger().warn("WorkspaceFileIndex must not be queried for the default project")
        }
      }
      EmptyWorkspaceFileIndexData.RESET -> {
        data = WorkspaceFileIndexDataImpl(contributors, project, RootFileSupplier.INSTANCE)
        indexData = data
      }
    }
    return data
  }

  val contributors: List<WorkspaceFileIndexContributor<*>>
    get() = EP_NAME.extensionList + CustomEntityProjectModelInfoProvider.EP.extensionList.map { CustomEntityProjectModelInfoProviderBridge(it) }

  private fun obtainBranchIndexData(branch: ModelBranch): WorkspaceFileIndexData {
    var pair = branch.getUserData(BRANCH_INDEX_DATA_KEY)
    val modCount = branch.branchedVfsStructureModificationCount
    if (pair == null || pair.first != modCount) {
      pair = Pair(modCount, WorkspaceFileIndexDataImpl(contributors, branch.project, RootFileSupplier.forBranch(branch)))
      branch.putUserData(BRANCH_INDEX_DATA_KEY, pair)
    }
    return pair.second
  }

  override fun reset() {
    indexData = EmptyWorkspaceFileIndexData.RESET
  }
}

