// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.core.fileIndex.impl

import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.newvfs.events.*
import com.intellij.util.containers.MultiMap
import com.intellij.util.io.URLUtil
import com.intellij.workspaceModel.core.fileIndex.EntityStorageKind
import com.intellij.workspaceModel.ide.WorkspaceModel
import com.intellij.workspaceModel.ide.getInstance
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.GlobalLibraryTableBridgeImpl
import com.intellij.workspaceModel.ide.legacyBridge.GlobalLibraryTableBridge
import com.intellij.workspaceModel.ide.virtualFile
import com.intellij.workspaceModel.storage.EntityReference
import com.intellij.workspaceModel.storage.EntityStorage
import com.intellij.workspaceModel.storage.WorkspaceEntity
import com.intellij.workspaceModel.storage.bridgeEntities.LibraryEntity
import com.intellij.workspaceModel.storage.impl.indices.VirtualFileIndex
import com.intellij.workspaceModel.storage.url.VirtualFileUrl
import com.intellij.workspaceModel.storage.url.VirtualFileUrlManager
import java.util.*

internal class NonExistingWorkspaceRootsRegistry(private val project: Project, private val indexData: WorkspaceFileIndexDataImpl) {
  private val virtualFileManager = VirtualFileUrlManager.getInstance(project)
  /** access guarded by the global read/write locks; todo: replace by MostlySingularMultiMap to reduce memory usage  */
  private val nonExistingFiles = MultiMap.create<VirtualFileUrl, NonExistingFileSetData>()
  
  fun registerUrl(root: VirtualFileUrl, entity: WorkspaceEntity, storageKind: EntityStorageKind, fileSetKind: NonExistingFileSetKind) {
    registerUrl(root, entity.createReference(), storageKind, fileSetKind)
  }

  fun registerUrl(root: VirtualFileUrl, reference: EntityReference<WorkspaceEntity>, storageKind: EntityStorageKind, fileSetKind: NonExistingFileSetKind) {
    nonExistingFiles.putValue(root, NonExistingFileSetData(reference, storageKind, fileSetKind))
  }

  fun unregisterUrl(fileUrl: VirtualFileUrl, entity: WorkspaceEntity, storageKind: EntityStorageKind) {
    nonExistingFiles.removeValueIf(fileUrl) { (reference, kind) ->
      kind == storageKind && reference.isReferenceTo(entity)
    }
  }

  fun unregisterUrl(fileUrl: VirtualFileUrl, reference: EntityReference<WorkspaceEntity>, storageKind: EntityStorageKind) {
    nonExistingFiles.removeValueIf(fileUrl) { (ref, kind) ->
      kind == storageKind && ref == reference
    }
  }


  fun removeUrl(url: VirtualFileUrl) {
    nonExistingFiles.remove(url)
  }
  
  fun getFileSetKindsFor(url: VirtualFileUrl): Set<NonExistingFileSetKind> {
    val data = nonExistingFiles.get(url)
    if (data.isEmpty()) return emptySet()
    return data.mapTo(EnumSet.noneOf(NonExistingFileSetKind::class.java)) { it.fileSetKind }
  }

  private inline fun <K, V> MultiMap<K, V>.removeValueIf(key: K, crossinline valuePredicate: (V) -> Boolean) {
    val collection = get(key)
    collection.removeIf { valuePredicate(it) }
    if (collection.isEmpty()) {
      remove(key)
    }
  }

