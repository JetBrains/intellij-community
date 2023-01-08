// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

internal class WorkspaceFileIndexData(contributorList: List<WorkspaceFileIndexContributor<*>>,
                                      private val project: Project,
                                      private val rootFileSupplier: RootFileSupplier) {
  private val contributors = contributorList.groupBy { it.entityClass }
  private val contributorsWithDependency = contributorList
    .flatMap { contributor -> contributor.dependenciesOnParentEntities.map { it to contributor } }
    .groupBy({ it.second.entityClass }, { it.first to it.second })
  private val fileSets = MultiMap.create<VirtualFile, WorkspaceFileSetImpl>()
  private val excludedRoots = MultiMap.create<VirtualFile, ExcludedRootData>()
  private val exclusionPatternsByRoot = MultiMap.create<VirtualFile, ExclusionPatterns>()
  private val exclusionConditionsByRoot = MultiMap.create<VirtualFile, ExclusionCondition>()
  private val customExcludedRootContributors = CustomExcludedRootContributors(project, rootFileSupplier)
  private val syntheticLibraryContributors = SyntheticLibraryContributors(project, rootFileSupplier)
  private val librariesAndSdkContributors = LibrariesAndSdkContributors(project, rootFileSupplier, fileSets, excludedRoots)
  private val storeFileSetRegistrar = StoreFileSetsRegistrarImpl()
  private val removeFileSetRegistrar = RemoveFileSetsRegistrarImpl()
  private val fileTypeRegistry = FileTypeRegistry.getInstance()
  private val dirtyEntities = HashSet<WorkspaceEntity>()
  private val dirtyFiles = HashSet<VirtualFile>()
  @Volatile
  private var hasDirtyEntities = false

  init {
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    contributors.keys.forEach { entityClass ->
      storage.entities(entityClass).forEach {
        registerFileSets(it, entityClass, storage)
      }
    }
    syntheticLibraryContributors.registerFileSets(fileSets, excludedRoots, exclusionConditionsByRoot)
    librariesAndSdkContributors.registerFileSets()
  }

  fun getFileInfo(file: VirtualFile,
                  honorExclusion: Boolean,
                  includeContentSets: Boolean,
                  includeExternalSets: Boolean,
                  includeExternalSourceSets: Boolean): WorkspaceFileInternalInfo {
    if (!file.isValid) return WorkspaceFileInternalInfo.NonWorkspace.INVALID
    if (hasDirtyEntities && ApplicationManager.getApplication().isWriteAccessAllowed) {
      updateDirtyEntities()
    }

    val originalKindMask = 
      (if (includeContentSets) WorkspaceFileKindMask.CONTENT else 0) or 
      (if (includeExternalSets) WorkspaceFileKindMask.EXTERNAL_BINARY else 0) or
      (if (includeExternalSourceSets) WorkspaceFileKindMask.EXTERNAL_SOURCE else 0) 
    var kindMask = originalKindMask 
    var current: VirtualFile? = file
    var currentExclusionMask = 0
    while (current != null) {
      if (honorExclusion) {
        if (excludedRoots.containsKey(current)) {
          val excludedRootData = excludedRoots[current]
          excludedRootData.forEach { 
            currentExclusionMask = currentExclusionMask or it.mask
          }
          if (currentExclusionMask.inv() and kindMask == 0) {
            return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
          }
        }
        val exclusionPatterns = exclusionPatternsByRoot[current]
        if (exclusionPatterns.isNotEmpty()) {
          if (exclusionPatterns.any { it.isExcluded(file) }) {
            return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
          }
        }
        if (exclusionConditionsByRoot[current].any { it.isExcluded(file) }) {
          return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
        }
        val mask = customExcludedRootContributors.getCustomExcludedRootMask(current)
        if (mask != 0) {
          currentExclusionMask = currentExclusionMask or mask
          if (currentExclusionMask.inv() and kindMask == 0) {
            return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
          }
        }
      }
      val fileSets = fileSets[current]
      if (fileSets.isNotEmpty()) {
        val relevant = fileSets.filter { it.kind.toMask() and kindMask != 0 && !it.isUnloaded(project) }
        if (relevant.isNotEmpty()) {
          val notExcluded = relevant.filter { it.kind.toMask() and kindMask and currentExclusionMask.inv() != 0 }
          if (notExcluded.isNotEmpty()) {
            val single = notExcluded.singleOrNull()
            return single ?: MultipleWorkspaceFileSets(notExcluded)
          }
          relevant.forEach { 
            kindMask = kindMask and it.kind.toMask().inv()
          }
          if (currentExclusionMask.inv() and kindMask == 0) {
            return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
          }
        }
      }
      if (fileTypeRegistry.isFileIgnored(current)) {
        return WorkspaceFileInternalInfo.NonWorkspace.IGNORED
      }
      current = current.parent
    }
    if (originalKindMask != kindMask || currentExclusionMask != 0) {
      return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
    }
    return WorkspaceFileInternalInfo.NonWorkspace.NOT_UNDER_ROOTS
  }

  private fun <E : WorkspaceEntity> getContributors(entityClass: Class<out E>): List<WorkspaceFileIndexContributor<E>> {
    val value = contributors[entityClass] ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    return value as List<WorkspaceFileIndexContributor<E>>
  }

  private fun <E : WorkspaceEntity> registerFileSets(entity: E, entityClass: Class<out E>, storage: EntityStorage) {
    getContributors(entityClass).forEach { contributor ->
      contributor.registerFileSets(entity, storeFileSetRegistrar, storage)
    }
  }

  private fun <E : WorkspaceEntity> unregisterFileSets(entity: E, entityClass: Class<out E>, storage: EntityStorage) {
    getContributors(entityClass).forEach { contributor ->
      contributor.registerFileSets(entity, removeFileSetRegistrar, storage)
    }
  }

  private fun <E : WorkspaceEntity> processChanges(event: VersionedStorageChange, entityClass: Class<out E>) {
    val changes = event.getChanges(entityClass)
    changes.forEach { change -> processChange(change, entityClass, event) }
    collectChangesFromParents(event, entityClass)
  }

  private fun <C : WorkspaceEntity> collectChangesFromParents(event: VersionedStorageChange, childClass: Class<C>) {
    val contributors = contributorsWithDependency[childClass] ?: return
    contributors.forEach { (dependency, contributor) ->
      @Suppress("UNCHECKED_CAST")
      processChangeInDependency(dependency as DependencyOnParentEntity<C, WorkspaceEntity>,
                                contributor as WorkspaceFileIndexContributor<C>, event)
    }
  }

  private fun <E : WorkspaceEntity> processChange(change: EntityChange<out E>, entityClass: Class<out E>, event: VersionedStorageChange) {
    change.oldEntity?.let { unregisterFileSets(it, entityClass, event.storageBefore) }
    change.newEntity?.let { registerFileSets(it, entityClass, event.storageAfter) }
  }

  private fun <C : WorkspaceEntity, P : WorkspaceEntity> processChangeInDependency(dependency: DependencyOnParentEntity<C, P>,
                                                                                   contributor: WorkspaceFileIndexContributor<C>,
                                                                                   event: VersionedStorageChange) {
    event.getChanges(dependency.parentClass).asSequence().filterIsInstance<EntityChange.Replaced<P>>().forEach { change ->
      dependency.childrenGetter(change.oldEntity).forEach {
        contributor.registerFileSets(it, removeFileSetRegistrar, event.storageBefore)
      }
      dependency.childrenGetter(change.newEntity).forEach {
        contributor.registerFileSets(it, storeFileSetRegistrar, event.storageAfter)
      }
    }
  }

  fun resetCustomContributors() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    customExcludedRootContributors.resetCache()
    syntheticLibraryContributors.unregisterFileSets(fileSets, excludedRoots, exclusionConditionsByRoot)
    syntheticLibraryContributors.registerFileSets(fileSets, excludedRoots, exclusionConditionsByRoot)
  }

  fun markDirty(entities: Collection<WorkspaceEntity>, files: Collection<VirtualFile>) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    dirtyEntities.addAll(entities)
    dirtyFiles.addAll(files)
    hasDirtyEntities = dirtyEntities.isNotEmpty()
  }

  fun onEntitiesChanged(event: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    contributors.keys.forEach { entityClass ->
      processChanges(event, entityClass)
    }
  }

  fun updateDirtyEntities() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    for (file in dirtyFiles) {
      fileSets.remove(file)
      excludedRoots.remove(file)
      exclusionPatternsByRoot.remove(file)
      exclusionPatternsByRoot.remove(file)
    }
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    for (entity in dirtyEntities) {
      unregisterFileSets(entity, entity.getEntityInterface(), storage)
      registerFileSets(entity, entity.getEntityInterface(), storage)
    }
    dirtyFiles.clear()
    dirtyEntities.clear()
    hasDirtyEntities = false
  }

  private inner class StoreFileSetsRegistrarImpl : WorkspaceFileSetRegistrar {
    override fun registerFileSet(root: VirtualFileUrl,
                                 kind: WorkspaceFileKind,
                                 entity: WorkspaceEntity,
                                 customData: WorkspaceFileSetData?) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null) {
        registerFileSet(rootFile, kind, entity, customData)
      }
    }

    override fun registerFileSet(root: VirtualFile,
                                 kind: WorkspaceFileKind,
                                 entity: WorkspaceEntity,
                                 customData: WorkspaceFileSetData?) {
      fileSets.putValue(root, WorkspaceFileSetImpl(root, kind, entity.createReference(), customData ?: DummyWorkspaceFileSetData))
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
      val excludedRootFile = rootFileSupplier.findFile(excludedRoot)
      if (excludedRootFile != null) {
        excludedRoots.putValue(excludedRootFile, ExcludedRootData(WorkspaceFileKindMask.ALL, entity.createReference()))
      }
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFile,
                                      excludedFrom: WorkspaceFileKind,
                                      entity: WorkspaceEntity) {
      val mask = if (excludedFrom == WorkspaceFileKind.EXTERNAL) WorkspaceFileKindMask.EXTERNAL else excludedFrom.toMask()
      excludedRoots.putValue(excludedRoot, ExcludedRootData(mask, entity.createReference()))
    }

    override fun registerExclusionPatterns(root: VirtualFileUrl,
                                           patterns: List<String>,
                                           entity: WorkspaceEntity) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null && !patterns.isEmpty()) {
        exclusionPatternsByRoot.putValue(rootFile, ExclusionPatterns(rootFile, patterns, entity.createReference()))
      }
    }

    override fun registerExclusionCondition(root: VirtualFile,
                                            condition: (VirtualFile) -> Boolean,
                                            entity: WorkspaceEntity) {
      exclusionConditionsByRoot.putValue(root, ExclusionCondition(root, condition, entity.createReference()))
    }
  }

  private inner class RemoveFileSetsRegistrarImpl : WorkspaceFileSetRegistrar {
    override fun registerFileSet(root: VirtualFileUrl,
                                 kind: WorkspaceFileKind,
                                 entity: WorkspaceEntity,
                                 customData: WorkspaceFileSetData?) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null) {
        registerFileSet(rootFile, kind, entity, customData)
      }
    }

    override fun registerFileSet(root: VirtualFile,
                                 kind: WorkspaceFileKind,
                                 entity: WorkspaceEntity,
                                 customData: WorkspaceFileSetData?) {
      fileSets.removeValueIf(root) { isResolvesTo(it.entityReference, entity) }
    }

    private fun isResolvesTo(reference: EntityReference<*>, entity: WorkspaceEntity) = reference == entity.createReference<WorkspaceEntity>() 

    override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
      val excludedRootFile = rootFileSupplier.findFile(excludedRoot)
      if (excludedRootFile != null) {
        //todo compare origins, not just their entities?
        excludedRoots.removeValueIf(excludedRootFile) { isResolvesTo(it.entityReference, entity) }
      }
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFile,
                                      excludedFrom: WorkspaceFileKind,
                                      entity: WorkspaceEntity) {
      excludedRoots.removeValueIf(excludedRoot) { isResolvesTo(it.entityReference, entity) }
    }

    override fun registerExclusionPatterns(root: VirtualFileUrl,
                                           patterns: List<String>,
                                           entity: WorkspaceEntity) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null) {
        exclusionPatternsByRoot.removeValueIf(rootFile) { isResolvesTo(it.entityReference, entity) }
      }
    }

    override fun registerExclusionCondition(root: VirtualFile,
                                            condition: (VirtualFile) -> Boolean,
                                            entity: WorkspaceEntity) {
      exclusionConditionsByRoot.removeValueIf(root) { isResolvesTo(it.entityReference, entity) }
    }
  }
}

