// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.VFileCopyEvent
import com.intellij.openapi.vfs.newvfs.events.VFileCreateEvent
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent
import com.intellij.openapi.vfs.newvfs.events.VFileEvent
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.backend.workspace.toVirtualFileUrl
import com.intellij.platform.backend.workspace.virtualFile
import com.intellij.platform.workspace.jps.entities.LibraryEntity
import com.intellij.platform.workspace.jps.entities.LibraryTableId
import com.intellij.platform.workspace.storage.EntityPointer
import com.intellij.platform.workspace.storage.EntityStorage
import com.intellij.platform.workspace.storage.WorkspaceEntity
import com.intellij.platform.workspace.storage.impl.indices.VirtualFileIndex
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind
import com.intellij.workspaceModel.core.fileIndex.WorkspaceFileSetExclusionCondition
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.ProjectLibraryTableBridgeImpl.Companion.libraryMap
import org.intellij.lang.annotations.MagicConstant
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.jps.model.fileTypes.FileNameMatcherFactory

internal class NonExistingWorkspaceRootsRegistry(
  private val project: Project,
  private val virtualFileManager: VirtualFileUrlManager,
) {
  /** todo: replace by MostlySingularMultiMap to reduce memory usage  */
  private val nonExistingFiles = MultiMap.createConcurrent<VirtualFileUrl, NonExistingFileSetData>()
  
  fun registerUrl(root: VirtualFileUrl, data: NonExistingFileSetData) {
    nonExistingFiles.putValue(root, data)
  }

  fun unregisterUrl(fileUrl: VirtualFileUrl, entity: WorkspaceEntity, storageKind: EntityStorageKind) {
    nonExistingFiles.removeValueIf(fileUrl) { data ->
      data.storageKind == storageKind && data.reference.isPointerTo(entity)
    }
  }

  fun unregisterUrl(fileUrl: VirtualFileUrl, reference: EntityPointer<WorkspaceEntity>, storageKind: EntityStorageKind) {
    nonExistingFiles.removeValueIf(fileUrl) { data ->
      data.storageKind == storageKind && data.reference == reference
    }
  }

  fun removeUrl(url: VirtualFileUrl) {
    nonExistingFiles.remove(url)
  }
  
  fun getFileSetsFor(url: VirtualFileUrl): Collection<NonExistingFileSetData> = nonExistingFiles.get(url)

  private inline fun <K, V> MultiMap<K, V>.removeValueIf(key: K, crossinline valuePredicate: (V) -> Boolean) {
    val collection = get(key)
    collection.removeIf { valuePredicate(it) }
    if (collection.isEmpty()) {
      remove(key)
    }
  }

  fun analyzeVfsChanges(events: List<VFileEvent>, indexData: WorkspaceFileIndexDataImpl): VfsChangeApplier? {
    val entityChanges = EntityChangeStorage()
    val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
    for (event in events) {
      when (event) {
        is VFileDeleteEvent ->
          calculateEntityChangesIfNeeded(event.file.toVirtualFileUrl(virtualFileManager), event.file, entityChanges, entityStorage, true, indexData)
        is VFileCreateEvent -> {
          val parentUrl = event.parent.url
          val protocolEnd = parentUrl.indexOf(URLUtil.SCHEME_SEPARATOR)
          val url = if (protocolEnd != -1) {
            parentUrl.take(protocolEnd) + URLUtil.SCHEME_SEPARATOR + event.path
          }
          else {
            VfsUtilCore.pathToUrl(event.path)
          }
          val virtualFileUrl = virtualFileManager.storeAndGet(url)
          calculateEntityChangesIfNeeded(virtualFileUrl, null, entityChanges, entityStorage, false, indexData)
          if (url.startsWith(URLUtil.FILE_PROTOCOL) && (event.isDirectory || event.childName.endsWith(".jar"))) {
            //if a new directory or a new jar file is created, we may have roots pointing to files under it with jar protocol
            val suffix = if (event.isDirectory) "" else URLUtil.JAR_SEPARATOR
            val jarFileUrl = URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + URLUtil.urlToPath(url) + suffix
            val jarVirtualFileUrl = virtualFileManager.storeAndGet(jarFileUrl)
            calculateEntityChangesIfNeeded(jarVirtualFileUrl, null, entityChanges, entityStorage, false, indexData)
          }
        }
        is VFileCopyEvent -> calculateEntityChangesIfNeeded(virtualFileManager.storeAndGet(VfsUtilCore.pathToUrl(event.path)), null, entityChanges,
                                                            entityStorage, false, indexData)
        is VFilePropertyChangeEvent, is VFileMoveEvent -> {
          val (oldUrl, newUrl) = getOldAndNewUrls(event)
          if (oldUrl != newUrl) {
            calculateEntityChangesIfNeeded(virtualFileManager.storeAndGet(oldUrl), event.file, entityChanges, entityStorage, true, indexData)
            calculateEntityChangesIfNeeded(virtualFileManager.storeAndGet(newUrl), null, entityChanges, entityStorage, false, indexData)
          }
        }
      }
    }

    if (!entityChanges.hasChanges()) {
      return null
    }

    return VfsChangeApplierImpl(entityChanges, indexData, this, project)
  }

  private fun getIncludingJarDirectory(storage: EntityStorage,
                                       virtualFileUrl: VirtualFileUrl): VirtualFileUrl? {
    val indexedJarDirectories = (storage.getVirtualFileUrlIndex() as VirtualFileIndex).getIndexedJarDirectories()
    var parentVirtualFileUrl: VirtualFileUrl? = virtualFileUrl
    while (parentVirtualFileUrl != null && parentVirtualFileUrl !in indexedJarDirectories) {
      parentVirtualFileUrl = parentVirtualFileUrl.parent
    }
    return if (parentVirtualFileUrl != null && parentVirtualFileUrl in indexedJarDirectories) parentVirtualFileUrl else null
  }

  private fun calculateEntityChangesIfNeeded(
    virtualFileUrl: VirtualFileUrl,
    virtualFile: VirtualFile?,
    entityChanges: EntityChangeStorage,
    storage: EntityStorage,
    allRootsWereRemoved: Boolean,
    indexData: WorkspaceFileIndexDataImpl,
  ) {
    val includingJarDirectory = getIncludingJarDirectory(storage, virtualFileUrl)
    if (includingJarDirectory != null) {
      //todo handle JAR directories inside WorkspaceFileIndex instead
      storage.getVirtualFileUrlIndex().findEntitiesByUrl(includingJarDirectory).forEach {
        entityChanges.addAffectedEntity(it.createPointer(), allRootsWereRemoved)
      }
      return
    }

    collectAffectedEntities(virtualFileUrl, virtualFile, allRootsWereRemoved, entityChanges, indexData)
    virtualFileUrl.subTreeFileUrls.forEach { urlUnder ->
      val fileUnder = if (virtualFile != null) urlUnder.virtualFile else null
      collectAffectedEntities(urlUnder, fileUnder, allRootsWereRemoved, entityChanges, indexData)
    }
  }

  private fun collectAffectedEntities(
    url: VirtualFileUrl,
    virtualFile: VirtualFile?,
    allRootsWereRemoved: Boolean,
    entityChanges: EntityChangeStorage,
    indexData: WorkspaceFileIndexDataImpl,
  ) {
    if (virtualFile != null) {
      var hasEntities = false
      indexData.processFileSets(virtualFile) {
        if (it.entityStorageKind == EntityStorageKind.MAIN) {
          entityChanges.addAffectedEntity(it.entityPointer, allRootsWereRemoved)
        }
        hasEntities = true
      }
      if (hasEntities && allRootsWereRemoved) {
        entityChanges.addFileToInvalidate(url.virtualFile)
      }
    }
    var hasEntities = false
    nonExistingFiles[url].forEach { data ->
      if (data.storageKind == EntityStorageKind.MAIN) {
        hasEntities = true
        entityChanges.addAffectedEntity(data.reference, allRootsWereRemoved)
      }
    }
    if (hasEntities) {
      entityChanges.addUrlToCleanUp(url)
    }
  }
}