  fun analyzeVfsChanges(events: List<VFileEvent>): VfsChangeApplier? {
    val entityChanges = EntityChangeStorage()
    val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
    for (event in events) {
      when (event) {
        is VFileDeleteEvent ->
          calculateEntityChangesIfNeeded(virtualFileManager.fromUrl(event.file.url), event.file, entityChanges, entityStorage, true)
        is VFileCreateEvent -> {
          val parentUrl = event.parent.url
          val protocolEnd = parentUrl.indexOf(URLUtil.SCHEME_SEPARATOR)
          val url = if (protocolEnd != -1) {
            parentUrl.substring(0, protocolEnd) + URLUtil.SCHEME_SEPARATOR + event.path
          }
          else {
            VfsUtilCore.pathToUrl(event.path)
          }
          val virtualFileUrl = virtualFileManager.fromUrl(url)
          calculateEntityChangesIfNeeded(virtualFileUrl, null, entityChanges, entityStorage, false)
          if (url.startsWith(URLUtil.FILE_PROTOCOL) && (event.isDirectory || event.childName.endsWith(".jar"))) {
            //if a new directory or a new jar file is created, we may have roots pointing to files under it with jar protocol
            val suffix = if (event.isDirectory) "" else URLUtil.JAR_SEPARATOR
            val jarFileUrl = URLUtil.JAR_PROTOCOL + URLUtil.SCHEME_SEPARATOR + URLUtil.urlToPath(url) + suffix
            val jarVirtualFileUrl = virtualFileManager.fromUrl(jarFileUrl)
            calculateEntityChangesIfNeeded(jarVirtualFileUrl, null, entityChanges, entityStorage, false)
          }
        }
        is VFileCopyEvent -> calculateEntityChangesIfNeeded(virtualFileManager.fromUrl(VfsUtilCore.pathToUrl(event.path)), null, entityChanges,
                                                            entityStorage, false)
        is VFilePropertyChangeEvent, is VFileMoveEvent -> {
          val (oldUrl, newUrl) = getOldAndNewUrls(event)
          if (oldUrl != newUrl) {
            calculateEntityChangesIfNeeded(virtualFileManager.fromUrl(oldUrl), event.file, entityChanges, entityStorage, true)
            calculateEntityChangesIfNeeded(virtualFileManager.fromUrl(newUrl), null, entityChanges, entityStorage, false)
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
      parentVirtualFileUrl = virtualFileManager.getParentVirtualUrl(parentVirtualFileUrl)
    }
    return if (parentVirtualFileUrl != null && parentVirtualFileUrl in indexedJarDirectories) parentVirtualFileUrl else null
  }

  private fun calculateEntityChangesIfNeeded(virtualFileUrl: VirtualFileUrl,
                                             virtualFile: VirtualFile?,
                                             entityChanges: EntityChangeStorage,
                                             storage: EntityStorage,
                                             allRootsWereRemoved: Boolean) {
    val includingJarDirectory = getIncludingJarDirectory(storage, virtualFileUrl)
    if (includingJarDirectory != null) {
      //todo handle JAR directories inside WorkspaceFileIndex instead
      storage.getVirtualFileUrlIndex().findEntitiesByUrl(includingJarDirectory).forEach {
        entityChanges.addAffectedEntity(it.first.createReference(), allRootsWereRemoved)
      }
      return
    }

    collectAffectedEntities(virtualFileUrl, virtualFile, allRootsWereRemoved, entityChanges)
    virtualFileUrl.subTreeFileUrls.forEach { urlUnder ->
      val fileUnder = if (virtualFile != null) urlUnder.virtualFile else null
      collectAffectedEntities(urlUnder, fileUnder, allRootsWereRemoved, entityChanges)
    }
  }

  private fun collectAffectedEntities(url: VirtualFileUrl,
                                      virtualFile: VirtualFile?,
                                      allRootsWereRemoved: Boolean,
                                      entityChanges: EntityChangeStorage) {
    if (virtualFile != null) {
      var hasEntities = false
      indexData.processFileSets(virtualFile) {
        if (it.entityStorageKind == EntityStorageKind.MAIN) {
          entityChanges.addAffectedEntity(it.entityReference, allRootsWereRemoved)
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
    
    // Keep old behaviour for global libraries
    if (GlobalLibraryTableBridge.isEnabled() && affectedEntities.isNotEmpty()) {
      val entityStorage = WorkspaceModel.getInstance(project).currentSnapshot
      val globalLibraryTableBridge = GlobalLibraryTableBridge.getInstance() as GlobalLibraryTableBridgeImpl

      affectedEntities.forEach { entityRef ->
        val libraryEntity = (entityRef.resolve(entityStorage) as? LibraryEntity) ?: return@forEach
        if (libraryEntity.tableId.level != LibraryTablesRegistrar.APPLICATION_LEVEL) return@forEach
        globalLibraryTableBridge.fireRootSetChanged(libraryEntity, entityStorage)
      }
    }
  }

  override val entitiesToReindex: List<EntityReference<WorkspaceEntity>>
    get() = entityChanges.entitiesToReindex
}

private class EntityChangeStorage {
  private var isInitialized = false
  lateinit var entitiesToReindex: MutableList<EntityReference<WorkspaceEntity>>
  lateinit var affectedEntities: MutableSet<EntityReference<WorkspaceEntity>>
  lateinit var filesToInvalidate: MutableSet<VirtualFile>
  lateinit var urlsToCleanUp: MutableSet<VirtualFileUrl>

  private fun init() {
    if (!isInitialized) {
      entitiesToReindex = ArrayList()
      affectedEntities = HashSet()
      filesToInvalidate = HashSet()
      urlsToCleanUp = HashSet()
      isInitialized = true
    }
  }

  fun hasChanges() = isInitialized

  fun addAffectedEntity(reference: EntityReference<WorkspaceEntity>, allRootsWereRemoved: Boolean) {
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

internal data class NonExistingFileSetData(
  val reference: EntityReference<WorkspaceEntity>,
  val storageKind: EntityStorageKind,
  val fileSetKind: NonExistingFileSetKind
)

/**
 * Describes kind of workspace file set associated with a non-existing file.
 */
enum class NonExistingFileSetKind {
  /**
   * File set of [content][com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind.isContent]
   */                                
  INCLUDED_CONTENT,

  /**
   * File set of other kinds
   */
  INCLUDED_OTHER,

  /**
   * Root excluded from [content][com.intellij.workspaceModel.core.fileIndex.WorkspaceFileKind.isContent]
   */
  EXCLUDED_FROM_CONTENT,

  /**
   * Root for files some of them are excluded by pattern, condition, etc.
   */
  EXCLUDED_OTHER
}
