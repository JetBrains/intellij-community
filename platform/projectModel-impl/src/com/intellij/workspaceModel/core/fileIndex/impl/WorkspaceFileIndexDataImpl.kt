// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet", "SSBasedInspection")

package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectRootManager
import com.intellij.openapi.roots.ex.ProjectRootManagerEx
import com.intellij.openapi.roots.impl.PackageDirectoryCacheImpl
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.*
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.pointers.VirtualFilePointer
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.impl.WorkspaceModelInternal
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.diagnostic.telemetry.helpers.Nanoseconds
import com.intellij.platform.diagnostic.telemetry.impl.span
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.CollectionQuery
import com.intellij.util.Query
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.workspaceModel.core.fileIndex.*
import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap

@Suppress("DuplicatedCode")
internal suspend fun initWorkspaceFileIndexData(
  project: Project,
  parentDisposable: Disposable,
  contributorList: List<WorkspaceFileIndexContributor<*>>,
): WorkspaceFileIndexDataImpl {
  val fileSets = Object2ObjectOpenHashMap<VirtualFile, StoredFileSetCollection>()
  val fileSetsByPackagePrefix = PackagePrefixStorage()

  val workspaceModel = project.serviceAsync<WorkspaceModel>() as WorkspaceModelInternal
  val nonExistingFilesRegistry = NonExistingWorkspaceRootsRegistry(project, workspaceModel.getVirtualFileUrlManager())

  val librariesAndSdkContributors: LibrariesAndSdkContributors? = if (Registry.`is`("ide.workspace.model.sdk.remove.custom.processing")) {
    null
  }
  else {
    LibrariesAndSdkContributors(
      project = project,
      fileSets = fileSets,
      fileSetsByPackagePrefix = fileSetsByPackagePrefix,
      projectRootManager = project.serviceAsync<ProjectRootManager>() as ProjectRootManagerEx,
      parentDisposable = parentDisposable,
    )
  }

  val contributors = getContributors(contributorList, EntityStorageKind.MAIN)

  WorkspaceFileIndexDataMetrics.instancesCounter.incrementAndGet()
  val start = Nanoseconds.now()

  span("register main entities") {
    val registrar = StoreFileSetsRegistrarImpl(EntityStorageKind.MAIN, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix)
    WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
      for ((entityClass, contributors) in contributors) {
        span("register file sets by ${entityClass.name.substringAfterLast('.')}") {
          for (entity in workspaceModel.currentSnapshot.entities(entityClass)) {
            registerFileSets(entity = entity, storage = workspaceModel.currentSnapshot, contributors = contributors, registrar = registrar)
          }
        }
      }
    }
  }
  span("register unloaded entities") {
    val registrar = StoreFileSetsRegistrarImpl(EntityStorageKind.UNLOADED, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix)
    registerAllEntities(
      registrar = registrar,
      storage = workspaceModel.currentSnapshotOfUnloadedEntities,
      contributorMap = getContributors(contributorList, EntityStorageKind.UNLOADED),
    )
  }

  if (librariesAndSdkContributors != null) {
    WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
      val libraryTablesRegistrar = serviceAsync<LibraryTablesRegistrar>()
      readActionBlocking {
        librariesAndSdkContributors.registerFileSets(libraryTablesRegistrar)
      }
    }
  }

  WorkspaceFileIndexDataMetrics.initTimeNanosec.addElapsedTime(start)

  return WorkspaceFileIndexDataImpl(
    contributorList = contributorList,
    project = project,
    fileSets = fileSets,
    fileSetsByPackagePrefix = fileSetsByPackagePrefix,
    nonExistingFilesRegistry = nonExistingFilesRegistry,
    contributors = contributors,
  )
}

private fun getContributors(
  contributorList: List<WorkspaceFileIndexContributor<*>>,
  storageKind: EntityStorageKind,
): Map<Class<WorkspaceEntity>, List<WorkspaceFileIndexContributor<WorkspaceEntity>>> {
  val contributors = LinkedHashMap<Class<WorkspaceEntity>, MutableList<WorkspaceFileIndexContributor<WorkspaceEntity>>>()
  for (element in contributorList) {
    if (element.storageKind != storageKind) {
      continue
    }
    @Suppress("UNCHECKED_CAST")
    element as WorkspaceFileIndexContributor<WorkspaceEntity>
    contributors.computeIfAbsent(element.entityClass) { ArrayList() }.add(element)
  }
  return contributors
}

