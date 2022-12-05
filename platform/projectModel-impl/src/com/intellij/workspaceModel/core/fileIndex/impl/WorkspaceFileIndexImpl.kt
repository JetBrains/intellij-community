// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.injected.editor.VirtualFileWindow
import com.intellij.model.ModelBranch
import com.intellij.notebook.editor.BackedVirtualFile
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.CustomEntityProjectModelInfoProvider
import com.intellij.openapi.roots.impl.DirectoryIndexImpl
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.LowMemoryWatcher
import com.intellij.openapi.util.Pair
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.BulkFileListener
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.util.CollectionQuery
import com.intellij.util.Query
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity

class WorkspaceFileIndexImpl(private val project: Project) : WorkspaceFileIndexEx {
  companion object {
    private val EP_NAME = ExtensionPointName<WorkspaceFileIndexContributor<*>>("com.intellij.workspaceModel.fileIndexContributor")
    private val BRANCH_INDEX_DATA_KEY = Key.create<Pair<Long, WorkspaceFileIndexData>>("BRANCH_WORKSPACE_FILE_INDEX")
  }

  @Volatile
  private var indexData: WorkspaceFileIndexData? = null 

  init {
    project.messageBus.connect().subscribe(VirtualFileManager.VFS_CHANGES, object : BulkFileListener {
      override fun after(events: List<VFileEvent>) {
        val data = indexData
        if (data != null && DirectoryIndexImpl.shouldResetOnEvents(events)) {
          data.clearPackageDirectoryCache()
          if (events.any { DirectoryIndexImpl.isIgnoredFileCreated(it) }) {
            data.resetFileCache()
          }
        }
      }
    })
    LowMemoryWatcher.register({
      indexData?.onLowMemory()
    }, project)
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

  override fun ensureInitialized() {
    getOrCreateMainIndexData()
  }

  override fun unloadModules(entities: List<ModuleEntity>) {
    indexData?.unloadModules(entities)
  }

  override fun loadModules(entities: List<ModuleEntity>) {
    indexData?.loadModules(entities)
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

  override fun getPackageName(directory: VirtualFile): String? {
    return getOrCreateIndexData(directory).getPackageName(directory)
  }

  override fun getDirectoriesByPackageName(packageName: String, includeLibrarySources: Boolean): Query<VirtualFile> {
    return getOrCreateMainIndexData().getDirectoriesByPackageName(packageName, includeLibrarySources)
  }

  override fun getDirectoriesByPackageName(packageName: String, scope: GlobalSearchScope): Query<VirtualFile> {
    val branches = scope.modelBranchesAffectingScope
    if (branches.isEmpty()) {
      return getDirectoriesByPackageName(packageName, true).filtering { scope.contains(it) }
    }
    val indexDataList = mutableListOf(getOrCreateMainIndexData()).also {
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
    return getOrCreateMainIndexData()
  }

  private fun getOrCreateMainIndexData(): WorkspaceFileIndexData {
    var data = indexData
    if (data == null) {
      data = WorkspaceFileIndexData(contributors, project, RootFileSupplier.INSTANCE)
      indexData = data
    }
    return data
  }

  private val contributors: List<WorkspaceFileIndexContributor<*>>
    get() = EP_NAME.extensionList + CustomEntityProjectModelInfoProvider.EP.extensionList.map { CustomEntityProjectModelInfoProviderBridge(it) }

  private fun obtainBranchIndexData(branch: ModelBranch): WorkspaceFileIndexData {
    var pair = branch.getUserData(BRANCH_INDEX_DATA_KEY)
    val modCount = branch.branchedVfsStructureModificationCount
    if (pair == null || pair.first != modCount) {
      pair = Pair.create(modCount, WorkspaceFileIndexData(contributors, branch.project, RootFileSupplier.forBranch(branch)))
      branch.putUserData(BRANCH_INDEX_DATA_KEY, pair)
    }
    return pair.second
  }

  override fun resetCustomContributors() {
    indexData?.resetCustomContributors()
  }

  override fun markDirty(entityReferences: Collection<EntityReference<WorkspaceEntity>>, filesToInvalidate: Collection<VirtualFile>) {
    indexData?.markDirty(entityReferences, filesToInvalidate)
  }

  override fun updateDirtyEntities() {
    indexData?.updateDirtyEntities()
  }

  fun onEntitiesChanged(event: VersionedStorageChange) {
    indexData?.onEntitiesChanged(event)
  }
}

