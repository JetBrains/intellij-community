package com.intellij.workspace.legacyBridge.libraries.libraries

import com.intellij.openapi.Disposable
import com.intellij.openapi.module.Module
import com.intellij.openapi.roots.OrderRootType
import com.intellij.openapi.roots.ProjectModelExternalSource
import com.intellij.openapi.roots.RootProvider
import com.intellij.openapi.roots.impl.libraries.LibraryEx
import com.intellij.openapi.roots.impl.libraries.LibraryImpl
import com.intellij.openapi.roots.libraries.Library
import com.intellij.openapi.roots.libraries.LibraryProperties
import com.intellij.openapi.roots.libraries.LibraryTable
import com.intellij.openapi.roots.libraries.PersistentLibraryKind
import com.intellij.openapi.vfs.VfsUtilCore
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.containers.ContainerUtil
import com.intellij.workspace.api.*
import com.intellij.workspace.ide.VirtualFileUrlManagerImpl
import com.intellij.workspace.legacyBridge.typedModel.library.LibraryViaTypedEntity
import org.jdom.Element

internal class LegacyBridgeLibraryModifiableModelImpl(
  private val originalLibrary: LegacyBridgeLibraryImpl,
  private val originalLibrarySnapshot: LibraryViaTypedEntity,
  diff: TypedEntityStorageBuilder,
  private val committer: (LegacyBridgeLibraryModifiableModelImpl, TypedEntityStorageDiffBuilder) -> Unit
) : LegacyBridgeModifiableBase(diff), LibraryEx.ModifiableModelEx, LibraryEx, RootProvider {

  private val virtualFileManager: VirtualFileUrlManager = VirtualFileUrlManagerImpl.getInstance(originalLibrary.project)
  private var entityId = originalLibrarySnapshot.libraryEntity.persistentId()
  private var reloadKind = false

  private val currentLibraryValue = CachedValue { storage ->
    val newLibrary = LibraryViaTypedEntity(
      libraryImpl = originalLibrary,
      libraryEntity = storage.resolve(entityId) ?: error("Can't resolve library via $entityId"),
      storage = storage,
      filePointerProvider = originalLibrarySnapshot.filePointerProvider,
      libraryTable = originalLibrarySnapshot.libraryTable,
      modifiableModelFactory = { _,_ -> throw UnsupportedOperationException() }
    )

    newLibrary
  }

  private val currentLibrary: LibraryViaTypedEntity
    get() = entityStoreOnDiff.cachedValue(currentLibraryValue)

  override fun getFiles(rootType: OrderRootType): Array<VirtualFile> = currentLibrary.getFiles(rootType)
  override fun getKind(): PersistentLibraryKind<*>? = currentLibrary.kind
  override fun getUrls(rootType: OrderRootType): Array<String> = currentLibrary.getUrls(rootType)
  override fun getName(): String? = currentLibrary.name
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

    val oldPersistentId = entity.persistentId()
    entityId = entity.persistentId().copy(name = name)
    diff.modifyEntity(ModifiableLibraryEntity::class.java, entity) {
      this.name = name
    }

    updateModuleDependency(oldPersistentId)
    if (assertChangesApplied && currentLibrary.name != name) {
      error("setName: expected library name ${name}, but got ${currentLibrary.name}. Original name: ${originalLibrarySnapshot.name}")
    }
  }

  override fun commit() {
    assertModelIsLive()

    modelIsCommittedOrDisposed = true

    if (reloadKind) {
      originalLibrary.entityStore.clearCachedValue(originalLibrary.snapshotValue, originalLibrary.entityId)
    }
    if (isChanged) {
      committer(this, diff)
    }
  }

  private fun update(updater: ModifiableLibraryEntity.() -> Unit) {
    diff.modifyEntity(ModifiableLibraryEntity::class.java, currentLibrary.libraryEntity, updater)
  }

  private fun updateProperties(updater: ModifiableLibraryPropertiesEntity.() -> Unit) {
    val entity = currentLibrary.libraryEntity

    val referrers = entity.referrers(LibraryPropertiesEntity::library).toList()
    if (referrers.isEmpty()) {
      diff.addEntity(ModifiableLibraryPropertiesEntity::class.java, entity.entitySource) {
        library = entity
        updater()
      }
    }
    else {
      diff.modifyEntity(ModifiableLibraryPropertiesEntity::class.java, referrers.first(), updater)
      referrers.drop(1).forEach { diff.removeEntity(it) }
    }
  }

  private fun updateModuleDependency(oldLibraryId: LibraryId) {
    entityStoreOnDiff.current.entities(ModuleEntity::class.java).forEach { moduleEntity ->
      var containsOldDependency = false
      val newDependencies = moduleEntity.dependencies.map {
        when(it) {
          is ModuleDependencyItem.Exportable.LibraryDependency -> {
            if (it.library == oldLibraryId) {
              containsOldDependency = true
              it.copy(library = entityId)
            } else it
          }
          else -> it
        }
      }

      if (containsOldDependency) {
        diff.modifyEntity(ModifiableModuleEntity::class.java, moduleEntity) {
          dependencies = newDependencies
        }
      }
    }
  }

  override fun isChanged(): Boolean {
    if (!originalLibrarySnapshot.libraryEntity.hasEqualProperties(currentLibrary.libraryEntity)) return true
    val p1 = originalLibrarySnapshot.libraryEntity.getCustomProperties()
    val p2 = currentLibrary.libraryEntity.getCustomProperties()
    return !(p1 == null && p2 == null || p1 != null && p2 != null && p1.hasEqualProperties(p2))
  }

  override fun addJarDirectory(url: String, recursive: Boolean) =
    addJarDirectory(url, recursive, OrderRootType.CLASSES)

  override fun addJarDirectory(url: String, recursive: Boolean, rootType: OrderRootType) {
    assertModelIsLive()

    val rootTypeId = LibraryRootTypeId(rootType.name())
    val virtualFileUrl = virtualFileManager.fromUrl(url)
    val inclusionOptions = if (recursive) LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT_RECURSIVELY else LibraryRoot.InclusionOptions.ARCHIVES_UNDER_ROOT

    update {
      roots = roots + listOf(LibraryRoot(virtualFileUrl, rootTypeId, inclusionOptions))
    }

    if (assertChangesApplied && !currentLibrary.isJarDirectory(virtualFileUrl.url, rootType)) {
      error("addJarDirectory: expected jarDirectory exists for url '${virtualFileUrl.url}'")
    }
  }

  override fun addJarDirectory(file: VirtualFile, recursive: Boolean) = addJarDirectory(file.url, recursive)

  override fun addJarDirectory(file: VirtualFile, recursive: Boolean, rootType: OrderRootType) =
    addJarDirectory(file.url, recursive, rootType)

  // TODO Write a test
  override fun moveRootUp(url: String, rootType: OrderRootType) {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.fromUrl(url)

    update {
      val index = roots.withIndex().firstOrNull { it.value.url == virtualFileUrl } ?: return@update
      if (index.index <= 0) return@update

      val mutable = roots.toMutableList()
      ContainerUtil.swapElements(mutable, index.index - 1, index.index)
      roots = mutable.toList()
    }
  }

  // TODO Write a test
  override fun moveRootDown(url: String, rootType: OrderRootType) {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.fromUrl(url)

    update {
      val index = roots.withIndex().firstOrNull { it.value.url == virtualFileUrl } ?: return@update
      if (index.index < 0 || index.index + 1 >= roots.size) return@update

      val mutable = roots.toMutableList()
      ContainerUtil.swapElements(mutable, index.index + 1, index.index)
      roots = mutable.toList()
    }
  }

  override fun isValid(url: String, rootType: OrderRootType): Boolean = currentLibrary.isValid(url, rootType)

  override fun addExcludedRoot(url: String) {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.fromUrl(url)

    update {
      if (!excludedRoots.contains(virtualFileUrl)) {
        excludedRoots = excludedRoots + listOf(virtualFileUrl)
      }
    }

    if (assertChangesApplied && !currentLibrary.excludedRootUrls.contains(virtualFileUrl.url)) {
      error("addExcludedRoot: expected excluded urls contain url '${virtualFileUrl.url}'")
    }
  }

  override fun addRoot(url: String, rootType: OrderRootType) {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.fromUrl(url)

    val root = LibraryRoot(
      url = virtualFileUrl,
      type = LibraryRootTypeId(rootType.name()),
      inclusionOptions = LibraryRoot.InclusionOptions.ROOT_ITSELF
    )

    update {
      roots = roots + root
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
      if (properties != null) error("Setting properties with null kind is unsupported")
      return
    }

    updateProperties {
      libraryType = kind.kindId
      propertiesXmlTag = serializeComponentAsString(LibraryImpl.PROPERTIES_ELEMENT, properties)
    }

    if (assertChangesApplied && currentLibrary.properties != properties) {
      error("setProperties: properties are not equal after changing")
    }
  }

  override fun setKind(type: PersistentLibraryKind<*>) {
    assertModelIsLive()

    if (kind == type) return

    updateProperties {
      libraryType = type.kindId
    }

    if (assertChangesApplied && currentLibrary.kind?.kindId != type.kindId) {
      error("setKind: expected kindId ${type.kindId}, but got ${currentLibrary.kind?.kindId}. Original kind: ${originalLibrarySnapshot.kind?.kindId}")
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

    val virtualFileUrl = virtualFileManager.fromUrl(url)

    if (!currentLibrary.getUrls(rootType).contains(virtualFileUrl.url)) return false

    update {
      roots = roots.filterNot { it.url == virtualFileUrl && it.type.name == rootType.name() }
      excludedRoots = excludedRoots.filter { isUnderRoots(it, roots) }
    }

    if (assertChangesApplied && currentLibrary.getUrls(rootType).contains(virtualFileUrl.url)) {
      error("removeRoot: removed url '${virtualFileUrl.url}' type '${rootType.name()}' still exists after removing")
    }

    return true
  }

  override fun removeExcludedRoot(url: String): Boolean {
    assertModelIsLive()

    val virtualFileUrl = virtualFileManager.fromUrl(url)

    if (!currentLibrary.excludedRootUrls.contains(virtualFileUrl.url)) return false

    update {
      excludedRoots = excludedRoots.filter { it != virtualFileUrl }
    }

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

  override fun getInvalidRootUrls(type: OrderRootType): List<String> = currentLibrary.getInvalidRootUrls(type)
  override fun getExcludedRoots(): Array<VirtualFile> = currentLibrary.excludedRoots
  override fun getModule(): Module? = currentLibrary.module

  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener) = throw UnsupportedOperationException()
  override fun addRootSetChangedListener(listener: RootProvider.RootSetChangedListener, parentDisposable: Disposable) = throw UnsupportedOperationException()
  override fun removeRootSetChangedListener(listener: RootProvider.RootSetChangedListener) = throw UnsupportedOperationException()

  override fun getExternalSource(): ProjectModelExternalSource? = originalLibrarySnapshot.externalSource

  override fun getModifiableModel(): LibraryEx.ModifiableModelEx = throw UnsupportedOperationException()

  override fun getSource(): Library? = originalLibrary

  override fun getTable(): LibraryTable = originalLibrarySnapshot.libraryTable

  override fun getRootProvider(): RootProvider = this

  override fun readExternal(element: Element?) = throw UnsupportedOperationException()
  override fun writeExternal(element: Element?) = throw UnsupportedOperationException()
}