@Suppress("DuplicatedCode")
internal fun blockingInitWorkspaceFileIndexData(
  project: Project,
  parentDisposable: Disposable,
  contributorList: List<WorkspaceFileIndexContributor<*>>,
): WorkspaceFileIndexDataImpl {
  val fileSets = Object2ObjectOpenHashMap<VirtualFile, StoredFileSetCollection>()
  val fileSetsByPackagePrefix = PackagePrefixStorage()

  val workspaceModel = WorkspaceModel.getInstance(project) as WorkspaceModelInternal
  val nonExistingFilesRegistry = NonExistingWorkspaceRootsRegistry(project, workspaceModel.getVirtualFileUrlManager())

  val librariesAndSdkContributors: LibrariesAndSdkContributors? = if (Registry.`is`("ide.workspace.model.sdk.remove.custom.processing")) {
    null
  }
  else {
    LibrariesAndSdkContributors(
      project = project,
      fileSets = fileSets,
      fileSetsByPackagePrefix = fileSetsByPackagePrefix,
      projectRootManager = ProjectRootManagerEx.getInstanceEx(project),
      parentDisposable = parentDisposable,
    )
  }

  val contributors = getContributors(contributorList, EntityStorageKind.MAIN)

  WorkspaceFileIndexDataMetrics.instancesCounter.incrementAndGet()
  val start = Nanoseconds.now()

  registerAllEntities(
    registrar = StoreFileSetsRegistrarImpl(EntityStorageKind.MAIN, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix),
    storage = workspaceModel.currentSnapshot,
    contributorMap = contributors,
  )
  registerAllEntities(
    registrar = StoreFileSetsRegistrarImpl(EntityStorageKind.UNLOADED, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix),
    storage = workspaceModel.currentSnapshotOfUnloadedEntities,
    contributorMap = getContributors(contributorList, EntityStorageKind.UNLOADED),
  )

  if (librariesAndSdkContributors != null) {
    WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
      val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
      ApplicationManager.getApplication().runReadAction {
        librariesAndSdkContributors.registerFileSets(libraryTablesRegistrar)
      }
    }
  }

  WorkspaceFileIndexDataMetrics.initTimeNanosec.addElapsedTime(start)

  return WorkspaceFileIndexDataImpl(
    contributorList = contributorList,
    project = project,
    fileSets = fileSets,
    fileSetsByPackagePrefix = fileSetsByPackagePrefix,
    nonExistingFilesRegistry = nonExistingFilesRegistry,
    contributors = contributors,
  )
}

/** Maps are accessed under 'Read Action' and updated under 'Write Action' or under 'Read Action' with a special lock in [NonIncrementalContributors.updateIfNeeded]
 * [VirtualFile] is used as a key instead of [VirtualFileUrl] primarily for performance and memory efficiency.
 * Using VirtualFile allows for fast HashMap lookups in getFileInfo (which is requested via for example [com.intellij.openapi.roots.FileIndex.isInContent])
 * Also, we would need to convert all virtual files to urls but all created instances of VirtualFileUrl are retained indefinitely which will
 * lead to memory leak. Maybe it is possible to implement lightweight [VirtualFileUrl] but it's not clear how to then implement efficient
 * equals and hashCode.
 */
