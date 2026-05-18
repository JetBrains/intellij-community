// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.diagnostic.PluginException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.AdditionalLibraryRootsProvider
import com.intellij.openapi.roots.JavaSyntheticLibrary
import com.intellij.openapi.roots.ModuleRootManager
import com.intellij.openapi.roots.ModuleRootModel
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.SyntheticLibrary
import com.intellij.openapi.roots.impl.DirectoryIndexExcludePolicy
import com.intellij.openapi.roots.impl.RootFileValidityChecker
import com.intellij.openapi.util.Condition
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.diagnostic.telemetry.helpers.MillisecondsMeasurer
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.asSafely
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSet
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetData
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition
import com.intellij.workspaceModel.ide.impl.workspaceModelMetrics
import it.unimi.dsi.fastutil.objects.Object2IntMap
import it.unimi.dsi.fastutil.objects.Object2IntMaps
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap
import java.util.concurrent.ConcurrentHashMap
import kotlin.system.measureTimeMillis

/**
 * Integrates data provided by [DirectoryIndexExcludePolicy] and [AdditionalLibraryRootsProvider] extensions
 * to [com.intellij.workspaceModel.core.fileIndex.WorkspaceFileIndex].
 * Since these extensions don't support incremental updates, the corresponding parts of the index are rebuilt from scratch
 * in the [updateIfNeeded] method after [resetCache] was called.
 */
internal class NonIncrementalContributors(private val project: Project) {
  private var allRoots = emptySet<VirtualFile>()
  private var excludedUrls = emptySet<VirtualFileUrl>()
  @Volatile
  private var upToDate = false
  private val lock = Any()

