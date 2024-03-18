// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.JavaSyntheticLibrary
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap

/**
 * Integrates data provided by [DirectoryIndexExcludePolicy] and [AdditionalLibraryRootsProvider] extensions to [com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex].
 * Since these extensions don't support incremental updates, the corresponding parts of the index are rebuilt from scratch in [updateIfNeeded]
 * method after [resetCache] was called.
 */
internal class NonIncrementalContributors(private val project: Project,
                                          private val rootFileSupplier: RootFileSupplier) {
  private var allRoots = emptySet<VirtualFile>()
  private var excludedUrls = emptySet<VirtualFileUrl>()
  @Volatile
  private var upToDate = false
  private val lock = Any()

  fun updateIfNeeded(fileSets: MutableMap<VirtualFile, StoredFileSetCollection>,
                     fileSetsByPackagePrefix: PackagePrefixStorage,
                     nonExistingFilesRegistry: NonExistingWorkspaceRootsRegistry) {
    if (!upToDate) {
      ApplicationManager.getApplication().assertReadAccessAllowed()
      val (newExcludedRoots, newExcludedUrls) = computeCustomExcludedRoots()
      val newFileSets = computeFileSets()

      /*
      This function requires 'read access' only, so multiple thread may execute it in parallel, and we need to synchronize write access to
      the shared fileSets* maps. Modifications here are performed under exclusive lock while holding the global 'Read Action', and other 
      modifications are performed under the global 'Write Action' lock. The maps are always read under 'Read Action', and this function is
      called under the same 'Read Action' before accessing the map, and `upToDate` flag will not be reset before that read action finishes,
      so read/write conflicts shouldn't happen. 
      */
      synchronized(lock) {
        if (!upToDate) {
          allRoots.forEach { file ->
            fileSets.removeValueIf(file) { fileSet: StoredFileSet -> fileSet.entityPointer === NonIncrementalMarker }
            fileSetsByPackagePrefix.removeByPrefixAndPointer("", NonIncrementalMarker)
          }
          excludedUrls.forEach {
            nonExistingFilesRegistry.unregisterUrl(it, NonIncrementalMarker, EntityStorageKind.MAIN)
          }
          val newRoots = HashSet<VirtualFile>()
          Object2IntMaps.fastForEach(newExcludedRoots) { 
            fileSets.putValue(it.key, ExcludedFileSet.ByFileKind(it.intValue, NonIncrementalMarker))
            newRoots.add(it.key)
          }
          newExcludedUrls.forEach {
            nonExistingFilesRegistry.registerUrl(it, NonIncrementalMarker, EntityStorageKind.MAIN, NonExistingFileSetKind.EXCLUDED_FROM_CONTENT)
          }
          newFileSets.forEach { (root, sets) ->
            sets.forEach { set ->
              fileSets.putValue(root, set)
              val fileSet = set as? WorkspaceFileSetImpl
              if (fileSet != null && fileSet.data is JvmPackageRootDataInternal) {
                fileSetsByPackagePrefix.addFileSet("", fileSet)
              }
            }
            newRoots.add(root)
          }
          allRoots = newRoots
          excludedUrls = newExcludedUrls
          upToDate = true
        }
      }
    }
  }
  
  private fun computeCustomExcludedRoots(): Pair<Object2IntMap<VirtualFile>, Set<VirtualFileUrl>> {
    val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val excludedFiles = Object2IntOpenHashMap<VirtualFile>()
    val excludedUrls = HashSet<VirtualFileUrl>()

    DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project).forEach { policy -> 
      policy.excludeUrlsForProject.forEach { url ->
        val file = rootFileSupplier.findFileByUrl(url)
        if (file != null) {
          if (RootFileSupplier.ensureValid(file, project, policy)) {
            excludedFiles.put(file, WorkspaceFileKindMask.ALL)
          }
        }
        else {
          excludedUrls.add(virtualFileUrlManager.getOrCreateFromUri(url))
        }
      }
      policy.excludeSdkRootsStrategy?.let { strategy ->
        val sdks = ModuleManager.getInstance(project).modules.mapNotNullTo(HashSet()) { ModuleRootManager.getInstance(it).sdk }
        val sdkClasses = sdks.flatMapTo(HashSet()) { it.rootProvider.getFiles(OrderRootType.CLASSES).asList() }
        sdks.forEach { sdk ->
          strategy.`fun`(sdk).forEach { root ->
            if (root !in sdkClasses) {
              val correctedRoot = rootFileSupplier.correctRoot(root, sdk, policy)
              if (correctedRoot != null) {
                excludedFiles.put(correctedRoot, WorkspaceFileKindMask.EXTERNAL or excludedFiles.getInt(correctedRoot))
              }
            }
          }
        }
      }
      ModuleManager.getInstance(project).modules.forEach { module ->
        policy.getExcludeRootsForModule(ModuleRootManager.getInstance(module)).forEach { pointer ->
          val file = pointer.file
          if (file != null) {
            val correctedRoot = rootFileSupplier.correctRoot(file, module, policy)
            if (correctedRoot != null) {
              excludedFiles.put(correctedRoot, WorkspaceFileKindMask.CONTENT or excludedFiles.getInt(correctedRoot))
            }
          }
          else {
            excludedUrls.add(virtualFileUrlManager.getOrCreateFromUri(pointer.url))
          }
        }
      }
    }
    return excludedFiles to excludedUrls
  }

  private fun computeFileSets(): Map<VirtualFile, StoredFileSetCollection> {
    val result = HashMap<VirtualFile, StoredFileSetCollection>()
    AdditionalLibraryRootsProvider.EP_NAME.extensionList.forEach { provider ->
      provider.getAdditionalProjectLibraries(project).forEach { library ->
        fun registerRoots(files: MutableCollection<VirtualFile>, kind: WorkspaceFileKind, fileSetData: WorkspaceFileSetData) {
          files.forEach { root ->
            rootFileSupplier.correctRoot(root, library, provider)?.let {
              result.putValue(it, WorkspaceFileSetImpl(it, kind, NonIncrementalMarker, EntityStorageKind.MAIN, fileSetData))
            }
          }
        }
        //todo use comparisonId for incremental updates?
        registerRoots(library.sourceRoots, WorkspaceFileKind.EXTERNAL_SOURCE, if (library is JavaSyntheticLibrary) LibrarySourceRootFileSetData(null, "") else SyntheticLibrarySourceRootData)
        registerRoots(library.binaryRoots, WorkspaceFileKind.EXTERNAL, if (library is JavaSyntheticLibrary) LibraryRootFileSetData(null, "") else DummyWorkspaceFileSetData)
        library.excludedRoots.forEach {
          result.putValue(it, ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.EXTERNAL, NonIncrementalMarker))
        }
        library.unitedExcludeCondition?.let { condition ->
          val predicate = { file: VirtualFile -> condition.value(file) }
          (library.sourceRoots + library.binaryRoots).forEach { root ->
            result.putValue(root, ExcludedFileSet.ByCondition(root, predicate, NonIncrementalMarker, EntityStorageKind.MAIN))
          }
        }
      }
    }
    return result
  }

  @RequiresWriteLock
  fun resetCache() {
    upToDate = false
  }

  companion object {
    internal fun isFromAdditionalLibraryRootsProvider(fileSet: WorkspaceFileSet): Boolean {
      return fileSet.asSafely<WorkspaceFileSetImpl>()?.entityPointer is NonIncrementalMarker
    }

    fun isPlaceholderReference(entityPointer: EntityPointer<WorkspaceEntity>): Boolean {
      return entityPointer is NonIncrementalMarker
    }
  }
}

private object SyntheticLibrarySourceRootData : ModuleOrLibrarySourceRootData

private object NonIncrementalMarker : EntityPointer<WorkspaceEntity> {
  override fun resolve(storage: EntityStorage): WorkspaceEntity? = null
  override fun isPointerTo(entity: WorkspaceEntity): Boolean = false
}