internal class WorkspaceFileIndexDataImpl(
  private val contributorList: List<WorkspaceFileIndexContributor<*>>,
  private val project: Project,
  private val fileSets: MutableMap<VirtualFile, StoredFileSetCollection>,
  private val fileSetsByPackagePrefix: PackagePrefixStorage,
  private val nonExistingFilesRegistry: NonExistingWorkspaceRootsRegistry,
  private val contributors: Map<Class<WorkspaceEntity>, List<WorkspaceFileIndexContributor<WorkspaceEntity>>>,
): WorkspaceFileIndexData {
  private val contributorDependencies = contributorList.associateWith { it.dependenciesOnOtherEntities }

  private val packageDirectoryCache = PackageDirectoryCacheImpl(::fillPackageFilesAndDirectories, ::isPackageDirectory)
  private val nonIncrementalContributors = NonIncrementalContributors(project)
  private val fileIdWithoutFileSets = ConcurrentBitSet.create()
  private val fileTypeRegistry = FileTypeRegistry.getInstance()
  private val dirtyEntities = HashSet<EntityPointer<WorkspaceEntity>>()
  private val dirtyFiles = HashSet<VirtualFile>()
  @Volatile
  private var hasDirtyEntities = false

  override fun getFileInfo(
    file: VirtualFile,
    honorExclusion: Boolean,
    includeContentSets: Boolean,
    includeContentNonIndexableSets: Boolean,
    includeExternalSets: Boolean,
    includeExternalSourceSets: Boolean,
    includeCustomKindSets: Boolean
  ): WorkspaceFileInternalInfo = WorkspaceFileIndexDataMetrics.getFileInfoTimeNanosec.addMeasuredTime {
    if (!file.isValid) return@addMeasuredTime WorkspaceFileInternalInfo.NonWorkspace.INVALID
    if (file.fileSystem is NonPhysicalFileSystem && file.parent == null) {
      return@addMeasuredTime WorkspaceFileInternalInfo.NonWorkspace.NOT_UNDER_ROOTS
    }
    ensureIsUpToDate()

    val originalAcceptedKindMask =
      (if (includeContentSets) WorkspaceFileKindMask.CONTENT else 0) or
        (if (includeContentNonIndexableSets) WorkspaceFileKindMask.CONTENT_NON_INDEXABLE else 0) or
        (if (includeExternalSets) WorkspaceFileKindMask.EXTERNAL_BINARY else 0) or
        (if (includeExternalSourceSets) WorkspaceFileKindMask.EXTERNAL_SOURCE else 0) or
        (if (includeCustomKindSets) WorkspaceFileKindMask.CUSTOM else 0)
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
            return@addMeasuredTime WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
          }
          
          if (storedKindMask and StoredFileSetKindMask.ACCEPTED_FILE_SET != 0) {
            if (storedKindMask == StoredFileSetKindMask.ACCEPTED_FILE_SET) {
              return@addMeasuredTime storedFileSets as WorkspaceFileInternalInfo
            }
            val acceptedFileSets = ArrayList<WorkspaceFileSetImpl>()
            //copy a mutable variable used from lambda to a 'val' to ensure that kotlinc won't wrap it into IntRef
            val currentKindMask = acceptedKindsMask 
            //this should be a rare case, so it's ok to use less optimal code here and check 'isUnloaded' again
            storedFileSets.forEach { fileSet ->
              if (fileSet is WorkspaceFileSetImpl && fileSet.accepts(currentKindMask, project, file)) {
                acceptedFileSets.add(fileSet)
              }
            }
            return@addMeasuredTime if (acceptedFileSets.size > 1) MultipleWorkspaceFileSetsImpl(acceptedFileSets) else acceptedFileSets.first()
          }
        }
        if (fileTypeRegistry.isFileIgnored(current)) {
          return@addMeasuredTime WorkspaceFileInternalInfo.NonWorkspace.IGNORED
        }
        if (fileId >= 0 && storedFileSets == null) {
          fileIdWithoutFileSets.set(fileId)
        }
      }
      current = current.parent
    }
    if (originalAcceptedKindMask != acceptedKindsMask) {
      return@addMeasuredTime WorkspaceFileInternalInfo.NonWorkspace.EXCLUDED
    }
    return@addMeasuredTime WorkspaceFileInternalInfo.NonWorkspace.NOT_UNDER_ROOTS
  }

  private fun ensureIsUpToDate() {
    SlowOperations.assertSlowOperationsAreAllowed()
    if (hasDirtyEntities && ApplicationManager.getApplication().isWriteAccessAllowed) {
      updateDirtyEntities()
    }
    ThreadingAssertions.assertReadAccess()
    nonIncrementalContributors.updateIfNeeded(fileSets, fileSetsByPackagePrefix, nonExistingFilesRegistry)
  }

  @RequiresReadLock
  override fun visitFileSets(visitor: WorkspaceFileSetVisitor) {
    ThreadingAssertions.assertReadAccess()
    val start = Nanoseconds.now()
    try {
      ensureIsUpToDate()
      //forEach call below isn't inlined, so the lambda is stored in a variable to prevent creation of many identical instances (IJPL-14542)
      val action = { storedFileSet: StoredFileSet ->
        when (storedFileSet) {
          is WorkspaceFileSetImpl -> {
            visitor.visitIncludedRoot(storedFileSet, storedFileSet.entityPointer)
          }
          is ExcludedFileSet -> Unit
        }
      }
      for (value in fileSets.values) {
        value.forEach(action)
      }
    }
    finally {
      WorkspaceFileIndexDataMetrics.visitFileSetsTimeNanosec.addElapsedTime(start)
    }
  }

  fun processFileSets(virtualFile: VirtualFile, action: (StoredFileSet) -> Unit) = WorkspaceFileIndexDataMetrics.processFileSetsTimeNanosec.addMeasuredTime {
    fileSets[virtualFile]?.forEach(action)
  }

  private fun <E : WorkspaceEntity> processChangesByContributor(contributor: WorkspaceFileIndexContributor<E>,
                                                                event: VersionedStorageChange,
                                                                storeRegistrar: StoreFileSetsRegistrarImpl,
                                                                removeRegistrar: RemoveFileSetsRegistrarImpl) {
    val removedEntities = LinkedHashSet<E>()
    val addedEntities = LinkedHashSet<E>()
    val entitiesInStorage = LinkedHashSet<E>()
    val entitiesNotInStorage = LinkedHashSet<E>()
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
        is DependencyDescription.OnEntity<*, *> -> processOnEntityDependency(dependency as DependencyDescription.OnEntity<E, *>,
                                                                             event,
                                                                             removedEntities,
                                                                             addedEntities,
                                                                             entitiesInStorage,
                                                                             entitiesNotInStorage)
        is DependencyDescription.OnReference<*, *> -> processOnReference(dependency,
                                                                         event,
                                                                         removedEntities as MutableSet<WorkspaceEntity>,
                                                                         addedEntities as MutableSet<WorkspaceEntity>,
        )
      }
    }

    WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
      for (removed in removedEntities) {
        contributor.registerFileSets(removed, removeRegistrar, event.storageBefore)
      }
      for (entityInStorage in entitiesInStorage) {
        contributor.registerFileSets(entityInStorage, storeRegistrar, event.storageAfter)
      }
    }
    WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
      for (added in addedEntities) {
        contributor.registerFileSets(added, storeRegistrar, event.storageAfter)
      }
      for (entityNotInStorage in entitiesNotInStorage) {
        contributor.registerFileSets(entityNotInStorage, removeRegistrar, event.storageBefore)
      }
    }
  }

  private fun <R : WorkspaceEntityWithSymbolicId, E : WorkspaceEntityWithSymbolicId> processOnReference(
    dependencyDescription: DependencyDescription.OnReference<R, E>,
    event: VersionedStorageChange,
    removedEntities: MutableSet<WorkspaceEntity>,
    addedEntities: MutableSet<WorkspaceEntity>,
  ) {
    val previousDependencies = mutableSetOf<SymbolicEntityId<R>>()
    val actualDependencies = mutableSetOf<SymbolicEntityId<R>>()


    event.getChanges(dependencyDescription.referenceHolderClass).asSequence().forEach { change ->
      change.oldEntity?.let {
        dependencyDescription.referencedEntitiesGetter(it).toCollection(previousDependencies)
      }
      change.newEntity?.let {
        dependencyDescription.referencedEntitiesGetter(it).toCollection(actualDependencies)
      }
    }

    // everything in actual dependencies but not in previous is considered new
    // everything in previous but not in actual dependencies is considered removed

    (actualDependencies - previousDependencies).mapNotNullTo(addedEntities) { it.resolve(event.storageAfter) }

    (previousDependencies - actualDependencies)
      // check if any reference holder is still references removed entity
      .filter { event.storageAfter.referrers(it, dependencyDescription.referenceHolderClass).none() }
      .mapNotNullTo(removedEntities) { it.resolve(event.storageBefore) }
  }

  /**
   * This method searches for entities [R] that were affected by the change of entity [E].
   *
   * Example:
   *
   * Suppose we add entities A, B, C into [removedEntities] and E, F, G in [addedEntities] and we have B and F actually in storage.
   * We remove file sets for A, B, C, but we need to register a file set for B, so entity B will be in [entitiesToKeep].
   * By the same logic we register file sets for E, F, G, but we need to remove file sets for E and G, so they will in [entitiesToRemove].
   *
   * [entitiesToRemove] = [addedEntities] - [entitiesInCurrentStorage]
   *
   * [entitiesToKeep] = [removedEntities] intersect [affectedByRemovalEntities]
   *
   */
  private fun <R: WorkspaceEntity, E: WorkspaceEntity> processOnEntityDependency(dependency: DependencyDescription.OnEntity<R, E>,
                                                                                 event: VersionedStorageChange,
                                                                                 removedEntities: MutableSet<R>,
                                                                                 addedEntities: MutableSet<R>,
                                                                                 entitiesToKeep: MutableSet<R>,
                                                                                 entitiesToRemove: MutableSet<R>) {
    var onEntityDependencyApplied = false
    event.getChanges(dependency.entityClass).asSequence().forEach { change ->
      onEntityDependencyApplied = true
      change.oldEntity?.let {
        dependency.resultGetter(it).toCollection(removedEntities)
      }
      change.newEntity?.let {
        dependency.resultGetter(it).toCollection(addedEntities)
      }
    }

    if (onEntityDependencyApplied) {
      val entitiesInCurrentStorage = event.storageAfter.entities(dependency.resultClass).toSet()

      if (removedEntities.isNotEmpty()) {
        entitiesToKeep.addAll(removedEntities.intersect(entitiesInCurrentStorage))
      }
      if (addedEntities.isNotEmpty()) {
        entitiesToRemove.addAll(addedEntities - entitiesInCurrentStorage)
      }
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
    ThreadingAssertions.assertWriteAccess()
    nonIncrementalContributors.resetCache()
    resetFileCache()
  }

  override fun markDirty(entityPointers: Collection<EntityPointer<WorkspaceEntity>>,
                         filesToInvalidate: Collection<VirtualFile>) = WorkspaceFileIndexDataMetrics.markDirtyTimeNanosec.addMeasuredTime {
    ThreadingAssertions.assertWriteAccess()
    dirtyEntities.addAll(entityPointers)
    dirtyFiles.addAll(filesToInvalidate)
    hasDirtyEntities = dirtyEntities.isNotEmpty()
  }

  override fun onEntitiesChanged(event: VersionedStorageChange,
                                 storageKind: EntityStorageKind) = WorkspaceFileIndexDataMetrics.onEntitiesChangedTimeNanosec.addMeasuredTime {
    ThreadingAssertions.assertWriteAccess()
    val removeRegistrar = RemoveFileSetsRegistrarImpl(storageKind, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix)
    val storeRegistrar = StoreFileSetsRegistrarImpl(storageKind, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix)
    contributorList.filter { it.storageKind == storageKind }.forEach { 
      processChangesByContributor(it, event, storeRegistrar, removeRegistrar)
    }
    resetFileCache()
    if (storeRegistrar.storedFileSets.isNotEmpty() || removeRegistrar.removedFileSets.isNotEmpty()) {
      val changeLog = WorkspaceFileIndexChangedEventImpl(project,
                                                         removedFileSets = removeRegistrar.removedFileSets.values,
                                                         storedFileSets = storeRegistrar.storedFileSets.values,)
      project.messageBus.syncPublisher(WorkspaceFileIndexListener.TOPIC).workspaceFileIndexChanged(changeLog)
    }
  }

  override fun updateDirtyEntities() {
    val start = Nanoseconds.now()

    ThreadingAssertions.assertWriteAccess()
    for (file in dirtyFiles) {
      val collection = fileSets.remove(file)
      collection?.forEach { 
        dirtyEntities.add(it.entityPointer)
      }
    }
    val storage = WorkspaceModel.getInstance(project).currentSnapshot
    val removeRegistrar = RemoveFileSetsRegistrarImpl(EntityStorageKind.MAIN, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix)
    val storeRegistrar = StoreFileSetsRegistrarImpl(EntityStorageKind.MAIN, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix)
    for (reference in dirtyEntities) {
      val entity = reference.resolve(storage) ?: continue
      @Suppress("UNCHECKED_CAST")
      val contributors = contributors.get(entity.getEntityInterface()) ?: continue
      WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
        registerFileSets(entity = entity, storage = storage, contributors = contributors, registrar = removeRegistrar)
        registerFileSets(entity = entity, storage = storage, contributors = contributors, registrar = storeRegistrar)
      }
    }
    dirtyFiles.clear()
    dirtyEntities.clear()
    resetFileCache()
    hasDirtyEntities = false

    WorkspaceFileIndexDataMetrics.updateDirtyEntitiesTimeNanosec.addElapsedTime(start)
  }

  override fun resetFileCache() {
    fileIdWithoutFileSets.clear()
    packageDirectoryCache.clear()
  }
  
  private fun isPackageDirectory(dir: VirtualFile, packageName: String): Boolean = getPackageName(dir) == packageName

  private fun fillPackageFilesAndDirectories(packageName: String, result: MutableList<in VirtualFile>) {
    val addedRoots = HashSet<VirtualFile>()
    val map = fileSetsByPackagePrefix.get(packageName) ?: return
    for (list in map.values) {
      for (fileSet in list) {
        val root = fileSet.root
        if (!root.isValid) {
          continue
        }

        // single file source roots could be added here as well
        if (addedRoots.add(root)) result.add(root)
        if (root.fileSystem.protocol == StandardFileSystems.JAR_PROTOCOL) {
          root.findChild("META-INF")?.findChild("versions")?.children?.forEach { versionRoot ->
            val version = versionRoot.name.toIntOrNull()
            if (version != null && version >= 9) {
              if (addedRoots.add(versionRoot)) result.add(versionRoot)
            }
          }
        }
      }
    }
  }

  private fun isVersionRoot(root: VirtualFile, directory: VirtualFile): Boolean {
    val parent = directory.parent ?: return false
    if (parent.name == "versions") {
      val grandParent = parent.parent
      if (grandParent != null && grandParent.name == "META-INF" && root == grandParent.parent) {
        val version = directory.name.toIntOrNull()
        return version != null && version >= 9
      }
    }
    return false
  }

  private fun correctRoot(root: VirtualFile, file: VirtualFile): VirtualFile {
    if (root.fileType !is ArchiveFileType) return root
    var cur = file
    while (cur != root) {
      if (isVersionRoot(root, cur)) return cur
      cur = cur.parent
    }
    return root
  }

  override fun getPackageName(dirOrFile: VirtualFile): String? = WorkspaceFileIndexDataMetrics.getPackageNameTimeNanosec.addMeasuredTime {
    val fileSet = when (val info = getFileInfo(dirOrFile, true, true, true, true, true, true)) {
                    is WorkspaceFileSetWithCustomData<*> -> info.takeIf { it.data is JvmPackageRootDataInternal }
                    is MultipleWorkspaceFileSets -> info.find(JvmPackageRootDataInternal::class.java)
                    else -> null
                  } ?: return@addMeasuredTime null

    val packagePrefix = (fileSet.data as JvmPackageRootDataInternal).packagePrefix
    if (!fileSet.root.isDirectory) return@addMeasuredTime packagePrefix
    val dir = if (dirOrFile.isDirectory) dirOrFile else dirOrFile.parent
    if (!dir.isDirectory) return@addMeasuredTime null
    val packageName = VfsUtilCore.getRelativePath(dir, correctRoot(fileSet.root, dir), '.') 
                      ?: error("${dir.presentableUrl} is not under ${fileSet.root.presentableUrl}")
    return@addMeasuredTime when {
      packagePrefix.isEmpty() -> packageName
      packageName.isEmpty() -> packagePrefix
      else -> "$packagePrefix.$packageName"
    }
  }

  override fun getDirectoriesByPackageName(packageName: String,
                                           includeLibrarySources: Boolean): Query<VirtualFile> = WorkspaceFileIndexDataMetrics.getDirectoriesByPackageNameTimeNanosec.addMeasuredTime {
    val query = CollectionQuery(packageDirectoryCache.getDirectoriesByPackageName(packageName))
    return@addMeasuredTime if (includeLibrarySources) query
    else query.filtering {
      getFileInfo(it, true, true, true, true, false, true) !is WorkspaceFileInternalInfo.NonWorkspace
    }
  }

  override fun getFilesByPackageName(packageName: String): Query<VirtualFile> {
    return CollectionQuery(packageDirectoryCache.getFilesByPackageName(packageName))
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
    return nonExistingFilesRegistry.analyzeVfsChanges(events, this)
  }
}

