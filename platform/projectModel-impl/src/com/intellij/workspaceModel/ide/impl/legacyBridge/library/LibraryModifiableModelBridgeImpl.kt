// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.workspaceModel.ide.impl.legacyBridge.library

import com.intellij.openapi.Disposable
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.module.Module
import com.intellij.openapi.project.isExternalStorageEnabled
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.libraries.*
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.backend.workspace.WorkspaceModel
import com.intellij.platform.workspace.jps.JpsFileEntitySource
import com.intellij.platform.workspace.jps.JpsImportedEntitySource
import com.intellij.platform.workspace.jps.entities.*
import com.intellij.platform.workspace.storage.CachedValue
import com.intellij.platform.workspace.storage.MutableEntityStorage
import com.intellij.platform.workspace.storage.url.VirtualFileUrl
import com.intellij.platform.workspace.storage.url.VirtualFileUrlManager
import com.intellij.util.containers.ContainerUtil
import com.intellij.workspaceModel.ide.impl.GlobalWorkspaceModel
import com.intellij.workspaceModel.ide.impl.legacyBridge.LegacyBridgeModifiableBase
import com.intellij.workspaceModel.ide.impl.legacyBridge.library.LibraryBridgeImpl.Companion.toLibraryRootType
import com.intellij.workspaceModel.ide.legacyBridge.LibraryModifiableModelBridge
import org.jdom.Element
import org.jetbrains.jps.model.serialization.library.JpsLibraryTableSerializer