private class VfsChangeApplierImpl(
  private val entityChanges: EntityChangeStorage,
  private val indexData: WorkspaceFileIndexData,
  private val nonExistingRootsRegistry: NonExistingWorkspaceRootsRegistry,
  private val project: Project
) : VfsChangeApplier {
  override fun beforeVfsChange() {
    indexData.markDirty(entityChanges.affectedEntities, entityChanges.filesToInvalidate)
  }

  override fun afterVfsChange() {
    val affectedEntities = entityChanges.affectedEntities
    indexData.markDirty(affectedEntities, entityChanges.filesToInvalidate)
    entityChanges.urlsToCleanUp.forEach {
      nonExistingRootsRegistry.removeUrl(it)
    }
    indexData.updateDirtyEntities()
    
    // Keep old behaviour for global and custom libraries
    if (affectedEntities.isNotEmpty()) {
      val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot

      affectedEntities.forEach { entityRef ->
        val libraryEntity = (entityRef.resolve(entityStorage) as? LibraryEntity) ?: return@forEach
        if (libraryEntity.tableId !is LibraryTableId.GlobalLibraryTableId) return@forEach
        (entityStorage.libraryMap.getDataByEntity(libraryEntity) as? LibraryBridgeImpl)?.fireRootSetChanged()
      }
    }
  }

  override val entitiesToReindex: Set<EntityPointer<WorkspaceEntity>>
    get() = entityChanges.entitiesToReindex
}

private class EntityChangeStorage {
  private var isInitialized = false
  lateinit var entitiesToReindex: MutableSet<EntityPointer<WorkspaceEntity>>
  lateinit var affectedEntities: MutableSet<EntityPointer<WorkspaceEntity>>
  lateinit var filesToInvalidate: MutableSet<VirtualFile>
  lateinit var urlsToCleanUp: MutableSet<VirtualFileUrl>