internal class WorkspaceFileSetImpl(override val root: VirtualFile,
                                    override val kind: WorkspaceFileKind,
                                    val entityReference: EntityReference<WorkspaceEntity>,
                                    override val data: WorkspaceFileSetData) : WorkspaceFileSetWithCustomData<WorkspaceFileSetData>, WorkspaceFileInternalInfo {
  fun isUnloaded(project: Project): Boolean {
    return (data as? UnloadableFileSetData)?.isUnloaded(project) == true
  }
}

internal class MultipleWorkspaceFileSets(val fileSets: List<WorkspaceFileSetImpl>) : WorkspaceFileInternalInfo

internal object DummyWorkspaceFileSetData : WorkspaceFileSetData

internal object WorkspaceFileKindMask {
  const val CONTENT = 1
  const val EXTERNAL_BINARY = 2
  const val EXTERNAL_SOURCE = 4
  const val EXTERNAL = EXTERNAL_SOURCE or EXTERNAL_BINARY
  const val ALL = CONTENT or EXTERNAL
}

internal class ExcludedRootData(@MagicConstant(flagsFromClass = WorkspaceFileKindMask::class) val mask: Int,
                                val entityReference: EntityReference<WorkspaceEntity>)

private class ExclusionPatterns(val root: VirtualFile, patterns: List<String>,
                                val entityReference: EntityReference<WorkspaceEntity>) {
  val table = FileTypeAssocTable<Boolean>()

  init {
    for (pattern in patterns) {
      table.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(pattern), true)
    }
  }

  fun isExcluded(file: VirtualFile): Boolean {
    var current = file
    while (current != root) {
      if (table.findAssociatedFileType(current.nameSequence) != null) {
        return true
      }
      current = current.parent
    }
    return false
  }
}

internal class ExclusionCondition(val root: VirtualFile, val condition: (VirtualFile) -> Boolean,
                                  val entityReference: EntityReference<WorkspaceEntity>) {
  fun isExcluded(file: VirtualFile): Boolean {
    var current = file
    while (current != root) {
      if (condition(current)) {
        return true
      }
      current = current.parent
    }
    
    return condition(root)
  }
}

internal inline fun <K, V> MultiMap<K, V>.removeValueIf(key: K, crossinline valuePredicate: (V) -> Boolean) {
  val collection = get(key)
  collection.removeIf { valuePredicate(it) }
  if (collection.isEmpty()) {
    remove(key)
  }
}

private fun WorkspaceFileKind.toMask(): Int {
  val mask = when (this) {
    WorkspaceFileKind.CONTENT, WorkspaceFileKind.TEST_CONTENT -> WorkspaceFileKindMask.CONTENT
    WorkspaceFileKind.EXTERNAL -> WorkspaceFileKindMask.EXTERNAL_BINARY
    WorkspaceFileKind.EXTERNAL_SOURCE -> WorkspaceFileKindMask.EXTERNAL_SOURCE
  }
  return mask
}