internal class LibraryModifiableModelBridgeImpl(
  private val originalLibrary: LibraryBridgeImpl,
  private val originalLibrarySnapshot: LibraryStateSnapshot,
  diff: MutableEntityStorage,
  private val targetBuilder: MutableEntityStorage?,
  private val virtualFileManager: VirtualFileUrlManager,
  cacheStorageResult: Boolean = true
) : LegacyBridgeModifiableBase(diff, cacheStorageResult), LibraryModifiableModelBridge, RootProvider {

  private var entityId = originalLibrarySnapshot.libraryEntity.symbolicId
  private var reloadKind = false

  private val currentLibraryValue = CachedValue { storage ->
    val newLibrary = LibraryStateSnapshot(
      libraryEntity = storage.resolve(entityId) ?: error("Can't resolve library via $entityId"),
      storage = storage,
      libraryTable = originalLibrarySnapshot.libraryTable
    )

    newLibrary
  }

  private val currentLibrary: LibraryStateSnapshot
    get() = entityStorageOnDiff.cachedValue(currentLibraryValue)

  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> = currentLibrary.getFiles(rootType)
  override fun getKind(): PersistentLibraryKind<*>? = currentLibrary.kind
  override fun getUrls(rootType: OrderRootType): Array<String> = currentLibrary.getUrls(rootType)
  override fun getName(): String? = currentLibrary.name
  override fun getPresentableName(): String = LibraryBridgeImpl.getPresentableName(this)
  override fun getProperties(): LibraryProperties<*>? = currentLibrary.properties
  override fun getExcludedRootUrls(): Array<String> = currentLibrary.excludedRootUrls
  override fun isJarDirectory(url: String): Boolean = currentLibrary.isJarDirectory(url)
  override fun isJarDirectory(url: String, rootType: OrderRootType): Boolean = currentLibrary.isJarDirectory(url, rootType)

  override fun setName(name: String) {
    assertModelIsLive()

    val entity = currentLibrary.libraryEntity
    if (entity.name == name) return
    if (currentLibrary.libraryTable.getLibraryByName(name) != null) {
      error("Library named $name already exists")
    }

    entityId = entity.symbolicId.copy(name = name)
    diff.modifyLibraryEntity(entity) {
      this.name = name
    }

    if (assertChangesApplied && currentLibrary.name != name) {
      error("setName: expected library name ${name}, but got ${currentLibrary.name}. Original name: ${originalLibrarySnapshot.name}")
    }
  }

  override fun commit() {
    assertModelIsLive()

    modelIsCommittedOrDisposed = true

    if (reloadKind) {
      originalLibrary.cleanCachedValue()
    }
    if (isChanged) {
      if (targetBuilder != null) {
        if (targetBuilder !== diff) {
          targetBuilder.applyChangesFrom(diff)
        }
      }
      else {
        when (val o = originalLibrary.origin) {
          is LibraryOrigin.OfProject -> {
            WorkspaceModel.getInstance(o.project).updateProjectModel("Library model commit") {
              it.applyChangesFrom(diff)
            }
          }
          is LibraryOrigin.OfMachine -> {
            GlobalWorkspaceModel.getInstance(o.eelMachine).updateModel("Library model commit") {
              it.applyChangesFrom(diff)
            }
          }
        }
      }
      originalLibrary.entityId = entityId
      originalLibrary.fireRootSetChanged()
    }
  }

  override fun prepareForCommit() {
    assertModelIsLive()

    modelIsCommittedOrDisposed = true

    if (reloadKind) originalLibrary.cleanCachedValue()
    if (isChanged) originalLibrary.entityId = entityId
  }

  override val libraryId: LibraryId
    get() = entityId

  private fun update(updater: LibraryEntity.Builder.() -> Unit) {
    diff.modifyLibraryEntity(currentLibrary.libraryEntity, updater)
  }

  override fun setExternalSource(externalSource: ProjectModelExternalSource) {
    update {
      val currentEntitySource = entitySource
      if (currentEntitySource is JpsFileEntitySource) {
        entitySource = JpsImportedEntitySource(currentEntitySource, externalSource.id, (originalLibrary.origin as LibraryOrigin.OfProject).project.isExternalStorageEnabled)
      }
    }
  }

  private fun updateProperties(libraryType: String?, propertiesXmlTag: String? = null) {
    val entity = currentLibrary.libraryEntity

    val properties = entity.libraryProperties
    if (libraryType == null) {
      if (properties != null) {
        diff.removeEntity(properties)
      }
    }
    else if (properties == null) {
      diff.modifyLibraryEntity(currentLibrary.libraryEntity) {
        this.libraryProperties = LibraryPropertiesEntity(entity.entitySource) {
          if (propertiesXmlTag != null) this.propertiesXmlTag = propertiesXmlTag
        }
      }
    }
    else {
      diff.modifyLibraryPropertiesEntity(properties) {
        if (propertiesXmlTag != null) this.propertiesXmlTag = propertiesXmlTag
      }
    }

    diff.modifyLibraryEntity(entity) {
      this.typeId = libraryType?.let { LibraryTypeId(libraryType) }
    }
  }

  override fun isChanged(): Boolean {
    if (!originalLibrarySnapshot.libraryEntity.hasEqualProperties(currentLibrary.libraryEntity)) return true
    val p1 = originalLibrarySnapshot.libraryEntity.libraryProperties
    val p2 = currentLibrary.libraryEntity.libraryProperties
    return !(p1 == null && p2 == null || p1 != null && p2 != null && p1.hasEqualProperties(p2))
  }

  private fun LibraryEntity.hasEqualProperties(another: LibraryEntity): Boolean {
    if (this.tableId != another.tableId) return false
    if (this.typeId != another.typeId) return false
    if (this.name != another.name) return false
    if (this.roots != another.roots) return false
    if (this.excludedRoots != another.excludedRoots) return false
    return true
  }

  private fun LibraryPropertiesEntity.hasEqualProperties(another: LibraryPropertiesEntity): Boolean {
    return this.propertiesXmlTag == another.propertiesXmlTag
  }

  override fun addJarDirectory(url: String, recursive: Boolean) =
    addJarDirectory(url, recursive, OrderRootType.CLASSES)

  override fun addJarDirectory(url: String, recursive: Boolean, rootType: OrderRootType) {
    assertModelIsLive()

    val rootTypeId = rootType.toLibraryRootType()
    val virtualFileUrl = virtualFileManager.getOrCreateFromUrl(url)
    val inclusionOptions = if (recursive) LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY else LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT

    update {
      roots.add(LibraryRoot(virtualFileUrl, rootTypeId, inclusionOptions))
    }

    if (assertChangesApplied && !currentLibrary.isJarDirectory(virtualFileUrl.url, rootType)) {
      error("addJarDirectory: expected jarDirectory exists for url '${virtualFileUrl.url}'")
    }
  }

  override fun addJarDirectory(file: VirtualFile, recursive: Boolean) = addJarDirectory(file.url, recursive)

  override fun addJarDirectory(file: VirtualFile, recursive: Boolean, rootType: OrderRootType) =
    addJarDirectory(file.url, recursive, rootType)

  override fun moveRootUp(url: String, rootType: OrderRootType) {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.getOrCreateFromUrl(url)

    update {
      val index = roots.indexOfFirst { it.url == virtualFileUrl }
      if (index <= 0) return@update
      val prevRootIndex = roots.subList(0, index).indexOfLast { it.type.name == rootType.name() }
      if (prevRootIndex < 0) return@update

      val mutable = roots.toMutableList()
      ContainerUtil.swapElements(mutable, prevRootIndex, index)
      roots = mutable
    }
  }

  override fun moveRootDown(url: String, rootType: OrderRootType) {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.getOrCreateFromUrl(url)

    update {
      val index = roots.indexOfFirst { it.url == virtualFileUrl }
      if (index < 0 || index + 1 >= roots.size) return@update
      val nextRootOffset = roots.subList(index + 1, roots.size).indexOfFirst { it.type.name == rootType.name() }
      if (nextRootOffset < 0) return@update

      val mutable = roots.toMutableList()
      ContainerUtil.swapElements(mutable, index + nextRootOffset + 1, index)
      roots = mutable
    }
  }

  override fun isValid(url: String, rootType: OrderRootType): Boolean = currentLibrary.isValid(url, rootType)

  override fun hasSameContent(library: Library): Boolean {
    if (this === library) return true
    if (library !is LibraryBridgeImpl) return false

    if (name != library.name) return false
    if (kind != library.kind) return false
    if (properties != library.properties) return false
    if (currentLibrary.libraryEntity.roots != library.librarySnapshot.libraryEntity.roots) return false
    if (!excludedRoots.contentEquals(library.excludedRoots)) return false
    return true
  }

  override fun addExcludedRoot(url: String) {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.getOrCreateFromUrl(url)

    update {
      if (!excludedRoots.map { it.url }.contains(virtualFileUrl)) {
        excludedRoots = excludedRoots + ExcludeUrlEntity(virtualFileUrl, this.entitySource)
      }
    }

    if (assertChangesApplied && !currentLibrary.excludedRootUrls.contains(virtualFileUrl.url)) {
      error("addExcludedRoot: expected excluded urls contain url '${virtualFileUrl.url}'")
    }
  }

  override fun addRoot(url: String, rootType: OrderRootType) {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.getOrCreateFromUrl(url)

    val root = LibraryRoot(
      url = virtualFileUrl,
      type = rootType.toLibraryRootType()
    )

    update {
      roots.add(root)
    }

    if (assertChangesApplied && !currentLibrary.getUrls(rootType).contains(virtualFileUrl.url)) {
      error("addRoot: expected urls for root type '${rootType.name()}' contain url '${virtualFileUrl.url}'")
    }
  }

  override fun addRoot(file: VirtualFile, rootType: OrderRootType) = addRoot(file.url, rootType)

  override fun setProperties(properties: LibraryProperties<*>?) {
    assertModelIsLive()

    if (currentLibrary.properties == properties) return

    val kind = currentLibrary.kind
    if (kind == null) {
      if (properties != null && properties !is DummyLibraryProperties) {
        LOG.error("Setting properties with null kind is unsupported")
      }
      return
    }

    updateProperties(kind.kindId, serializeComponentAsString(JpsLibraryTableSerializer.PROPERTIES_TAG, properties))
    if (assertChangesApplied && currentLibrary.properties != properties) {
      error("setProperties: properties are not equal after changing")
    }
  }

  override fun setKind(type: PersistentLibraryKind<*>?) {
    assertModelIsLive()

    if (kind == type) return

    updateProperties(type?.kindId)

    if (assertChangesApplied && currentLibrary.kind?.kindId != type?.kindId) {
      error(
        "setKind: expected kindId ${type?.kindId}, but got ${currentLibrary.kind?.kindId}. Original kind: ${originalLibrarySnapshot.kind?.kindId}")
    }
  }

  override fun forgetKind() {
    reloadKind = true
  }

  override fun restoreKind() {
    reloadKind = true
  }

  private fun isUnderRoots(url: VirtualFileUrl, roots: Collection<LibraryRoot>): Boolean {
    return VfsUtilCore.isUnder(url.url, roots.map { it.url.url })
  }

  override fun removeRoot(url: String, rootType: OrderRootType): Boolean {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.getOrCreateFromUrl(url)

    if (!currentLibrary.getUrls(rootType).contains(virtualFileUrl.url)) return false

    update {
      roots.removeIf { it.url == virtualFileUrl && it.type.name == rootType.name() }
    }
    val libraryEntity = currentLibrary.libraryEntity
    libraryEntity.excludedRoots.filterNot { isUnderRoots(it.url, libraryEntity.roots) }.forEach {
      diff.removeEntity(it)
    }

    if (assertChangesApplied && currentLibrary.getUrls(rootType).contains(virtualFileUrl.url)) {
      error("removeRoot: removed url '${virtualFileUrl.url}' type '${rootType.name()}' still exists after removing")
    }

    return true
  }

  override fun removeExcludedRoot(url: String): Boolean {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.getOrCreateFromUrl(url)

    val excludeUrlEntity = currentLibrary.libraryEntity.excludedRoots.find { it.url == virtualFileUrl }
    if (excludeUrlEntity == null) return false
    diff.removeEntity(excludeUrlEntity)

    if (assertChangesApplied && currentLibrary.excludedRootUrls.contains(virtualFileUrl.url)) {
      error("removeRoot: removed excluded url '${virtualFileUrl.url}' still exists after removing")
    }

    return true
  }

  private var disposed = false

  override fun dispose() {
    // No assertions here since it is ok to call dispose twice or more
    modelIsCommittedOrDisposed = true

    disposed = true
  }

  override fun isDisposed(): Boolean = disposed

  override fun toString(): String {
    return "Library '$name', roots: ${currentLibrary.libraryEntity.roots}"
  }

  override fun getInvalidRootUrls(type: OrderRootType): List<String> = currentLibrary.getInvalidRootUrls(type)
  override fun getExcludedRoots(): Array<VirtualFile> = currentLibrary.excludedRoots
  override fun getModule(): Module? = currentLibrary.module

  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener) = throw UnsupportedOperationException()
  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener,
                                         parentDisposable: Disposable) = throw UnsupportedOperationException()

  override fun removeRootSetChangedListener(listener: RootProvider.RootSetChangedListener) = throw UnsupportedOperationException()

  override fun getExternalSource(): ProjectModelExternalSource? = originalLibrarySnapshot.externalSource

  override fun getModifiableModel(): LibraryEx.ModifiableModelEx = throw UnsupportedOperationException()

  override fun getSource(): Library = originalLibrary

  override fun getTable(): LibraryTable = originalLibrarySnapshot.libraryTable

  override fun getRootProvider(): RootProvider = this

  override fun readExternal(element: Element?) = throw UnsupportedOperationException()
  override fun writeExternal(element: Element?) = throw UnsupportedOperationException()

  companion object {
    private val LOG = logger<LibraryModifiableModelBridgeImpl>()
  }
}
