// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.fileTypes.impl.FileTypeAssocTable
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

internal class WorkspaceFileIndexData(private val contributorList: List<WorkspaceFileIndexContributor<*>>,
                                      private val project: Project,
                                      private val rootFileSupplier: RootFileSupplier) {
  private val contributors = contributorList.groupBy { it.entityClass }
  private val contributorDependencies = contributorList.associateWith { it.dependenciesOnOtherEntities }
  /** this map is accessed under 'Read Action' and updated under 'Write Action' or under 'Read Action' with a special lock in [NonIncrementalContributors.updateIfNeeded] */
  private val fileSets = MultiMap.create<VirtualFile, StoredFileSet>()
  private val nonIncrementalContributors = NonIncrementalContributors(project, rootFileSupplier)
  private val librariesAndSdkContributors = LibrariesAndSdkContributors(project, rootFileSupplier, fileSets)
  private val fileIdWithoutFileSets = ConcurrentBitSet.create()
  private val storeFileSetRegistrar = StoreFileSetsRegistrarImpl()
  private val removeFileSetRegistrar = RemoveFileSetsRegistrarImpl()
  private val fileTypeRegistry = FileTypeRegistry.getInstance()
  private val dirtyEntities = HashSet<EntityReference<WorkspaceEntity>>()
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
    nonIncrementalContributors.updateIfNeeded(fileSets)

    val originalKindMask = 
      (if (includeContentSets) WorkspaceFileKindMask.CONTENT else 0) or 
      (if (includeExternalSets) WorkspaceFileKindMask.EXTERNAL_BINARY else 0) or
      (if (includeExternalSourceSets) WorkspaceFileKindMask.EXTERNAL_SOURCE else 0) 
    var kindMask = originalKindMask 
    var current: VirtualFile? = file
    var currentExclusionMask = 0
    while (current != null) {
      val fileId = (current as? VirtualFileWithId)?.id ?: -1
      val mayHaveFileSets = fileId < 0 || !fileIdWithoutFileSets.get(fileId)
      if (mayHaveFileSets) {
        var firstRelevantFileSet: WorkspaceFileSetImpl? = null
        var additionalRelevantFileSets: MutableList<WorkspaceFileSetImpl>? = null
        val storedFileSets = fileSets.get(current)
        for (info in storedFileSets) {
          when (info) {
            is ExcludedFileSet.ByFileKind -> {
              if (honorExclusion) {
                currentExclusionMask = currentExclusionMask or info.mask
              }
            }
            is ExcludedFileSet.ByPattern -> {
              if (honorExclusion && info.isExcluded(file)) return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
            }
            is ExcludedFileSet.ByCondition -> {
              if (honorExclusion && info.isExcluded(file)) return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
            }
            is WorkspaceFileSetImpl -> {
              if (info.kind.toMask() and kindMask != 0 && !info.isUnloaded(project)) {
                if (firstRelevantFileSet == null) {
                  firstRelevantFileSet = info
                }
                else if (additionalRelevantFileSets == null) {
                  additionalRelevantFileSets = ArrayList<WorkspaceFileSetImpl>(2).apply { add(info) }
                }
                else {
                  additionalRelevantFileSets.add(info)
                }
              }
            }
          }
        }
        if (honorExclusion && currentExclusionMask.inv() and kindMask == 0) {
          return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
        }
        if (firstRelevantFileSet != null) {
          val firstNotExcluded = firstRelevantFileSet.takeIf { it.kind.toMask() and kindMask and currentExclusionMask.inv() != 0 }
          if (firstNotExcluded != null && additionalRelevantFileSets == null) return firstNotExcluded
          
          val additionalNotExcluded = additionalRelevantFileSets?.filter { it.kind.toMask() and kindMask and currentExclusionMask.inv() != 0 }
                                                                ?.takeIf { it.isNotEmpty() }
          if (firstNotExcluded != null || additionalNotExcluded != null) {
            if (additionalNotExcluded == null) return firstNotExcluded!!
            val allNotExcluded = 
              if (firstNotExcluded != null) ArrayList<WorkspaceFileSetImpl>().apply { add(firstNotExcluded); addAll(additionalNotExcluded) }
              else additionalNotExcluded
            val single = allNotExcluded.singleOrNull()
            return single ?: MultipleWorkspaceFileSets(allNotExcluded)
          }
          kindMask = kindMask and firstRelevantFileSet.kind.toMask().inv()
          additionalRelevantFileSets?.forEach {
            kindMask = kindMask and it.kind.toMask().inv()
          }
          if (currentExclusionMask.inv() and kindMask == 0) {
            return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
          }
        }
        if (fileTypeRegistry.isFileIgnored(current)) {
          return WorkspaceFileInternalInfo.NonWorkspace.IGNORED
        }
        if (fileId >= 0 && storedFileSets.isEmpty()) {
          fileIdWithoutFileSets.set(fileId)
        }
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

  private fun <E : WorkspaceEntity> processChangesByContributor(contributor: WorkspaceFileIndexContributor<E>, event: VersionedStorageChange) {
    val removedEntities = LinkedHashSet<E>()
    val addedEntities = LinkedHashSet<E>()
    for (change in event.getChanges(contributor.entityClass)) {
      change.oldEntity?.let { removedEntities.add(it) }
      change.newEntity?.let { addedEntities.add(it) }
    }
    for (dependency in contributorDependencies.getValue(contributor)) {
      @Suppress("UNCHECKED_CAST")
      when (dependency) {
        is DependencyDescription.OnParent<*, *> -> collectEntitiesWithChangedParent(dependency as DependencyDescription.OnParent<E, *>,
                                                                                    event, removedEntities, addedEntities)
        is DependencyDescription.OnChild<*, *> -> collectEntitiesWithChangedChild(dependency as DependencyDescription.OnChild<E, *>,
                                                                                  event, removedEntities, addedEntities)
      }
    }
    
    for (removed in removedEntities) {
      contributor.registerFileSets(removed, removeFileSetRegistrar, event.storageBefore)
    }
    for (added in addedEntities) {
      contributor.registerFileSets(added, storeFileSetRegistrar, event.storageAfter)
    }
  }

  private fun <E : WorkspaceEntity, P : WorkspaceEntity> collectEntitiesWithChangedParent(dependency: DependencyDescription.OnParent<E, P>,
                                                                                          event: VersionedStorageChange,
                                                                                          removedEntities: MutableSet<E>,
                                                                                          addedEntities: MutableSet<E>) {
    event.getChanges(dependency.parentClass).asSequence().filterIsInstance<EntityChange.Replaced<P>>().forEach { change ->
      dependency.childrenGetter(change.oldEntity).toCollection(removedEntities)
      dependency.childrenGetter(change.newEntity).toCollection(addedEntities)
    }
  }

  private fun <E : WorkspaceEntity, C : WorkspaceEntity> collectEntitiesWithChangedChild(dependency: DependencyDescription.OnChild<E, C>,
                                                                                         event: VersionedStorageChange,
                                                                                         removedEntities: LinkedHashSet<E>,
                                                                                         addedEntities: LinkedHashSet<E>) {
    event.getChanges(dependency.childClass).asSequence().forEach { change ->
      change.oldEntity?.let {
        removedEntities.add(dependency.parentGetter(it))
      }
      change.newEntity?.let {
        addedEntities.add(dependency.parentGetter(it))
      }
    }
  }

  fun resetCustomContributors() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    nonIncrementalContributors.resetCache()
    resetFileCache()
  }

  fun markDirty(entities: Collection<EntityReference<WorkspaceEntity>>, files: Collection<VirtualFile>) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    dirtyEntities.addAll(entities)
    dirtyFiles.addAll(files)
    hasDirtyEntities = dirtyEntities.isNotEmpty()
  }

  fun onEntitiesChanged(event: VersionedStorageChange) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    contributorList.forEach { 
      processChangesByContributor(it, event)
    }
    resetFileCache()
  }

  fun updateDirtyEntities() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    for (file in dirtyFiles) {
      fileSets.remove(file)
    }
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    for (reference in dirtyEntities) {
      val entity = reference.resolve(storage) ?: continue
      unregisterFileSets(entity, entity.getEntityInterface(), storage)
      registerFileSets(entity, entity.getEntityInterface(), storage)
    }
    dirtyFiles.clear()
    dirtyEntities.clear()
    resetFileCache()
    hasDirtyEntities = false
  }

  fun resetFileCache() {
    fileIdWithoutFileSets.clear()
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
        fileSets.putValue(excludedRootFile, ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.ALL, entity.createReference()))
      }
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFile,
                                      excludedFrom: WorkspaceFileKind,
                                      entity: WorkspaceEntity) {
      val mask = if (excludedFrom == WorkspaceFileKind.EXTERNAL) WorkspaceFileKindMask.EXTERNAL else excludedFrom.toMask()
      fileSets.putValue(excludedRoot, ExcludedFileSet.ByFileKind(mask, entity.createReference()))
    }

    override fun registerExclusionPatterns(root: VirtualFileUrl,
                                           patterns: List<String>,
                                           entity: WorkspaceEntity) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null && !patterns.isEmpty()) {
        fileSets.putValue(rootFile, ExcludedFileSet.ByPattern(rootFile, patterns, entity.createReference()))
      }
    }

    override fun registerExclusionCondition(root: VirtualFile,
                                            condition: (VirtualFile) -> Boolean,
                                            entity: WorkspaceEntity) {
      fileSets.putValue(root, ExcludedFileSet.ByCondition(root, condition, entity.createReference()))
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
      fileSets.removeValueIf(root) { it is WorkspaceFileSetImpl && isResolvesTo(it.entityReference, entity) }
    }

    private fun isResolvesTo(reference: EntityReference<*>, entity: WorkspaceEntity) = reference == entity.createReference<WorkspaceEntity>() 

    override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
      val excludedRootFile = rootFileSupplier.findFile(excludedRoot)
      if (excludedRootFile != null) {
        //todo compare origins, not just their entities?
        fileSets.removeValueIf(excludedRootFile) { it is ExcludedFileSet && isResolvesTo(it.entityReference, entity) }
      }
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFile,
                                      excludedFrom: WorkspaceFileKind,
                                      entity: WorkspaceEntity) {
      fileSets.removeValueIf(excludedRoot) { it is ExcludedFileSet && isResolvesTo(it.entityReference, entity) }
    }

    override fun registerExclusionPatterns(root: VirtualFileUrl,
                                           patterns: List<String>,
                                           entity: WorkspaceEntity) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null) {
        fileSets.removeValueIf(rootFile) { it is ExcludedFileSet.ByPattern && isResolvesTo(it.entityReference, entity) }
      }
    }

    override fun registerExclusionCondition(root: VirtualFile,
                                            condition: (VirtualFile) -> Boolean,
                                            entity: WorkspaceEntity) {
      fileSets.removeValueIf(root) { it is ExcludedFileSet.ByCondition && isResolvesTo(it.entityReference, entity) }
    }
  }
}

internal class WorkspaceFileSetImpl(override val root: VirtualFile,
                                    override val kind: WorkspaceFileKind,
                                    override val entityReference: EntityReference<WorkspaceEntity>,
                                    override val data: WorkspaceFileSetData)
  : WorkspaceFileSetWithCustomData<WorkspaceFileSetData>, StoredFileSet, WorkspaceFileInternalInfo {
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

/**
 * Base interface for file sets stored in [WorkspaceFileIndexData]. 
 */
internal sealed interface StoredFileSet {
  val entityReference: EntityReference<WorkspaceEntity>
}

internal sealed interface ExcludedFileSet : StoredFileSet {
  class ByFileKind(@MagicConstant(flagsFromClass = WorkspaceFileKindMask::class) val mask: Int,
                   override val entityReference: EntityReference<WorkspaceEntity>) : ExcludedFileSet
  
  class ByPattern(val root: VirtualFile, patterns: List<String>,
                  override val entityReference: EntityReference<WorkspaceEntity>) : ExcludedFileSet {
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
  
  class ByCondition(val root: VirtualFile, val condition: (VirtualFile) -> Boolean,
                    override val entityReference: EntityReference<WorkspaceEntity>) : ExcludedFileSet {
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