  @RequiresReadLock
  fun updateIfNeeded(
    fileSets: MutableMap<VirtualFile, StoredFileSetCollection>,
    fileSetsByPackagePrefix: PackagePrefixStorage,
    nonExistingFilesRegistry: NonExistingWorkspaceRootsRegistry,
  ) {
    if (upToDate) return

    synchronized(lock) {
      if (upToDate) return

      val excludeRootsComputationTime: Long
      val fileSetsComputationTime: Long
      val updateTime = measureTimeMillis {
        val newExcludedRoots: Object2IntMap<VirtualFile>
        val newExcludedUrls: Set<VirtualFileUrl>
        excludeRootsComputationTime = measureTimeMillis {
          val result = computeCustomExcludedRoots()
          newExcludedRoots = result.first
          newExcludedUrls = result.second
        }
        val newFileSets: Map<VirtualFile, StoredFileSetCollection>
        fileSetsComputationTime = measureTimeMillis {
          newFileSets = computeFileSets()
        }

        /*
        This function requires 'read access' only, so multiple thread may execute it in parallel, and we need to synchronize write access to
        the shared fileSets* maps. Modifications here are performed under exclusive lock while holding the global 'Read Action', and other
        modifications are performed under the global 'Write Action' lock. The maps are always read under 'Read Action', and this function is
        called under the same 'Read Action' before accessing the map, and `upToDate` flag will not be reset before that read action finishes,
        so read/write conflicts shouldn't happen.
        */
        allRoots.forEach { file ->
          fileSets.removeValueIf(file) { fileSet: StoredFileSet -> fileSet.entityPointer === NonIncrementalMarker }
          fileSetsByPackagePrefix.removeByPrefixAndPointer("", NonIncrementalMarker)
        }
        excludedUrls.forEach {
          nonExistingFilesRegistry.unregisterUrl(it, NonIncrementalMarker, EntityStorageKind.MAIN)
        }
        val newRoots = HashSet<VirtualFile>()
        Object2IntMaps.fastForEach(newExcludedRoots) {
          fileSets.putValue(it.key, ExcludedFileSet.ByFileKind(it.key, it.intValue, NonIncrementalMarker))
          newRoots.add(it.key)
        }
        newExcludedUrls.forEach {
          nonExistingFilesRegistry.registerUrl(it, NonIncrementalMarker, EntityStorageKind.MAIN,
                                               NonExistingFileSetKind.EXCLUDED_FROM_CONTENT, recursive = true)
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
      totalUpdateTimeMs.duration.addAndGet(updateTime)
      excludeRootsComputationTimeMs.duration.addAndGet(excludeRootsComputationTime)
      fileSetsComputationTimeMs.duration.addAndGet(fileSetsComputationTime)
    }
  }

  private fun computeCustomExcludedRoots(): Pair<Object2IntMap<VirtualFile>, Set<VirtualFileUrl>> {
    val virtualFileUrlManager = WorkspaceModel.getInstance(project).getVirtualFileUrlManager()
    val excludedFiles = Object2IntOpenHashMap<VirtualFile>()
    val excludedUrls = HashSet<VirtualFileUrl>()

    DirectoryIndexExcludePolicy.EP_NAME.getExtensions(project).forEach { policy ->
      policy.excludeUrlsForProject.forEach { url ->
        val file = VirtualFileManager.getInstance().findFileByUrl(url)
        if (file != null) {
          if (RootFileValidityChecker.ensureValid(file, project, policy)) {
            excludedFiles.put(file, WorkspaceFileKindMask.ALL)
          }
        }
        else {
          excludedUrls.add(virtualFileUrlManager.getOrCreateFromUrl(url))
        }
      }
      @Suppress("DEPRECATION", "removal")
      policy.excludeSdkRootsStrategy?.let { strategy ->
        val sdks = ModuleManager.getInstance(project).modules.mapNotNullTo(HashSet()) { ModuleRootManager.getInstance(it).sdk }
        val sdkClasses = sdks.flatMapTo(HashSet()) { it.rootProvider.getFiles(OrderRootType.CLASSES).asList() }
        sdks.forEach { sdk ->
          strategy.`fun`(sdk).forEach { root ->
            if (root !in sdkClasses) {
              val correctedRoot = RootFileValidityChecker.correctRoot(root, sdk, policy)
              if (correctedRoot != null) {
                excludedFiles.put(correctedRoot, WorkspaceFileKindMask.EXTERNAL or excludedFiles.getInt(correctedRoot))
              }
            }
          }
        }
      }
      val hasExcludeRootsForModule = overrideMap.computeIfAbsent(policy.javaClass.name) {
        runCatching {
          policy.javaClass.getMethod("getExcludeRootsForModule", ModuleRootModel::class.java).declaringClass != DirectoryIndexExcludePolicy::class.java
        }.getOrDefault(true)
      }
      if (hasExcludeRootsForModule) {
        ModuleManager.getInstance(project).modules.forEach { module ->
          @Suppress("DEPRECATION", "removal")
          policy.getExcludeRootsForModule(ModuleRootManager.getInstance(module)).forEach { pointer ->
            val file = pointer.file
            if (file != null) {
              val correctedRoot = RootFileValidityChecker.correctRoot(file, module, policy)
              if (correctedRoot != null) {
                excludedFiles.put(correctedRoot, WorkspaceFileKindMask.CONTENT or excludedFiles.getInt(correctedRoot))
              }
            }
            else {
              excludedUrls.add(virtualFileUrlManager.getOrCreateFromUrl(pointer.url))
            }
          }
        }
      }
    }
    return excludedFiles to excludedUrls
  }

  private fun computeFileSets(): Map<VirtualFile, StoredFileSetCollection> {
    val result = HashMap<VirtualFile, StoredFileSetCollection>()
    AdditionalLibraryRootsProvider.EP_NAME.extensionList.forEach { provider ->
      for (library in provider.getAdditionalProjectLibraries(project)) {
        if (library == null) {
          PluginException.logPluginError(
            LOG, "The result of AdditionalLibraryRootsProvider.getAdditionalProjectLibraries on ${provider.javaClass} includes 'null' item",
            null, provider.javaClass
          )
          continue
        }

        fun registerRoots(files: Collection<VirtualFile>, kind: WorkspaceFileKind, fileSetData: WorkspaceFileSetData) {
          files.forEach { root ->
            RootFileValidityChecker.correctRoot(root, library, provider)?.let {
              result.putValue(it, WorkspaceFileSetImpl(it, kind, NonIncrementalMarker, EntityStorageKind.MAIN, fileSetData))
            }
          }
        }
        //todo use comparisonId for incremental updates?
        val sourceRoots = checkNotNull(library.sourceRoots, "getSourceRoots()", library) ?: emptyList<VirtualFile>()
        registerRoots(sourceRoots, WorkspaceFileKind.EXTERNAL_SOURCE, if (library is JavaSyntheticLibrary) LibrarySourceRootFileSetData(null) else SyntheticLibrarySourceRootData)
        val binaryRoots = checkNotNull(library.binaryRoots, "getBinaryRoots()", library) ?: emptyList<VirtualFile>()
        registerRoots(binaryRoots, WorkspaceFileKind.EXTERNAL, if (library is JavaSyntheticLibrary) LibraryRootFileSetData(null) else DummyWorkspaceFileSetData)
        val excludedRoots = checkNotNull(library.excludedRoots, "getExcludedRoots()", library) ?: emptySet<VirtualFile>()
        excludedRoots.forEach {
          result.putValue(it, ExcludedFileSet.ByFileKind(it, WorkspaceFileKindMask.EXTERNAL, NonIncrementalMarker))
        }
        library.unitedExcludeCondition?.let { condition ->
          val exclusionCondition = UnifiedLibraryExclusionCondition(library, condition)
          (library.sourceRoots + library.binaryRoots).forEach { root ->
            result.putValue(root, ExcludedFileSet.ByCondition(root, exclusionCondition, NonIncrementalMarker, EntityStorageKind.MAIN))
          }
        }
      }
    }
    return result
  }

  private fun <T> checkNotNull(result: T?, methodName: String, library: SyntheticLibrary): T? {
    if (result == null) {
      PluginException.logPluginError(
        LOG, "Contract violation: SyntheticLibrary::$methodName is marked as '@NotNull', but its implementation in $library returned 'null'",
        null, library.javaClass
      )
    }
    return result
  }

  @RequiresWriteLock
  fun resetCache() {
    upToDate = false
  }

  companion object {
    private val totalUpdateTimeMs = MillisecondsMeasurer()
    private val excludeRootsComputationTimeMs = MillisecondsMeasurer()
    private val fileSetsComputationTimeMs = MillisecondsMeasurer()
    private val overrideMap = ConcurrentHashMap<String, Boolean>()

    internal fun isFromAdditionalLibraryRootsProvider(fileSet: WorkspaceFileSet): Boolean {
      return fileSet.asSafely<WorkspaceFileSetImpl>()?.entityPointer is NonIncrementalMarker
    }

    fun isPlaceholderReference(entityPointer: EntityPointer<WorkspaceEntity>): Boolean = entityPointer is NonIncrementalMarker

    private val LOG = logger<NonIncrementalContributors>()

    init {
      val meter = workspaceModelMetrics.meter
      val nonIncrementalContributorsUpdateCounter = meter.counterBuilder("workspaceModel.non.incremental.contributors.total.update.time.ms").buildObserver()
      val excludeRootsComputationCounter = meter.counterBuilder("workspaceModel.non.incremental.contributors.exclude.roots.computation.time.ms").buildObserver()
      val fileSetsComputationCounter = meter.counterBuilder("workspaceModel.non.incremental.contributors.file.sets.computation.time.ms").buildObserver()
      meter.batchCallback(
        {
          nonIncrementalContributorsUpdateCounter.record(totalUpdateTimeMs.asMilliseconds())
          excludeRootsComputationCounter.record(excludeRootsComputationTimeMs.asMilliseconds())
          fileSetsComputationCounter.record(fileSetsComputationTimeMs.asMilliseconds())
        },
        nonIncrementalContributorsUpdateCounter, excludeRootsComputationCounter, fileSetsComputationCounter
      )
    }
  }
}

private object SyntheticLibrarySourceRootData : ModuleOrLibrarySourceRootData

private class UnifiedLibraryExclusionCondition(
  private val library: SyntheticLibrary,
  private val condition: Condition<in VirtualFile>,
) : WorkspaceFileSetExclusionCondition {
  override fun shouldExclude(file: VirtualFile): Boolean = condition.value(file)
  override fun equals(other: Any?): Boolean = other is UnifiedLibraryExclusionCondition && library == other.library
  override fun hashCode(): Int = library.hashCode()
}

private object NonIncrementalMarker : EntityPointer<WorkspaceEntity> {
  override fun resolve(storage: EntityStorage): WorkspaceEntity? = null
  override fun isPointerTo(entity: WorkspaceEntity): Boolean = false
  override fun isPointerToEntityOfSameTypeAs(other: EntityPointer<*>): Boolean = other === this
  override fun classHashcode(): Int = 0
}