private fun registerAllEntities(
  registrar: StoreFileSetsRegistrarImpl,
  storage: ImmutableEntityStorage,
  contributorMap: Map<Class<WorkspaceEntity>, List<WorkspaceFileIndexContributor<WorkspaceEntity>>>,
) {
  WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
    for ((entityClass, contributors) in contributorMap) {
      for (entity in storage.entities(entityClass)) {
        registerFileSets(entity = entity, storage = storage, contributors = contributors, registrar = registrar)
      }
    }
  }
}

private fun <E : WorkspaceEntity> registerFileSets(
  entity: E,
  storage: EntityStorage,
  registrar: WorkspaceFileSetRegistrar,
  contributors: List<WorkspaceFileIndexContributor<WorkspaceEntity>>,
) {
  WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
    for (contributor in contributors) {
      contributor.registerFileSets(entity, registrar, storage)
    }
  }
}

private class RemoveFileSetsRegistrarImpl(
  private val storageKind: EntityStorageKind,
  private val nonExistingFilesRegistry: NonExistingWorkspaceRootsRegistry,
  private val fileSets: MutableMap<VirtualFile, StoredFileSetCollection>,
  private val fileSetsByPackagePrefix: PackagePrefixStorage,
) : WorkspaceFileSetRegistrar {

  val removedFileSets = mutableMapOf<VirtualFile, WorkspaceFileSet>()

  override fun registerFileSet(root: VirtualFileUrl, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    val rootFile = root.virtualFile
    if (rootFile == null) {
      nonExistingFilesRegistry.unregisterUrl(root, entity, storageKind)
    }
    else {
      registerFileSet(rootFile, kind, entity, customData)
    }
  }

  override fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    val fileSetToRemove = fileSets[root]
    if (fileSetToRemove != null) {
      when (fileSetToRemove) {
        is MultipleWorkspaceFileSets -> {
          fileSetToRemove.forEach { fileSet ->
            if (fileSet is WorkspaceFileSetImpl) {
              removedFileSets.putIfAbsent(root, fileSet)
              return@forEach
            }
          }
        }
        is WorkspaceFileSetImpl -> removedFileSets.putIfAbsent(root, fileSetToRemove)
        else -> {}
      }
    }
    fileSets.removeValueIf(root) { it is WorkspaceFileSetImpl && isOriginatedFrom(it, entity) }
    if (customData is JvmPackageRootDataInternal) {
      fileSetsByPackagePrefix.removeByPrefixAndPointer(customData.packagePrefix, entity.createPointer())
    }
  }

  override fun registerNonRecursiveFileSet(
    file: VirtualFileUrl,
    kind: WorkspaceFileKind,
    entity: WorkspaceEntity,
    customData: WorkspaceFileSetData?,
  ) {
    registerFileSet(file, kind, entity, customData)
  }

  private fun isOriginatedFrom(fileSet: StoredFileSet, entity: WorkspaceEntity): Boolean {
    return fileSet.entityStorageKind == storageKind && fileSet.entityPointer.isPointerTo(entity)
  }

  override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
    val excludedRootFile = excludedRoot.virtualFile
    if (excludedRootFile == null) {
      nonExistingFilesRegistry.unregisterUrl(excludedRoot, entity, storageKind)
    }
    else {
      //todo compare origins, not just their entities?
      fileSets.removeValueIf(excludedRootFile) { it is ExcludedFileSet && it.entityPointer.isPointerTo(entity) }
    }
  }

  override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity) {
    val excludedRootFile = excludedRoot.virtualFile
    if (excludedRootFile == null) {
      nonExistingFilesRegistry.unregisterUrl(excludedRoot, entity, storageKind)
    }
    else {
      fileSets.removeValueIf(excludedRootFile) { it is ExcludedFileSet && it.entityPointer.isPointerTo(entity) }
    }
  }

  override fun registerExclusionPatterns(root: VirtualFileUrl, patterns: List<String>, entity: WorkspaceEntity) {
    val rootFile = root.virtualFile
    if (rootFile == null) {
      nonExistingFilesRegistry.unregisterUrl(root, entity, storageKind)
    }
    else {
      fileSets.removeValueIf(rootFile) { it is ExcludedFileSet.ByPattern && it.entityPointer.isPointerTo(entity) }
    }
  }

  override fun registerExclusionCondition(root: VirtualFileUrl, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity) {
    val rootFile = root.virtualFile
    if (rootFile == null) {
      nonExistingFilesRegistry.unregisterUrl(root, entity, storageKind)
    }
    else {
      fileSets.removeValueIf(rootFile) { it is ExcludedFileSet.ByCondition && it.entityPointer.isPointerTo(entity) }
    }
  }
}