  private fun init() {
    if (!isInitialized) {
      entitiesToReindex = LinkedHashSet()
      affectedEntities = HashSet()
      filesToInvalidate = HashSet()
      urlsToCleanUp = HashSet()
      isInitialized = true
    }
  }

  fun hasChanges() = isInitialized

  fun addAffectedEntity(reference: EntityPointer<WorkspaceEntity>, allRootsWereRemoved: Boolean) {
    init()
    affectedEntities.add(reference)
    if (!allRootsWereRemoved) {
      entitiesToReindex.add(reference)
    }
  }

  fun addFileToInvalidate(file: VirtualFile?) {
    init()
    file?.let { filesToInvalidate.add(it) }
  }
  
  fun addUrlToCleanUp(url: VirtualFileUrl) {
    init()
    urlsToCleanUp.add(url)
  }
}

fun getOldAndNewUrls(event: VFileEvent): Pair<String, String> {
  return when (event) {
    is VFilePropertyChangeEvent -> VfsUtilCore.pathToUrl(event.oldPath) to VfsUtilCore.pathToUrl(event.newPath)
    is VFileMoveEvent -> VfsUtilCore.pathToUrl(event.oldPath) to VfsUtilCore.pathToUrl(event.newPath)
    else -> error("Unexpected event type: ${event.javaClass}")
  }
}

/**
 * Describes a file set or an exclusion registered for a file which doesn't exist.
 */
@ApiStatus.Internal
sealed interface NonExistingFileSetData {
  val reference: EntityPointer<WorkspaceEntity>
  val storageKind: EntityStorageKind
}

/**
 * A file set registered for a file which doesn't exist.
 * The existing file counterpart is [WorkspaceFileSetImpl].
 */
@ApiStatus.Internal
data class NonExistingWorkspaceFileSet(
  override val reference: EntityPointer<WorkspaceEntity>,
  override val storageKind: EntityStorageKind,
  val kind: WorkspaceFileKind,
  val recursive: Boolean,
) : NonExistingFileSetData

/**
 * An exclusion registered for a file which doesn't exist.
 * Each type matches the [ExcludedFileSet] type with the same name.
 */
@ApiStatus.Internal
sealed interface NonExistingWorkspaceExclude : NonExistingFileSetData, WorkspaceExcludeFileSet {
  data class ByFileKind(
    override val reference: EntityPointer<WorkspaceEntity>,
    override val storageKind: EntityStorageKind,
    @MagicConstant(flagsFromClass = WorkspaceFileKindMask::class) override val mask: Int,
  ) : NonExistingWorkspaceExclude, WorkspaceExcludeFileSet.ByFileKind

  data class UnscopedRoot(
    override val reference: EntityPointer<WorkspaceEntity>,
    override val storageKind: EntityStorageKind,
    override val directoryOnly: Boolean,
  ) : NonExistingWorkspaceExclude, WorkspaceExcludeFileSet.UnscopedRoot

  data class ByPattern(
    override val reference: EntityPointer<WorkspaceEntity>,
    override val storageKind: EntityStorageKind,
    val patterns: List<String>,
  ) : NonExistingWorkspaceExclude, WorkspaceExcludeFileSet.ByPattern {
    private val table = FileTypeAssocTableUtil.newScalableFileTypeAssocTable<Boolean>().also { table ->
      for (pattern in patterns) {
        table.addAssociation(FileNameMatcherFactory.getInstance().createMatcher(pattern), true)
      }
    }

    override fun matches(fileName: CharSequence): Boolean = table.findAssociatedFileType(fileName) != null
  }

  data class ByCondition(
    override val reference: EntityPointer<WorkspaceEntity>,
    override val storageKind: EntityStorageKind,
    override val condition: WorkspaceFileSetExclusionCondition,
  ) : NonExistingWorkspaceExclude, WorkspaceExcludeFileSet.ByCondition

  data class ByUnscopedCondition(
    override val reference: EntityPointer<WorkspaceEntity>,
    override val storageKind: EntityStorageKind,
    override val condition: WorkspaceFileSetExclusionCondition,
  ) : NonExistingWorkspaceExclude, WorkspaceExcludeFileSet.ByUnscopedCondition
}

/**
 * Returns `true` if this exclusion excludes its root from the [content][WorkspaceFileKind.isContent] kinds.
 */
internal fun NonExistingWorkspaceExclude.excludesFromContent(): Boolean {
  return when (this) {
    is NonExistingWorkspaceExclude.ByFileKind -> mask and (WorkspaceFileKindMask.CONTENT or WorkspaceFileKindMask.CONTENT_NON_INDEXABLE) != 0
    is NonExistingWorkspaceExclude.UnscopedRoot -> true
    is NonExistingWorkspaceExclude.ByPattern,
    is NonExistingWorkspaceExclude.ByCondition,
    is NonExistingWorkspaceExclude.ByUnscopedCondition -> false
  }
}
