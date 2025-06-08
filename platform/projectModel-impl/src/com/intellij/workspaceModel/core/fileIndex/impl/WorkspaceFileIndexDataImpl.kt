// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.ide.highlighter.ArchiveFileType
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.readActionBlocking
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.fileTypes.FileTypeRegistry
import com.intellij.openapi.project.Project
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
import com.intellij.platform.workspace.storage.*
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.util.CollectionQuery
import com.intellij.util.Query
import com.intellij.util.SlowOperations
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.containers.CollectionFactory
import com.intellij.util.containers.ConcurrentBitSet
import com.intellij.workspaceModel.core.fileIndex.*

internal class WorkspaceFileIndexDataImpl(
  private val contributorList: List<WorkspaceFileIndexContributor<*>>,
  private val project: Project,
  parentDisposable: Disposable,
): WorkspaceFileIndexData {
  private val contributors = contributorList.asSequence().filter { it.storageKind == EntityStorageKind.MAIN }.groupBy { it.entityClass }
  private val contributorsForUnloaded = contributorList.asSequence().filter { it.storageKind == EntityStorageKind.UNLOADED }.groupBy { it.entityClass }
  private val contributorDependencies = contributorList.associateWith { it.dependenciesOnOtherEntities }
  
  /** These maps are accessed under 'Read Action' and updated under 'Write Action' or under 'Read Action' with a special lock in [NonIncrementalContributors.updateIfNeeded]
   * [VirtualFile] is used as a key instead of [VirtualFileUrl] primarily for performance and memory efficiency.
   * Using VirtualFile allows for fast HashMap lookups in getFileInfo (which is requested via for example [com.intellij.openapi.roots.FileIndex.isInContent])
   * Also, we would need to convert all virtual files to urls but all created instances of VirtualFileUrl are retained indefinitely which will
   * lead to memory leak. Maybe it is possible to implement lightweight [VirtualFileUrl] but it's not clear how to then implement efficient
   * equals and hashCode.
   */
  private val fileSets: MutableMap<VirtualFile, StoredFileSetCollection> = CollectionFactory.createSmallMemoryFootprintMap()
  private val fileSetsByPackagePrefix = PackagePrefixStorage()

  private val nonExistingFilesRegistry = NonExistingWorkspaceRootsRegistry(project, this)
  
  private val packageDirectoryCache: PackageDirectoryCacheImpl
  private val nonIncrementalContributors = NonIncrementalContributors(project)
  private val fileIdWithoutFileSets = ConcurrentBitSet.create()
  private val fileTypeRegistry = FileTypeRegistry.getInstance()
  private val dirtyEntities = HashSet<EntityPointer<WorkspaceEntity>>()
  private val dirtyFiles = HashSet<VirtualFile>()
  @Volatile
  private var hasDirtyEntities = false

  private val librariesAndSdkContributors: LibrariesAndSdkContributors? = if (Registry.`is`("ide.workspace.model.sdk.remove.custom.processing")) {
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

  init {
    packageDirectoryCache = PackageDirectoryCacheImpl(::fillPackageFilesAndDirectories, ::isPackageDirectory)
  }

  suspend fun init() {
    WorkspaceFileIndexDataMetrics.instancesCounter.incrementAndGet()
    val start = Nanoseconds.now()

    val workspaceModel = project.serviceAsync<WorkspaceModel>() as WorkspaceModelInternal
    registerAllEntities(EntityStorageKind.MAIN, workspaceModel)
    registerAllEntities(EntityStorageKind.UNLOADED, workspaceModel)

    if (librariesAndSdkContributors != null) {
      WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
        val libraryTablesRegistrar = serviceAsync<LibraryTablesRegistrar>()
        readActionBlocking {
          librariesAndSdkContributors.registerFileSets(libraryTablesRegistrar)
        }
      }
    }

    WorkspaceFileIndexDataMetrics.initTimeNanosec.addElapsedTime(start)
  }

  fun blockingInit() {
    val workspaceModel = WorkspaceModel.getInstance(project) as WorkspaceModelInternal
    WorkspaceFileIndexDataMetrics.instancesCounter.incrementAndGet()
    val start = Nanoseconds.now()

    registerAllEntities(EntityStorageKind.MAIN, workspaceModel)
    registerAllEntities(EntityStorageKind.UNLOADED, workspaceModel)

    if (librariesAndSdkContributors != null) {
      WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
        val libraryTablesRegistrar = LibraryTablesRegistrar.getInstance()
        ApplicationManager.getApplication().runReadAction {
          librariesAndSdkContributors.registerFileSets(libraryTablesRegistrar)
        }
      }
    }

    WorkspaceFileIndexDataMetrics.initTimeNanosec.addElapsedTime(start)
  }

  private fun registerAllEntities(storageKind: EntityStorageKind, workspaceModel: WorkspaceModelInternal) {
    val (storage, contributors) = when (storageKind) {
      EntityStorageKind.MAIN -> workspaceModel.currentSnapshot to contributors
      EntityStorageKind.UNLOADED -> workspaceModel.currentSnapshotOfUnloadedEntities to contributorsForUnloaded
    }
    val registrar = StoreFileSetsRegistrarImpl(storageKind, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix)
    WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
      for (entityClass in contributors.keys) {
        for (entity in storage.entities(entityClass)) {
          registerFileSets(entity = entity, entityClass = entityClass, storage = storage, storageKind = storageKind, registrar = registrar)
        }
      }
    }
  }

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
  
  private fun <E : WorkspaceEntity> getContributors(entityClass: Class<out E>, storageKind: EntityStorageKind): List<WorkspaceFileIndexContributor<E>> {
    val map = when (storageKind) {
      EntityStorageKind.MAIN -> contributors
      EntityStorageKind.UNLOADED -> contributorsForUnloaded
    }
    val value = map[entityClass] ?: emptyList()
    @Suppress("UNCHECKED_CAST")
    return value as List<WorkspaceFileIndexContributor<E>>
  }

  private fun <E : WorkspaceEntity> registerFileSets(
    entity: E,
    entityClass: Class<out E>,
    storage: EntityStorage,
    storageKind: EntityStorageKind,
    registrar: WorkspaceFileSetRegistrar,
  ) {
    val contributors = getContributors(entityClass, storageKind)
    WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
      for (contributor in contributors) {
        contributor.registerFileSets(entity, registrar, storage)
      }
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
    WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
      for (removed in removedEntities) {
        contributor.registerFileSets(removed, removeRegistrar, event.storageBefore)
      }
    }
    val storeRegistrar = StoreFileSetsRegistrarImpl(storageKind, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix)
    WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
      for (added in addedEntities) {
        contributor.registerFileSets(added, storeRegistrar, event.storageAfter)
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
    contributorList.filter { it.storageKind == storageKind }.forEach { 
      processChangesByContributor(it, storageKind, event)
    }
    resetFileCache()
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
    val removeRegistrar = RemoveFileSetsRegistrarImpl(EntityStorageKind.MAIN)
    val storeRegistrar = StoreFileSetsRegistrarImpl(EntityStorageKind.MAIN, nonExistingFilesRegistry, fileSets, fileSetsByPackagePrefix)
    for (reference in dirtyEntities) {
      val entity = reference.resolve(storage) ?: continue
      WorkspaceFileIndexDataMetrics.registerFileSetsTimeNanosec.addMeasuredTime {
        registerFileSets(entity, entity.getEntityInterface(), storage, EntityStorageKind.MAIN, removeRegistrar)
        registerFileSets(entity, entity.getEntityInterface(), storage, EntityStorageKind.MAIN, storeRegistrar)
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
    fileSetsByPackagePrefix.get(packageName)?.values()?.forEach { fileSet ->
      val root = fileSet.root
      if (root.isValid) {
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
    return nonExistingFilesRegistry.analyzeVfsChanges(events)
  }

  private inner class RemoveFileSetsRegistrarImpl(private val storageKind: EntityStorageKind) : WorkspaceFileSetRegistrar {
    override fun registerFileSet(root: VirtualFileUrl,
                                 kind: WorkspaceFileKind,
                                 entity: WorkspaceEntity,
                                 customData: WorkspaceFileSetData?) {
      val rootFile = root.virtualFile
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
        fileSetsByPackagePrefix.removeByPrefixAndPointer(customData.packagePrefix, entity.createPointer())
      }
    }

    override fun registerNonRecursiveFileSet(file: VirtualFileUrl,
                                             kind: WorkspaceFileKind,
                                             entity: WorkspaceEntity,
                                             customData: WorkspaceFileSetData?) {
      registerFileSet(file, kind, entity, customData)
    }

    private fun isOriginatedFrom(fileSet: StoredFileSet, entity: WorkspaceEntity): Boolean {
      return fileSet.entityStorageKind == storageKind && fileSet.entityPointer.isPointerTo(entity)
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, entity: WorkspaceEntity) {
      val excludedRootFile = excludedRoot.virtualFile
      if (excludedRootFile != null) {
        //todo compare origins, not just their entities?
        fileSets.removeValueIf(excludedRootFile) { it is ExcludedFileSet && it.entityPointer.isPointerTo(entity) }
      }
      else {
        nonExistingFilesRegistry.unregisterUrl(excludedRoot, entity, storageKind)
      }
    }

    override fun registerExcludedRoot(excludedRoot: VirtualFileUrl, excludedFrom: WorkspaceFileKind, entity: WorkspaceEntity) {
      val excludedRootFile = excludedRoot.virtualFile
      if (excludedRootFile != null) {
        fileSets.removeValueIf(excludedRootFile) { it is ExcludedFileSet && it.entityPointer.isPointerTo(entity) }
      }
      else {
        nonExistingFilesRegistry.unregisterUrl(excludedRoot, entity, storageKind)
      }
    }

    override fun registerExclusionPatterns(root: VirtualFileUrl,
                                           patterns: List<String>,
                                           entity: WorkspaceEntity) {
      val rootFile = root.virtualFile
      if (rootFile != null) {
        fileSets.removeValueIf(rootFile) { it is ExcludedFileSet.ByPattern && it.entityPointer.isPointerTo(entity) }
      }
      else {
        nonExistingFilesRegistry.unregisterUrl(root, entity, storageKind)
      }
    }

    override fun registerExclusionCondition(root: VirtualFileUrl, condition: (VirtualFile) -> Boolean, entity: WorkspaceEntity) {
      val rootFile = root.virtualFile
      if (rootFile != null) {
        fileSets.removeValueIf(rootFile) { it is ExcludedFileSet.ByCondition && it.entityPointer.isPointerTo(entity) }
      }
      else {
        nonExistingFilesRegistry.unregisterUrl(root, entity, storageKind)
      }
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