internal fun WorkspaceFileKind.toMask(): Int {
  val mask = when (this) {
    WorkspaceFileKind.CONTENT, WorkspaceFileKind.TEST_CONTENT -> WorkspaceFileKindMask.CONTENT
    WorkspaceFileKind.EXTERNAL -> WorkspaceFileKindMask.EXTERNAL_BINARY
    WorkspaceFileKind.EXTERNAL_SOURCE -> WorkspaceFileKindMask.EXTERNAL_SOURCE
    WorkspaceFileKind.CUSTOM -> WorkspaceFileKindMask.CUSTOM
    WorkspaceFileKind.CONTENT_NON_INDEXABLE -> WorkspaceFileKindMask.CONTENT_NON_INDEXABLE
  }
  return mask
}

private class StoreFileSetsRegistrarImpl(
  private val storageKind: EntityStorageKind,
  private val nonExistingFilesRegistry: NonExistingWorkspaceRootsRegistry,
  private val fileSets: MutableMap<VirtualFile, StoredFileSetCollection>,
  private val fileSetsByPackagePrefix: PackagePrefixStorage,
) : WorkspaceFileSetRegistrar {

  val storedFileSets = mutableMapOf<VirtualFile, WorkspaceFileSet>()

  override fun registerFileSet(
    root: VirtualFileUrl,
    kind: WorkspaceFileKind,
    entity: WorkspaceEntity,
    customData: WorkspaceFileSetData?,
  ) {
    registerFileSet(root = root, kind = kind, entity = entity, customData = customData, recursive = true)
  }

  private fun registerFileSet(
    root: VirtualFileUrl,
    kind: WorkspaceFileKind,
    entity: WorkspaceEntity,
    customData: WorkspaceFileSetData?,
    recursive: Boolean,
  ) {
    val rootFile = if (root is VirtualFilePointer) root.file else VirtualFileManager.getInstance().findFileByUrl(root.url)
    if (rootFile != null) {
      registerFileSet(rootFile, kind, entity, customData, recursive)
    }
    else {
      nonExistingFilesRegistry.registerUrl(
        root = root,
        entity = entity,
        storageKind = storageKind,
        fileSetKind = if (kind.isContent) NonExistingFileSetKind.INCLUDED_CONTENT else NonExistingFileSetKind.INCLUDED_OTHER,
      )
    }
  }

  override fun registerFileSet(root: VirtualFile, kind: WorkspaceFileKind, entity: WorkspaceEntity, customData: WorkspaceFileSetData?) {
    registerFileSet(root = root, kind = kind, entity = entity, customData = customData, recursive = true)
  }

  private fun registerFileSet(
    root: VirtualFile,
    kind: WorkspaceFileKind,
    entity: WorkspaceEntity,
    customData: WorkspaceFileSetData?,
    recursive: Boolean,
  ) {
    val fileSet = WorkspaceFileSetImpl(
      root = root,
      kind = kind,
      entityPointer = entity.createPointer(),
      entityStorageKind = storageKind,
      data = customData ?: DummyWorkspaceFileSetData,
      recursive = recursive,
    )
    fileSets.putValue(root, fileSet)
    storedFileSets.putIfAbsent(root, fileSet)
    if (customData is JvmPackageRootDataInternal) {
      fileSetsByPackagePrefix.addFileSet(customData.packagePrefix, fileSet)
    }
  }

  override fun registerNonRecursiveFileSet(
    file: VirtualFileUrl,
    kind: WorkspaceFileKind,
    entity: WorkspaceEntity,
    customData: WorkspaceFileSetData?,
  ) {
    registerFileSet(root = file, kind = kind, entity = entity, customData = customData, recursive = false)
  }

  override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
    val excludedRootFile = excludedRoot.virtualFile
    if (excludedRootFile == null) {
      nonExistingFilesRegistry.registerUrl(excludedRoot, entity, storageKind, NonExistingFileSetKind.EXCLUDED_FROM_CONTENT)
    }
    else {
      fileSets.putValue(excludedRootFile, ExcludedFileSet.ByFileKind(WorkspaceFileKindMask.ALL, entity.createPointer(), storageKind))
    }
  }

  override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity) {
    val file = excludedRoot.virtualFile
    if (file == null) {
      nonExistingFilesRegistry.registerUrl(
        root = excludedRoot,
        entity = entity,
        storageKind = storageKind,
        fileSetKind = if (excludedFrom.isContent) NonExistingFileSetKind.EXCLUDED_FROM_CONTENT else NonExistingFileSetKind.EXCLUDED_OTHER,
      )
    }
    else {
      val mask = when (excludedFrom) {
        WorkspaceFileKind.EXTERNAL -> WorkspaceFileKindMask.EXTERNAL
        WorkspaceFileKind.CONTENT ->  WorkspaceFileKindMask.CONTENT or WorkspaceFileKindMask.CONTENT_NON_INDEXABLE
        else -> excludedFrom.toMask()
      }

      fileSets.putValue(file, ExcludedFileSet.ByFileKind(mask, entity.createPointer(), storageKind))
    }
  }

  override fun registerExclusionPatterns(root: VirtualFileUrl, patterns: List<String>, entity: WorkspaceEntity) {
    val rootFile = root.virtualFile
    if (!patterns.isEmpty()) {
      if (rootFile == null) {
        nonExistingFilesRegistry.registerUrl(root, entity, storageKind, NonExistingFileSetKind.EXCLUDED_OTHER)
      }
      else {
        fileSets.putValue(rootFile, ExcludedFileSet.ByPattern(rootFile, patterns, entity.createPointer(), storageKind))
      }
    }
  }

  override fun registerExclusionCondition(root: VirtualFileUrl, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity) {
    val rootFile = root.virtualFile
    if (rootFile == null) {
      nonExistingFilesRegistry.registerUrl(root, entity, storageKind, NonExistingFileSetKind.EXCLUDED_OTHER)
    }
    else {
      fileSets.putValue(rootFile, ExcludedFileSet.ByCondition(rootFile, condition, entity.createPointer(), storageKind))
    }
  }
}