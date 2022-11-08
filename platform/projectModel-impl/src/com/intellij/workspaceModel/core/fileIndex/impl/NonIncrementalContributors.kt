// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
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
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
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
  @Volatile
  private var upToDate = false
  private val lock = Any()

  fun updateIfNeeded(fileSets: MultiMap<VirtualFile, StoredFileSet>, fileSetsByPackagePrefix: MultiMap<String, WorkspaceFileSetImpl>) {
    if (!upToDate) {
      ApplicationManager.getApplication().assertReadAccessAllowed()
      val newExcludedRoots = computeCustomExcludedRoots()
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
            val filter = { fileSet: StoredFileSet -> fileSet.entityReference === NonIncrementalMarker }
            fileSets.removeValueIf(file, filter)
            fileSetsByPackagePrefix.removeValueIf("", filter)
          }
          val newRoots = HashSet<VirtualFile>()
          Object2IntMaps.fastForEach(newExcludedRoots) { 
            fileSets.putValue(it.key, ExcludedFileSet.ByFileKind(it.intValue, NonIncrementalMarker))
            newRoots.add(it.key)
          }
          newFileSets.entrySet().forEach { (root, sets) ->
            fileSets.putValues(root, sets)
            for (set in sets) {
              val fileSet = set as? WorkspaceFileSetImpl
              if (fileSet != null && fileSet.data is JvmPackageRootData) {
                fileSetsByPackagePrefix.putValue("", fileSet)
              }
            }
            newRoots.add(root)
          }
          allRoots = newRoots
          upToDate = true
        }
      }
    }
  }
  
  private fun computeCustomExcludedRoots(): Object2IntMap<VirtualFile> {
    val result = Object2IntOpenHashMap<VirtualFile>()

    DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project).forEach { policy -> 
      policy.excludeUrlsForProject.forEach { url ->
        val file = rootFileSupplier.findFileByUrl(url)
        if (file != null && RootFileSupplier.ensureValid(file, project, policy)) {
          result.put(file, WorkspaceFileKindMask.ALL)
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
                result.put(correctedRoot, WorkspaceFileKindMask.EXTERNAL or result.getInt(correctedRoot))
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
              result.put(correctedRoot, WorkspaceFileKindMask.CONTENT or result.getInt(correctedRoot))
            }
          }
        }
      }
    }
    return result
  }

  private fun computeFileSets(): MultiMap<VirtualFile, StoredFileSet> {
    val result = MultiMap.create<VirtualFile, StoredFileSet>()
    AdditionalLibraryRootsProvider.EP_NAME.extensionList.forEach { provider ->
      provider.getAdditionalProjectLibraries(project).forEach { library ->
        fun registerRoots(files: MutableCollection<VirtualFile>, kind: WorkspaceFileKind, fileSetData: WorkspaceFileSetData) {
          files.forEach { root ->
            rootFileSupplier.correctRoot(root, library, provider)?.let {
              result.putValue(it, WorkspaceFileSetImpl(it, kind, NonIncrementalMarker, fileSetData))
            }
          }
        }
        //todo use comparisonId for incremental updates?
        registerRoots(library.sourceRoots, WorkspaceFileKind.EXTERNAL_SOURCE, if (library is JavaSyntheticLibrary) LibrarySourceRootFileSetData(null, "") else DummyWorkspaceFileSetData)
        registerRoots(library.binaryRoots, WorkspaceFileKind.EXTERNAL, if (library is JavaSyntheticLibrary) LibraryRootFileSetData(null, "") else DummyWorkspaceFileSetData)
        library.excludedRoots.forEach {
          result.putValue(it, ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.EXTERNAL, NonIncrementalMarker))
        }
        library.unitedExcludeCondition?.let { condition ->
          val predicate = { file: VirtualFile -> condition.value(file) }
          (library.sourceRoots + library.binaryRoots).forEach { root ->
            result.putValue(root, ExcludedFileSet.ByCondition(root, predicate, NonIncrementalMarker))
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
}

private object NonIncrementalMarker : EntityReference<WorkspaceEntity>() {
  override fun resolve(storage: EntityStorage): WorkspaceEntity? = null
}