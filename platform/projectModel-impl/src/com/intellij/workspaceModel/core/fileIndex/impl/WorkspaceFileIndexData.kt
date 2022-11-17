// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.impl.PackageDirectoryCacheImpl
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileWithId
import com.intellij.util.CollectionQuery
import com.intellij.util.Query
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.util.containers.MultiMap
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.storage.*
import com.intellij.workspaceModel.storage.bridgeEntities.ContentRootEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ExcludeUrlEntity
import com.intellij.workspaceModel.storage.bridgeEntities.ModuleEntity
import com.intellij.workspaceModel.storage.bridgeEntities.SourceRootEntity
import com.intellij.workspaceModel.storage.url.VirtualFileUrl

internal class WorkspaceFileIndexData(private val contributorList: List<WorkspaceFileIndexContributor<*>>,
                                      private val project: Project,
                                      private val rootFileSupplier: RootFileSupplier) {
  private val contributors = contributorList.groupBy { it.entityClass }
  private val contributorDependencies = contributorList.associateWith { it.dependenciesOnOtherEntities }
  
  /** these maps are accessed under 'Read Action' and updated under 'Write Action' or under 'Read Action' with a special lock in [NonIncrementalContributors.updateIfNeeded] */
  private val fileSets = HashMap<VirtualFile, StoredFileSetCollection>()
  private val fileSetsByPackagePrefix = MultiMap.create<String, WorkspaceFileSetImpl>()
  
  private val packageDirectoryCache: PackageDirectoryCacheImpl
  private val nonIncrementalContributors = NonIncrementalContributors(project, rootFileSupplier)
  private val librariesAndSdkContributors = LibrariesAndSdkContributors(project, rootFileSupplier, fileSets, fileSetsByPackagePrefix)
  private val fileIdWithoutFileSets = ConcurrentBitSet.create()
  private val storeFileSetRegistrar = StoreFileSetsRegistrarImpl()
  private val removeFileSetRegistrar = RemoveFileSetsRegistrarImpl()
  private val fileTypeRegistry = FileTypeRegistry.getInstance()
  private val dirtyEntities = HashSet<EntityReference<WorkspaceEntity>>()
  private val dirtyFiles = HashSet<VirtualFile>()
  @Volatile
  private var hasDirtyEntities = false

  init {
    packageDirectoryCache = PackageDirectoryCacheImpl(::fillPackageDirectories, ::isPackageDirectory)
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
    nonIncrementalContributors.updateIfNeeded(fileSets, fileSetsByPackagePrefix)

    val originalAcceptedKindMask = 
      (if (includeContentSets) WorkspaceFileKindMask.CONTENT else 0) or 
      (if (includeExternalSets) WorkspaceFileKindMask.EXTERNAL_BINARY else 0) or
      (if (includeExternalSourceSets) WorkspaceFileKindMask.EXTERNAL_SOURCE else 0) 
    var acceptedKindsMask = originalAcceptedKindMask 
    var current: VirtualFile? = file
    while (current != null) {
      val fileId = (current as? VirtualFileWithId)?.id ?: -1
      val mayHaveFileSets = fileId < 0 || !fileIdWithoutFileSets.get(fileId)
      if (mayHaveFileSets) {
        val storedFileSets = fileSets[current]
        if (storedFileSets != null) {
          val masks = storedFileSets.computeMasks(acceptedKindsMask shl ACCEPTED_KINDS_MASK_SHIFT, project, honorExclusion, file)
          val storedKindMask = masks and StoredFileSetKindMask.ALL
          acceptedKindsMask = (masks shr ACCEPTED_KINDS_MASK_SHIFT) and WorkspaceFileKindMask.ALL 
          
          if (acceptedKindsMask == 0) {
            return WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
          }
          
          if (storedKindMask and StoredFileSetKindMask.ACCEPTED_FILE_SET != 0) {
            if (storedKindMask == StoredFileSetKindMask.ACCEPTED_FILE_SET) {
              return storedFileSets as WorkspaceFileInternalInfo
            }
            val acceptedFileSets = ArrayList<WorkspaceFileSetImpl>()
            //copy a mutable variable used from lambda to a 'val' to ensure that kotlinc won't wrap it into IntRef
            val currentKindMask = acceptedKindsMask 
            //this should be a rare case so it's ok to use less optimal code here and check 'isUnloaded' again
            storedFileSets.forEach { fileSet ->
              if (fileSet is WorkspaceFileSetImpl && fileSet.kind.toMask() and currentKindMask != 0 && !fileSet.isUnloaded(project)) {
                acceptedFileSets.add(fileSet)
              }
            }
            return if (acceptedFileSets.size > 1) MultipleWorkspaceFileSetsImpl(acceptedFileSets) else acceptedFileSets.first()
          }
        }
        if (fileTypeRegistry.isFileIgnored(current)) {
          return WorkspaceFileInternalInfo.NonWorkspace.IGNORED
        }
        if (fileId >= 0 && storedFileSets == null) {
          fileIdWithoutFileSets.set(fileId)
        }
      }
      current = current.parent
    }
    if (originalAcceptedKindMask != acceptedKindsMask) {
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
    packageDirectoryCache.clear()
  }
  
  private fun isPackageDirectory(dir: VirtualFile, packageName: String): Boolean = getPackageName(dir) == packageName

  private fun fillPackageDirectories(packageName: String, result: MutableList<in VirtualFile>) {
    val addedRoots = HashSet<VirtualFile>()
    for (fileSet in fileSetsByPackagePrefix[packageName]) {
      val root = fileSet.root
      if (root.isDirectory && root.isValid && addedRoots.add(root)) {
        result.add(root)
      }
    }
  }

  fun getPackageName(dir: VirtualFile): String? {
    if (!dir.isDirectory) return null

    val fileSet = when (val info = getFileInfo(dir, true, true, true, true)) {
                    is WorkspaceFileSetWithCustomData<*> -> info.takeIf { it.data is JvmPackageRootData }
                    is MultipleWorkspaceFileSets -> info.find(JvmPackageRootData::class.java)
                    else -> null
                  } ?: return null

    val packagePrefix = (fileSet.data as JvmPackageRootData).packagePrefix
    val packageName = VfsUtilCore.getRelativePath(dir, fileSet.root, '.') 
                      ?: error("${dir.presentableUrl} is not under ${fileSet.root.presentableUrl}")
    return when {
      packagePrefix.isEmpty() -> packageName
      packageName.isEmpty() -> packagePrefix
      else -> "$packagePrefix.$packageName"
    }
  }

  fun getDirectoriesByPackageName(packageName: String, includeLibrarySources: Boolean): Query<VirtualFile> {
    val query = CollectionQuery(packageDirectoryCache.getDirectoriesByPackageName(packageName))
    if (includeLibrarySources) return query
    return query.filtering {
      getFileInfo(it, true, true, true, false) !is WorkspaceFileInternalInfo.NonWorkspace
    }
  }

  fun onLowMemory() {
    packageDirectoryCache.onLowMemory()
  }

  fun clearPackageDirectoryCache() {
    packageDirectoryCache.clear()
  }

  fun unloadModules(entities: List<ModuleEntity>) {
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    entities.forEach { moduleEntity ->
      unregisterFileSets(moduleEntity, ModuleEntity::class.java, storage)
      moduleEntity.contentRoots.forEach { contentRootEntity ->
        unregisterFileSets(contentRootEntity, ContentRootEntity::class.java, storage)
        contentRootEntity.sourceRoots.forEach { unregisterFileSets(it, SourceRootEntity::class.java, storage) }
        contentRootEntity.excludedUrls.forEach { unregisterFileSets(it, ExcludeUrlEntity::class.java, storage) }
      }
    }
  }

  fun loadModules(entities: List<ModuleEntity>) {
    val storage = WorkspaceModel.getInstance(project).entityStorage.current
    entities.forEach { moduleEntity ->
      registerFileSets(moduleEntity, ModuleEntity::class.java, storage)
      moduleEntity.contentRoots.forEach { contentRootEntity ->
        registerFileSets(contentRootEntity, ContentRootEntity::class.java, storage)
        contentRootEntity.sourceRoots.forEach { registerFileSets(it, SourceRootEntity::class.java, storage) }
        contentRootEntity.excludedUrls.forEach { registerFileSets(it, ExcludeUrlEntity::class.java, storage) }
      }
    }
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
      val fileSet = WorkspaceFileSetImpl(root, kind, entity.createReference(), customData ?: DummyWorkspaceFileSetData)
      fileSets.putValue(root, fileSet)
      if (customData is JvmPackageRootData) {
        fileSetsByPackagePrefix.putValue(customData.packagePrefix, fileSet)
      }
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
      if (customData is JvmPackageRootData) {
        fileSetsByPackagePrefix.removeValueIf(customData.packagePrefix) { 
          it is WorkspaceFileSetImpl && isResolvesTo(it.entityReference, entity) 
        }
      }
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

internal fun WorkspaceFileKind.toMask(): Int {
  val mask = when (this) {
    WorkspaceFileKind.CONTENT, WorkspaceFileKind.TEST_CONTENT -> WorkspaceFileKindMask.CONTENT
    WorkspaceFileKind.EXTERNAL -> WorkspaceFileKindMask.EXTERNAL_BINARY
    WorkspaceFileKind.EXTERNAL_SOURCE -> WorkspaceFileKindMask.EXTERNAL_SOURCE
  }
  return mask
}