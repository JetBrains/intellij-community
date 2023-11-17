// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.platform.diagnostic.telemetry.TelemetryManager
import com.intellij.platform.diagnostic.telemetry.WorkspaceModel
import com.intellij.platform.workspace.storage.EntityReference
import com.intellij.platform.workspace.storage.VersionedStorageChange
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.Query
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import io.opentelemetry.api.metrics.Meter
import java.util.concurrent.atomic.AtomicLong

/**
 * Represents computed information about workspace file sets.
 */
interface WorkspaceFileIndexData {
  fun getFileInfo(file: VirtualFile,
                  honorExclusion: Boolean,
                  includeContentSets: Boolean,
                  includeExternalSets: Boolean,
                  includeExternalSourceSets: Boolean,
                  includeCustomKindSets: Boolean): WorkspaceFileInternalInfo

  fun visitFileSets(visitor: WorkspaceFileSetVisitor)

  /**
   * Notifies the index about changes in files associated with the entities.
   * Must be called inside Write Action, and [updateDirtyEntities] must be called before that Write Action finishes.
   * It may happen that an implementation of [com.intellij.openapi.vfs.newvfs.BulkFileListener] will try to get information about changed
   * files synchronously during the same Write Action, in that case the index should recalculate the data to provide correct results.
   * @param entityReferences references to entities which refer to files which were created, deleted, moved or renamed
   * @param filesToInvalidate files which were deleted or moved to other directories and was referenced from some entities
   */
  fun markDirty(entityReferences: Collection<EntityReference<WorkspaceEntity>>, filesToInvalidate: Collection<VirtualFile>)

  /**
   * Forces the index to update entities marked by [markDirty]. Must be called during execution of the same Write Action as [markDirty].
   */
  fun updateDirtyEntities()

  fun onEntitiesChanged(event: VersionedStorageChange, storageKind: EntityStorageKind)
  
  /**
   * Analyzes changes in VFS and determines how the index must be updated.
   */
  @RequiresReadLock
  fun analyzeVfsChanges(events: List<VFileEvent>): VfsChangeApplier?

  /**
   * Returns package name for [directory] if it's located under source root or classes root of Java library, or `null` otherwise.
   * This is an internal function, plugins must use [com.intellij.openapi.roots.PackageIndex.getPackageNameByDirectory] instead.
   */
  fun getPackageName(dir: VirtualFile): String?

  /**
   * Returns a query producing directories which correspond to [packageName].
   * This is an internal function, plugins must use [com.intellij.openapi.roots.PackageIndex.getDirsByPackageName] instead.
   */
  fun getDirectoriesByPackageName(packageName: String, includeLibrarySources: Boolean): Query<VirtualFile>

  /**
   * Reset caches which cannot be updated incrementally.
   */
  fun resetCustomContributors()

  /**
   * Returns kinds of workspace file sets registered for [url] if the corresponding file doesn't exist. If [url] exists, return an empty set.
   */
  fun getNonExistentFileSetKinds(url: VirtualFileUrl): Set<NonExistingFileSetKind>
  
  fun onLowMemory()
  fun clearPackageDirectoryCache()
  fun resetFileCache()

  companion object {
    val instancesCounter: AtomicLong = AtomicLong()
    val initTimeMs: AtomicLong = AtomicLong()
    val getFileInfoTimeMs: AtomicLong = AtomicLong()
    val visitFileSetsTimeMs: AtomicLong = AtomicLong()
    val processFileSetsTimeMs: AtomicLong = AtomicLong()
    val markDirtyTimeMs: AtomicLong = AtomicLong()
    val updateDirtyEntitiesTimeMs: AtomicLong = AtomicLong()
    val onEntitiesChangedTimeMs: AtomicLong = AtomicLong()
    val getPackageNameTimeMs: AtomicLong = AtomicLong()
    val getDirectoriesByPackageNameTimeMs: AtomicLong = AtomicLong()

    private fun setupOpenTelemetryReporting(meter: Meter): Unit {
      val instancesCountGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.instances.count")
        .ofLongs().buildObserver()
      val initTimeGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.init.ms")
        .ofLongs().buildObserver()
      val getFileInfoTimeGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.getFileInfo.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val visitFileSetsGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.visitFileSets.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val processFileSetsGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.processFileSets.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val markDirtyGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.markDirty.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val updateDirtyEntitiesGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.updateDirtyEntities.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val onEntitiesChangedGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.onEntitiesChanged.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val getPackageNameGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.getPackageName.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()
      val getDirectoriesByPackageNameGauge = meter.gaugeBuilder("workspaceModel.workspaceFileIndexData.getDirectoriesByPackageName.ms")
        .ofLongs().setDescription("Total time spent in method").buildObserver()

      meter.batchCallback(
        {
          instancesCountGauge.record(instancesCounter.get())
          initTimeGauge.record(initTimeMs.get())
          getFileInfoTimeGauge.record(getFileInfoTimeMs.get())
          visitFileSetsGauge.record(visitFileSetsTimeMs.get())
          processFileSetsGauge.record(processFileSetsTimeMs.get())
          markDirtyGauge.record(markDirtyTimeMs.get())
          updateDirtyEntitiesGauge.record(updateDirtyEntitiesTimeMs.get())
          onEntitiesChangedGauge.record(onEntitiesChangedTimeMs.get())
          getPackageNameGauge.record(getPackageNameTimeMs.get())
          getDirectoriesByPackageNameGauge.record(getDirectoriesByPackageNameTimeMs.get())
        },
        instancesCountGauge, initTimeGauge, getFileInfoTimeGauge, visitFileSetsGauge,
        processFileSetsGauge, markDirtyGauge, updateDirtyEntitiesGauge, onEntitiesChangedGauge,
        getPackageNameGauge, getDirectoriesByPackageNameGauge
      )
    }

    init {
      setupOpenTelemetryReporting(TelemetryManager.getMeter(WorkspaceModel))
    }
  }
}