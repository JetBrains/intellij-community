// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.SingleFileSourcesTracker
import com.intellij.openapi.roots.impl.PackageDirectoryCacheImpl
import com.intellij.openapi.roots.impl.RootFileSupplier
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.util.CollectionQuery
import com.intellij.util.Query
import com.intellij.util.SlowOperations
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.workspaceModel.core.fileIndex.*
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl

internal class WorkspaceFileIndexDataImpl(private val contributorList: List<WorkspaceFileIndexContributor<*>>,
                                          private val project: Project,
                                          private val rootFileSupplier: RootFileSupplier): WorkspaceFileIndexData {
  private val contributors = contributorList.filter { it.storageKind == EntityStorageKind.MAIN }.groupBy { it.entityClass }
  private val contributorsForUnloaded = contributorList.filter { it.storageKind == EntityStorageKind.UNLOADED }.groupBy { it.entityClass }
  private val contributorDependencies = contributorList.associateWith { it.dependenciesOnOtherEntities }
  
  /** these maps are accessed under 'Read Action' and updated under 'Write Action' or under 'Read Action' with a special lock in [NonIncrementalContributors.updateIfNeeded] */
  private val fileSets = HashMap<VirtualFile, StoredFileSetCollection>()
  private val fileSetsByPackagePrefix = PackagePrefixStorage()
  
  private val nonExistingFilesRegistry = NonExistingWorkspaceRootsRegistry(project, this)
  
  private val packageDirectoryCache: PackageDirectoryCacheImpl
  private val nonIncrementalContributors = NonIncrementalContributors(project, rootFileSupplier)
  private val librariesAndSdkContributors = LibrariesAndSdkContributors(project, rootFileSupplier, fileSets, fileSetsByPackagePrefix)
  private val fileIdWithoutFileSets = ConcurrentBitSet.create()
  private val fileTypeRegistry = FileTypeRegistry.getInstance()
  private val dirtyEntities = HashSet<EntityReference<WorkspaceEntity>>()
  private val dirtyFiles = HashSet<VirtualFile>()
  private val singleFileSourcesTracker = SingleFileSourcesTracker.getInstance(project)
  @Volatile
  private var hasDirtyEntities = false

  init {
    packageDirectoryCache = PackageDirectoryCacheImpl(::fillPackageDirectories, ::isPackageDirectory)
    registerAllEntities(EntityStorageKind.MAIN)
    registerAllEntities(EntityStorageKind.UNLOADED)
    librariesAndSdkContributors.registerFileSets()
  }

  private fun registerAllEntities(storageKind: EntityStorageKind) {
    val (storage, contributors) = when (storageKind) {
      EntityStorageKind.MAIN -> WorkspaceModel.getInstance(project).currentSnapshot to contributors
      EntityStorageKind.UNLOADED -> WorkspaceModel.getInstance(project).currentSnapshotOfUnloadedEntities to contributorsForUnloaded 
    }
    val registrar = StoreFileSetsRegistrarImpl(storageKind)
    contributors.keys.forEach { entityClass ->
      storage.entities(entityClass).forEach {
        registerFileSets(it, entityClass, storage, storageKind, registrar)
      }
    }
  }

  override fun getFileInfo(file: VirtualFile,
                           honorExclusion: Boolean,
                           includeContentSets: Boolean,
                           includeExternalSets: Boolean,
                           includeExternalSourceSets: Boolean): WorkspaceFileInternalInfo {
    if (!file.isValid) return WorkspaceFileInternalInfo.NonWorkspace.INVALID
    if (file.fileSystem is NonPhysicalFileSystem && file.parent == null) {
      return WorkspaceFileInternalInfo.NonWorkspace.NOT_UNDER_ROOTS
    }
    ensureIsUpToDate()

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
            //this should be a rare case, so it's ok to use less optimal code here and check 'isUnloaded' again
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

  private fun ensureIsUpToDate() {
    SlowOperations.assertSlowOperationsAreAllowed()
    if (hasDirtyEntities && ApplicationManager.getApplication().isWriteAccessAllowed) {
      updateDirtyEntities()
    }
    ApplicationManager.getApplication().assertReadAccessAllowed()
    nonIncrementalContributors.updateIfNeeded(fileSets, fileSetsByPackagePrefix, nonExistingFilesRegistry)
  }

  override fun visitFileSets(visitor: WorkspaceFileSetVisitor) {
    ensureIsUpToDate()
    for (value in fileSets.values) {
      value.forEach { storedFileSet ->
        when (storedFileSet) {
          is WorkspaceFileSetImpl -> {
            visitor.visitIncludedRoot(storedFileSet)
          }
          is ExcludedFileSet -> Unit
        }
      }
    }
  }

  fun processFileSets(virtualFile: VirtualFile, action: (StoredFileSet) -> Unit) {
    fileSets[virtualFile]?.forEach(action)
  }
  
  private fun <E : WorkspaceEntity> getContributors(entityClass: Class<out E>, storageKind: EntityStorageKind): List<WorkspaceFileIndexContributor<E>> {
    val map = when (storageKind) {
      EntityStorageKind.MAIN -> contributors
      EntityStorageKind.UNLOADED -> contributorsForUnloaded
    }
    val value = map[entityClass] ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    return value as List<WorkspaceFileIndexContributor<E>>
  }

  private fun <E : WorkspaceEntity> registerFileSets(entity: E, entityClass: Class<out E>, storage: EntityStorage, 
                                                     storageKind: EntityStorageKind, registrar: WorkspaceFileSetRegistrar) {
    getContributors(entityClass, storageKind).forEach { contributor ->
      contributor.registerFileSets(entity, registrar, storage)
    }
  }

  private fun <E : WorkspaceEntity> processChangesByContributor(contributor: WorkspaceFileIndexContributor<E>,
                                                                storageKind: EntityStorageKind,
                                                                event: VersionedStorageChange) {
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

    val removeRegistrar = RemoveFileSetsRegistrarImpl(storageKind)
    for (removed in removedEntities) {
      contributor.registerFileSets(removed, removeRegistrar, event.storageBefore)
    }
    val storeRegistrar = StoreFileSetsRegistrarImpl(storageKind)
    for (added in addedEntities) {
      contributor.registerFileSets(added, storeRegistrar, event.storageAfter)
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

  override fun resetCustomContributors() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    nonIncrementalContributors.resetCache()
    resetFileCache()
  }

  override fun markDirty(entityReferences: Collection<EntityReference<WorkspaceEntity>>, filesToInvalidate: Collection<VirtualFile>) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    dirtyEntities.addAll(entityReferences)
    dirtyFiles.addAll(filesToInvalidate)
    hasDirtyEntities = dirtyEntities.isNotEmpty()
  }

  override fun onEntitiesChanged(event: VersionedStorageChange, storageKind: EntityStorageKind) {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    contributorList.filter { it.storageKind == storageKind }.forEach { 
      processChangesByContributor(it, storageKind, event)
    }
    resetFileCache()
  }

  override fun updateDirtyEntities() {
    ApplicationManager.getApplication().assertWriteAccessAllowed()
    for (file in dirtyFiles) {
      val collection = fileSets.remove(file)
      collection?.forEach { 
        dirtyEntities.add(it.entityReference)
      }
    }
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val removeRegistrar = RemoveFileSetsRegistrarImpl(EntityStorageKind.MAIN)
    val storeRegistrar = StoreFileSetsRegistrarImpl(EntityStorageKind.MAIN)
    for (reference in dirtyEntities) {
      val entity = reference.resolve(storage) ?: continue
      registerFileSets(entity, entity.getEntityInterface(), storage, EntityStorageKind.MAIN, removeRegistrar)
      registerFileSets(entity, entity.getEntityInterface(), storage, EntityStorageKind.MAIN, storeRegistrar)
    }
    dirtyFiles.clear()
    dirtyEntities.clear()
    resetFileCache()
    hasDirtyEntities = false
  }

  override fun resetFileCache() {
    fileIdWithoutFileSets.clear()
    packageDirectoryCache.clear()
  }
  
  private fun isPackageDirectory(dir: VirtualFile, packageName: String): Boolean = getPackageName(dir) == packageName

  private fun fillPackageDirectories(packageName: String, result: MutableList<in VirtualFile>) {
    val addedRoots = HashSet<VirtualFile>()
    fileSetsByPackagePrefix[packageName]?.values()?.forEach { fileSet ->
      val root = fileSet.root
      if (root.isValid) {
        // supporting single file source
        if (root.isFile) {
          val singleFileSourceDir = singleFileSourcesTracker.getSourceDirectoryIfExists(root)
          if (singleFileSourceDir != null && singleFileSourceDir.isValid && addedRoots.add(singleFileSourceDir)) result.add(singleFileSourceDir)
        }
        else if (addedRoots.add(root)) result.add(root)
      }
    }
  }

  override fun getPackageName(dir: VirtualFile): String? {
    if (!dir.isDirectory) return null

    val fileSet = when (val info = getFileInfo(dir, true, true, true, true)) {
                    is WorkspaceFileSetWithCustomData<*> -> info.takeIf { it.data is JvmPackageRootDataInternal }
                    is MultipleWorkspaceFileSets -> info.find(JvmPackageRootDataInternal::class.java)
                    else -> null
                  } ?: return null

    val packagePrefix = (fileSet.data as JvmPackageRootDataInternal).packagePrefix
    val packageName = VfsUtilCore.getRelativePath(dir, fileSet.root, '.') 
                      ?: error("${dir.presentableUrl} is not under ${fileSet.root.presentableUrl}")
    return when {
      packagePrefix.isEmpty() -> packageName
      packageName.isEmpty() -> packagePrefix
      else -> "$packagePrefix.$packageName"
    }
  }

  override fun getDirectoriesByPackageName(packageName: String, includeLibrarySources: Boolean): Query<VirtualFile> {
    val query = CollectionQuery(packageDirectoryCache.getDirectoriesByPackageName(packageName))
    if (includeLibrarySources) return query
    return query.filtering {
      getFileInfo(it, true, true, true, false) !is WorkspaceFileInternalInfo.NonWorkspace
    }
  }

  override fun onLowMemory() {
    packageDirectoryCache.onLowMemory()
  }

  override fun clearPackageDirectoryCache() {
    packageDirectoryCache.clear()
  }

  override fun getNonExistentFileSetKinds(url: VirtualFileUrl): Set<NonExistingFileSetKind> {
    return nonExistingFilesRegistry.getFileSetKindsFor(url)
  }

  override fun analyzeVfsChanges(events: List<VFileEvent>): VfsChangeApplier? {
    return nonExistingFilesRegistry.analyzeVfsChanges(events)
  }

  private inner class StoreFileSetsRegistrarImpl(private val storageKind: EntityStorageKind) : WorkspaceFileSetRegistrar {
    override fun registerFileSet(root: VirtualFileUrl,
                                 kind: WorkspaceFileKind,
                                 entity: WorkspaceEntity,
                                 customData: WorkspaceFileSetData?) {
      registerFileSet(root, kind, entity, customData, recursive = true)
    }

    private fun registerFileSet(root: VirtualFileUrl, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?,
                                recursive: Boolean) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null) {
        registerFileSet(rootFile, kind, entity, customData, recursive)
      }
      else {
        nonExistingFilesRegistry.registerUrl(root, entity, storageKind,
                                             if (kind.isContent) NonExistingFileSetKind.INCLUDED_CONTENT else NonExistingFileSetKind.INCLUDED_OTHER)
      }
    }

    override fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
      registerFileSet(root, kind, entity, customData, recursive = true)
    }

    private fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?,
                                recursive: Boolean) {
      val fileSet = WorkspaceFileSetImpl(root, kind, entity.createReference(), storageKind, customData ?: DummyWorkspaceFileSetData,
                                         recursive)
      fileSets.putValue(root, fileSet)
      if (customData is JvmPackageRootDataInternal) {
        fileSetsByPackagePrefix.addFileSet(customData.packagePrefix, fileSet)
      }
    }

    override fun registerNonRecursiveFileSet(file: VirtualFileUrl,
                                             kind: WorkspaceFileKind,
                                             entity: WorkspaceEntity,
                                             customData: WorkspaceFileSetData?) {
      registerFileSet(file, kind, entity, customData, recursive = false)
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
      val excludedRootFile = rootFileSupplier.findFile(excludedRoot)
      if (excludedRootFile != null) {
        fileSets.putValue(excludedRootFile, ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.ALL, entity.createReference(), storageKind))
      }
      else {
        nonExistingFilesRegistry.registerUrl(excludedRoot, entity, storageKind, NonExistingFileSetKind.EXCLUDED_FROM_CONTENT)
      }
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity) {
      val file = rootFileSupplier.findFile(excludedRoot)
      if (file != null) {
        registerExcludedRoot(file, excludedFrom, entity)
      }
      else {
        nonExistingFilesRegistry.registerUrl(excludedRoot, entity, storageKind, if (excludedFrom.isContent) NonExistingFileSetKind.EXCLUDED_FROM_CONTENT else NonExistingFileSetKind.EXCLUDED_OTHER)
      }
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFile,
                                      excludedFrom: WorkspaceFileKind,
                                      entity: WorkspaceEntity) {
      val mask = if (excludedFrom == WorkspaceFileKind.EXTERNAL) WorkspaceFileKindMask.EXTERNAL else excludedFrom.toMask()
      fileSets.putValue(excludedRoot, ExcludedFileSet.ByFileKind(mask, entity.createReference(), storageKind))
    }

    override fun registerExclusionPatterns(root: VirtualFileUrl,
                                           patterns: List<String>,
                                           entity: WorkspaceEntity) {
      val rootFile = rootFileSupplier.findFile(root)
      if (!patterns.isEmpty()) {
        if (rootFile != null) {
          fileSets.putValue(rootFile, ExcludedFileSet.ByPattern(rootFile, patterns, entity.createReference(), storageKind))
        }
        else {
          nonExistingFilesRegistry.registerUrl(root, entity, storageKind, NonExistingFileSetKind.EXCLUDED_OTHER)
        }
      }
    }

    override fun registerExclusionCondition(root: VirtualFileUrl, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null) {
        registerExclusionCondition(rootFile, condition, entity)
      }
      else {
        nonExistingFilesRegistry.registerUrl(root, entity, storageKind, NonExistingFileSetKind.EXCLUDED_OTHER)
      }
    }

    override fun registerExclusionCondition(root: VirtualFile,
                                            condition: (VirtualFile) -> Boolean,
                                            entity: WorkspaceEntity) {
      fileSets.putValue(root, ExcludedFileSet.ByCondition(root, condition, entity.createReference(), storageKind))
    }
  }

  private inner class RemoveFileSetsRegistrarImpl(private val storageKind: EntityStorageKind) : WorkspaceFileSetRegistrar {
    override fun registerFileSet(root: VirtualFileUrl,
                                 kind: WorkspaceFileKind,
                                 entity: WorkspaceEntity,
                                 customData: WorkspaceFileSetData?) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null) {
        registerFileSet(rootFile, kind, entity, customData)
      }
      else {
        nonExistingFilesRegistry.unregisterUrl(root, entity, storageKind)
      }
    }


    override fun registerFileSet(root: VirtualFile,
                                 kind: WorkspaceFileKind,
                                 entity: WorkspaceEntity,
                                 customData: WorkspaceFileSetData?) {
      fileSets.removeValueIf(root) { it is WorkspaceFileSetImpl && isOriginatedFrom(it, entity) }
      if (customData is JvmPackageRootDataInternal) {
        fileSetsByPackagePrefix.removeByPrefixAndReference(customData.packagePrefix, entity.createReference())
      }
    }

    override fun registerNonRecursiveFileSet(file: VirtualFileUrl,
                                             kind: WorkspaceFileKind,
                                             entity: WorkspaceEntity,
                                             customData: WorkspaceFileSetData?) {
      registerFileSet(file, kind, entity, customData)
    }

    private fun isOriginatedFrom(fileSet: StoredFileSet, entity: WorkspaceEntity): Boolean {
      return fileSet.entityStorageKind == storageKind && fileSet.entityReference.isReferenceTo(entity)
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
      val excludedRootFile = rootFileSupplier.findFile(excludedRoot)
      if (excludedRootFile != null) {
        //todo compare origins, not just their entities?
        fileSets.removeValueIf(excludedRootFile) { it is ExcludedFileSet && it.entityReference.isReferenceTo(entity) }
      }
      else {
        nonExistingFilesRegistry.unregisterUrl(excludedRoot, entity, storageKind)
      }
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity) {
      val excludedRootFile = rootFileSupplier.findFile(excludedRoot)
      if (excludedRootFile != null) {
        registerExcludedRoot(excludedRootFile, excludedFrom, entity)
      }
      else {
        nonExistingFilesRegistry.unregisterUrl(excludedRoot, entity, storageKind)
      }
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFile,
                                      excludedFrom: WorkspaceFileKind,
                                      entity: WorkspaceEntity) {
      fileSets.removeValueIf(excludedRoot) { it is ExcludedFileSet && it.entityReference.isReferenceTo(entity) }
    }

    override fun registerExclusionPatterns(root: VirtualFileUrl,
                                           patterns: List<String>,
                                           entity: WorkspaceEntity) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null) {
        fileSets.removeValueIf(rootFile) { it is ExcludedFileSet.ByPattern && it.entityReference.isReferenceTo(entity) }
      }
      else {
        nonExistingFilesRegistry.unregisterUrl(root, entity, storageKind)
      }
    }

    override fun registerExclusionCondition(root: VirtualFileUrl, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity) {
      val rootFile = rootFileSupplier.findFile(root)
      if (rootFile != null) {
        registerExclusionCondition(rootFile, condition, entity)
      }
      else {
        nonExistingFilesRegistry.unregisterUrl(root, entity, storageKind)
      }
    }

    override fun registerExclusionCondition(root: VirtualFile,
                                            condition: (VirtualFile) -> Boolean,
                                            entity: WorkspaceEntity) {
      fileSets.removeValueIf(root) { it is ExcludedFileSet.ByCondition && it.entityReference.isReferenceTo(entity) }
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